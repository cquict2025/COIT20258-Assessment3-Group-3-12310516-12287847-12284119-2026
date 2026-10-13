package au.edu.cqu.drs.server.event;

import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.MessageStreams;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.server.ServerLog;
import au.edu.cqu.drs.server.SessionManager;
import au.edu.cqu.drs.service.ErrorCode;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a client's second connection into a channel on which the server
 * pushes live events.
 *
 * <p>A client opens a second socket to the server and sends
 * {@link Command#SUBSCRIBE_EVENTS} with its session token. If the token
 * belongs to a signed-in user, the channel answers with a success and from
 * then on only writes to that socket: each client gets a
 * {@link QueuedSubscriber} with its own queue and writer thread, so the
 * worker thread that accepted the connection is free again at once. A
 * small watcher thread waits on the socket's input, so a client that closes
 * its connection is removed at once rather than at the next failed write.
 * The channel publishes the number of subscribed clients whenever it
 * changes.</p>
 */
public class EventChannel implements AutoCloseable {

    private final EventBus bus;
    private final SessionManager sessions;
    private final ServerLog log;
    private final Clock clock;
    private final int capacity;
    private final int heartbeatMillis;
    private final Set<QueuedSubscriber> subscribers;

    /**
     * Creates a channel with the standard queue size and heartbeat.
     *
     * @param bus      the bus whose events are delivered
     * @param sessions checks the session tokens
     * @param log      the server log
     * @param clock    the server clock
     */
    public EventChannel(EventBus bus, SessionManager sessions, ServerLog log,
                        Clock clock) {
        this(bus, sessions, log, clock, QueuedSubscriber.CAPACITY,
                QueuedSubscriber.HEARTBEAT_MILLIS);
    }

    /**
     * Creates a channel.
     *
     * @param bus             the bus whose events are delivered
     * @param sessions        checks the session tokens
     * @param log             the server log
     * @param clock           the server clock
     * @param capacity        the most events held for one client
     * @param heartbeatMillis milliseconds of silence before a heartbeat
     */
    public EventChannel(EventBus bus, SessionManager sessions, ServerLog log,
                        Clock clock, int capacity, int heartbeatMillis) {
        this.bus = bus;
        this.sessions = sessions;
        this.log = log;
        this.clock = clock;
        this.capacity = capacity;
        this.heartbeatMillis = heartbeatMillis;
        this.subscribers = ConcurrentHashMap.newKeySet();
        bus.addSubscriberCountListener(this::publishCount);
    }

    /**
     * Answers a {@link Command#SUBSCRIBE_EVENTS} request and, if the session
     * is valid, starts sending events on the connection.
     *
     * @param request the subscribe request
     * @param socket  the client's socket
     * @param in      the input stream already open on the socket
     * @param out     the object stream already open on the socket
     * @param client  the client's address, for the log
     * @return {@code true} if the channel now owns the connection; the
     *         caller must then neither read from nor close it
     * @throws IOException if the answer cannot be sent
     */
    public boolean open(Request request, Socket socket, InputStream in,
                        ObjectOutputStream out, String client)
            throws IOException {
        User user = this.sessions.resolve(request.getSessionToken());
        if (user == null) {
            MessageStreams.write(out, Response.failure(
                    request.getRequestId(), ErrorCode.PERMISSION_DENIED,
                    "Your session has ended. Please sign in again.",
                    "session"));
            return false;
        }
        String name = user.getUsername() + "@" + client;
        QueuedSubscriber[] holder = new QueuedSubscriber[1];
        QueuedSubscriber subscriber = new QueuedSubscriber(name, out,
                this.capacity, this.heartbeatMillis, this.log, this.clock,
                () -> stopped(holder[0], socket));
        holder[0] = subscriber;
        socket.setSoTimeout(0);
        MessageStreams.write(out, Response.success(request.getRequestId(),
                "Subscribed to live events."));
        this.subscribers.add(subscriber);
        subscriber.start();
        watch(subscriber, in);
        this.bus.subscribe(subscriber);
        this.log.info("Live events: " + name + " subscribed.");
        return true;
    }

    /**
     * Returns the number of clients receiving events.
     *
     * @return the count
     */
    public int getSubscriberCount() {
        return this.subscribers.size();
    }

    /**
     * Stops every subscriber and closes their connections.
     */
    @Override
    public void close() {
        for (QueuedSubscriber subscriber : this.subscribers) {
            subscriber.close();
        }
    }

    /**
     * Starts a thread that closes the subscriber as soon as the client
     * closes its connection. The client sends nothing after subscribing,
     * so a read only returns at the end of the stream or on an error.
     *
     * @param subscriber the subscriber
     * @param in         the input stream of its connection
     */
    private static void watch(QueuedSubscriber subscriber, InputStream in) {
        Thread watcher = new Thread(() -> {
            try {
                while (in.read() != -1) {
                    // Ignore anything sent; only the end matters.
                }
            } catch (IOException e) {
                // The connection broke; close below.
            }
            subscriber.close();
        }, "drs-events-watch-" + subscriber.getName());
        watcher.setDaemon(true);
        watcher.start();
    }

    /**
     * Forgets a subscriber that has stopped and closes its connection.
     *
     * @param subscriber the subscriber
     * @param socket     its connection
     */
    private void stopped(QueuedSubscriber subscriber, Socket socket) {
        this.subscribers.remove(subscriber);
        this.bus.unsubscribe(subscriber);
        try {
            socket.close();
        } catch (IOException e) {
            // Already closed; nothing more to do.
        }
        this.log.info("Live events: " + subscriber.getName()
                + " unsubscribed.");
    }

    /**
     * Publishes the number of subscribed clients.
     *
     * @param count the number
     */
    private void publishCount(int count) {
        this.bus.publish(ServerEvent.clients(count,
                LocalDateTime.now(this.clock)));
    }

    /**
     * Describes the channel.
     *
     * @return the number of subscribed clients
     */
    @Override
    public String toString() {
        return "EventChannel: " + this.subscribers.size()
                + " client(s) subscribed";
    }
}
