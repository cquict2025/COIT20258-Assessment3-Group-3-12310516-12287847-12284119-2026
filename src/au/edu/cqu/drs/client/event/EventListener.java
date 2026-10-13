package au.edu.cqu.drs.client.event;

import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.MessageStreams;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.server.event.EventType;
import au.edu.cqu.drs.server.event.QueuedSubscriber;
import au.edu.cqu.drs.server.event.ServerEvent;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Receives the server's live events on a second connection and hands them
 * to the screens.
 *
 * <p>The listener runs on its own background thread: it connects, sends
 * {@link Command#SUBSCRIBE_EVENTS} with the session token, then waits for
 * events. Each event is passed to the listeners through a deliverer, which
 * in the client is {@code Platform::runLater}, so screens are only ever
 * updated on the JavaFX thread. If the connection drops, the listener
 * reconnects every few seconds; if the server refuses the subscription,
 * because the session has ended, it stops. It remembers the latest events
 * and the number of connected clients, so a screen opened later can show
 * them at once.</p>
 */
public class EventListener implements Runnable {

    /** Events kept for screens that open later. */
    public static final int RECENT_LIMIT = 50;

    /** Milliseconds between attempts to reconnect. */
    public static final int RETRY_MILLIS = 5_000;

    /** Milliseconds without even a heartbeat before the link is dead. */
    public static final int SILENCE_MILLIS =
            3 * QueuedSubscriber.HEARTBEAT_MILLIS;

    /** Status shown while events are arriving. */
    public static final String LIVE = "Live updates on";

    /** Status shown while reconnecting. */
    public static final String RECONNECTING =
            "Live updates lost; reconnecting...";

    /** Milliseconds allowed to connect. */
    private static final int CONNECT_TIMEOUT_MILLIS = 5_000;

    private final String host;
    private final int port;
    private final String sessionToken;
    private final Consumer<Runnable> deliverer;
    private final int retryMillis;
    private final List<Consumer<ServerEvent>> listeners;
    private final List<Consumer<String>> statusListeners;
    private final Deque<ServerEvent> recent;
    private volatile boolean running;
    private volatile Socket socket;
    private volatile String status;
    private volatile int clientCount;
    private Thread thread;

    /**
     * Creates a listener that is not yet connected.
     *
     * @param host         the server host
     * @param port         the server port
     * @param sessionToken the signed-in user's session token
     * @param deliverer    runs each delivery, for example on the JavaFX
     *                     thread with {@code Platform::runLater}
     */
    public EventListener(String host, int port, String sessionToken,
                         Consumer<Runnable> deliverer) {
        this(host, port, sessionToken, deliverer, RETRY_MILLIS);
    }

    /**
     * Creates a listener with a chosen reconnect delay; for tests.
     *
     * @param host         the server host
     * @param port         the server port
     * @param sessionToken the signed-in user's session token
     * @param deliverer    runs each delivery
     * @param retryMillis  milliseconds between reconnect attempts
     */
    public EventListener(String host, int port, String sessionToken,
                         Consumer<Runnable> deliverer, int retryMillis) {
        this.host = host;
        this.port = port;
        this.sessionToken = sessionToken;
        this.deliverer = deliverer;
        this.retryMillis = retryMillis;
        this.listeners = new CopyOnWriteArrayList<>();
        this.statusListeners = new CopyOnWriteArrayList<>();
        this.recent = new ArrayDeque<>();
        this.status = "Connecting to live updates...";
    }

    /**
     * Starts listening on a background thread.
     */
    public synchronized void start() {
        if (this.running) {
            return;
        }
        this.running = true;
        this.thread = new Thread(this, "drs-event-listener");
        this.thread.setDaemon(true);
        this.thread.start();
    }

    /**
     * Stops listening and closes the connection.
     */
    public synchronized void stop() {
        this.running = false;
        closeSocket();
        if (this.thread != null) {
            this.thread.interrupt();
        }
    }

    /**
     * Adds a listener told about every event except heartbeats.
     *
     * @param listener the listener
     */
    public void addListener(Consumer<ServerEvent> listener) {
        this.listeners.add(listener);
    }

    /**
     * Removes a listener.
     *
     * @param listener the listener
     */
    public void removeListener(Consumer<ServerEvent> listener) {
        this.listeners.remove(listener);
    }

    /**
     * Adds a listener told whenever the connection status changes.
     *
     * @param listener the listener
     */
    public void addStatusListener(Consumer<String> listener) {
        this.statusListeners.add(listener);
    }

    /**
     * Removes a status listener.
     *
     * @param listener the listener
     */
    public void removeStatusListener(Consumer<String> listener) {
        this.statusListeners.remove(listener);
    }

    /**
     * Returns the latest events, newest first.
     *
     * @return up to {@link #RECENT_LIMIT} events
     */
    public List<ServerEvent> getRecentEvents() {
        synchronized (this.recent) {
            return new ArrayList<>(this.recent);
        }
    }

    /**
     * Returns the number of clients receiving live updates, as last told
     * by the server.
     *
     * @return the count, or 0 before the server has said
     */
    public int getClientCount() {
        return this.clientCount;
    }

    /**
     * Returns the connection status.
     *
     * @return a short description for the screen
     */
    public String getStatus() {
        return this.status;
    }

    /**
     * Tells whether the listener is still trying to receive events.
     *
     * @return {@code true} until stopped or refused
     */
    public boolean isRunning() {
        return this.running;
    }

    /**
     * Connects, subscribes and receives events until stopped, reconnecting
     * after a lost connection.
     */
    @Override
    public void run() {
        while (this.running) {
            try {
                listen();
            } catch (IOException | ClassNotFoundException e) {
                if (!this.running) {
                    return;
                }
                setStatus(RECONNECTING);
                pause();
            } finally {
                closeSocket();
            }
        }
    }

    /**
     * Opens the connection, subscribes, and reads events until the
     * connection ends or the subscription is refused.
     *
     * @throws IOException            if the connection fails
     * @throws ClassNotFoundException if an unknown class arrives
     */
    private void listen() throws IOException, ClassNotFoundException {
        Socket opened = new Socket();
        this.socket = opened;
        opened.connect(new InetSocketAddress(this.host, this.port),
                CONNECT_TIMEOUT_MILLIS);
        opened.setSoTimeout(SILENCE_MILLIS);
        ObjectOutputStream out = MessageStreams.openOutput(opened);
        ObjectInputStream in = MessageStreams.openInput(opened);
        MessageStreams.write(out, new Request(Command.SUBSCRIBE_EVENTS,
                this.sessionToken));
        Response response = MessageStreams.readResponse(in);
        if (!response.isSuccess()) {
            this.running = false;
            setStatus("Live updates stopped: " + response.getMessage());
            return;
        }
        setStatus(LIVE);
        while (this.running) {
            receive(MessageStreams.read(in, ServerEvent.class));
        }
    }

    /**
     * Records an event and hands it to the listeners.
     *
     * @param event the event
     */
    private void receive(ServerEvent event) {
        if (event.getType() == EventType.HEARTBEAT) {
            return;
        }
        if (event.getType() == EventType.CLIENTS) {
            this.clientCount = event.getClientCount();
        } else {
            synchronized (this.recent) {
                this.recent.addFirst(event);
                while (this.recent.size() > RECENT_LIMIT) {
                    this.recent.removeLast();
                }
            }
        }
        this.deliverer.accept(() -> {
            for (Consumer<ServerEvent> listener : this.listeners) {
                listener.accept(event);
            }
        });
    }

    /**
     * Records a new status and tells the status listeners.
     *
     * @param newStatus the status
     */
    private void setStatus(String newStatus) {
        if (newStatus.equals(this.status)) {
            return;
        }
        this.status = newStatus;
        this.deliverer.accept(() -> {
            for (Consumer<String> listener : this.statusListeners) {
                listener.accept(newStatus);
            }
        });
    }

    /**
     * Waits before the next reconnect attempt.
     */
    private void pause() {
        try {
            Thread.sleep(this.retryMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            this.running = false;
        }
    }

    /**
     * Closes the connection, ignoring any error.
     */
    private void closeSocket() {
        Socket current = this.socket;
        if (current == null) {
            return;
        }
        try {
            current.close();
        } catch (IOException e) {
            // Already closed; nothing more to do.
        }
    }

    /**
     * Describes the listener.
     *
     * @return the server and status
     */
    @Override
    public String toString() {
        return "EventListener for " + this.host + ":" + this.port + ": "
                + this.status;
    }
}
