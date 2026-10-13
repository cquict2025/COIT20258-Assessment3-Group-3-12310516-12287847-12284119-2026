package au.edu.cqu.drs.server;

import au.edu.cqu.drs.server.event.EventChannel;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;

/**
 * The multi-threaded DRS server: accepts connections on a
 * {@link ServerSocket} and serves each one with a {@link ClientHandler} on a
 * fixed pool of worker threads.
 *
 * <p>A fixed pool bounds the threads and memory the server uses however many
 * clients connect; once every worker is busy, new connections wait in the
 * pool's queue until a client leaves. The server keeps count of connected
 * clients and tells listeners whenever the count changes.</p>
 */
public class DrsServer {

    /** Seconds allowed for workers to finish when the server stops. */
    private static final int STOP_WAIT_SECONDS = 5;

    private final RequestDispatcher dispatcher;
    private final ServerLog log;
    private final int requestedPort;
    private final int threads;
    private final int timeoutMillis;
    private final AtomicInteger connectedClients;
    private final Set<Socket> openSockets;
    private final List<IntConsumer> countListeners;
    private volatile EventChannel events;
    private ServerSocket serverSocket;
    private ExecutorService workers;
    private volatile boolean running;

    /**
     * Creates a server that is not yet listening.
     *
     * @param dispatcher    performs the requests
     * @param log           the server log
     * @param port          the port, or 0 for any free port
     * @param threads       the number of worker threads
     * @param timeoutMillis how long a client may stay silent
     */
    public DrsServer(RequestDispatcher dispatcher, ServerLog log, int port,
                     int threads, int timeoutMillis) {
        this.dispatcher = dispatcher;
        this.log = log;
        this.requestedPort = port;
        this.threads = threads;
        this.timeoutMillis = timeoutMillis;
        this.connectedClients = new AtomicInteger();
        this.openSockets = ConcurrentHashMap.newKeySet();
        this.countListeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Starts listening and accepting clients on a background thread.
     *
     * @throws IOException if the port cannot be opened
     * @throws IllegalStateException if the server is already running
     */
    public synchronized void start() throws IOException {
        if (this.running) {
            throw new IllegalStateException("The server is already running.");
        }
        this.serverSocket = new ServerSocket(this.requestedPort);
        this.workers = Executors.newFixedThreadPool(this.threads,
                namedThreads("drs-worker-"));
        this.running = true;
        Thread acceptor = new Thread(this::acceptClients, "drs-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
        this.log.info("DRS server listening on port " + getPort() + " with "
                + this.threads + " worker threads.");
    }

    /**
     * Stops accepting clients, closes every connection and waits briefly
     * for the workers to finish.
     */
    public synchronized void stop() {
        if (!this.running) {
            return;
        }
        this.running = false;
        closeQuietly(this.serverSocket);
        for (Socket socket : this.openSockets) {
            closeQuietly(socket);
        }
        this.workers.shutdownNow();
        try {
            this.workers.awaitTermination(STOP_WAIT_SECONDS,
                    TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.log.info("DRS server stopped.");
    }

    /**
     * Tells whether the server is accepting clients.
     *
     * @return {@code true} while running
     */
    public boolean isRunning() {
        return this.running;
    }

    /**
     * Returns the port the server is listening on.
     *
     * @return the port, or the requested port before it starts
     */
    public int getPort() {
        if (this.serverSocket == null) {
            return this.requestedPort;
        }
        return this.serverSocket.getLocalPort();
    }

    /**
     * Returns the number of clients connected now.
     *
     * @return the count
     */
    public int getConnectedClients() {
        return this.connectedClients.get();
    }

    /**
     * Sets the channel that connections asking for live events are handed
     * to; without one, such requests are refused as not installed.
     *
     * @param events the event channel
     */
    public void setEventChannel(EventChannel events) {
        this.events = events;
    }

    /**
     * Adds a listener told the new count whenever a client connects or
     * leaves.
     *
     * @param listener the listener
     */
    public void addClientCountListener(IntConsumer listener) {
        this.countListeners.add(listener);
    }

    /**
     * Accepts connections until the server stops.
     */
    private void acceptClients() {
        while (this.running) {
            Socket socket;
            try {
                socket = this.serverSocket.accept();
            } catch (SocketException e) {
                return;
            } catch (IOException e) {
                this.log.error("Accepting a client failed", e);
                continue;
            }
            this.openSockets.add(socket);
            countChanged(this.connectedClients.incrementAndGet());
            try {
                this.workers.execute(new ClientHandler(socket,
                        this.dispatcher, this.log, this.timeoutMillis,
                        () -> clientLeft(socket), this.events));
            } catch (RejectedExecutionException e) {
                clientLeft(socket);
                closeQuietly(socket);
            }
        }
    }

    /**
     * Records that a client has left.
     *
     * @param socket the client's socket
     */
    private void clientLeft(Socket socket) {
        if (this.openSockets.remove(socket)) {
            countChanged(this.connectedClients.decrementAndGet());
        }
    }

    /**
     * Tells the listeners the client count.
     *
     * @param count the new count
     */
    private void countChanged(int count) {
        for (IntConsumer listener : this.countListeners) {
            listener.accept(count);
        }
    }

    /**
     * Makes a factory that names threads with a prefix and a number, so the
     * log shows which worker served each request.
     *
     * @param prefix the name prefix
     * @return the factory
     */
    private static ThreadFactory namedThreads(String prefix) {
        AtomicInteger number = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task,
                    prefix + number.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /**
     * Closes a socket, ignoring any error.
     *
     * @param closeable the socket to close, may be null
     */
    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception e) {
            // Already closed or broken; nothing more to do.
        }
    }

    /**
     * Describes the server.
     *
     * @return the port, state and client count
     */
    @Override
    public String toString() {
        String state = "stopped";
        if (this.running) {
            state = "running";
        }
        return String.format("DrsServer: port %d, %s, %d client(s), "
                + "%d threads", getPort(), state, getConnectedClients(),
                this.threads);
    }
}
