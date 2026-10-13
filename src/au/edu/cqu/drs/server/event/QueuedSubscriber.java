package au.edu.cqu.drs.server.event;

import au.edu.cqu.drs.protocol.MessageStreams;
import au.edu.cqu.drs.server.ServerLog;

import java.io.IOException;
import java.io.ObjectOutputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Delivers events to one client through its own queue and writer thread.
 *
 * <p>{@link #onEvent} only adds the event to a bounded
 * {@link LinkedBlockingQueue}, so the thread that published it never waits
 * for this client. The writer thread takes events from the queue and sends
 * them; if the client stops reading, only this thread blocks. When the
 * queue is full the oldest event is dropped to make room, because the
 * newest state matters most to a live picture, and the drop is logged.
 * When nothing has been sent for a while a heartbeat is sent, so a client
 * that has vanished is noticed when the write fails.</p>
 */
public class QueuedSubscriber implements EventSubscriber {

    /** Events held for a client before the oldest are dropped. */
    public static final int CAPACITY = 500;

    /** Milliseconds of silence before a heartbeat is sent. */
    public static final int HEARTBEAT_MILLIS = 20_000;

    /** Drops logged individually before only every hundredth is. */
    private static final int DROPS_LOGGED = 10;

    private final String name;
    private final ObjectOutputStream out;
    private final LinkedBlockingQueue<ServerEvent> queue;
    private final ServerLog log;
    private final Clock clock;
    private final int heartbeatMillis;
    private final Runnable onClose;
    private final AtomicInteger dropped;
    private final AtomicBoolean closed;
    private final Thread writer;

    /**
     * Creates a subscriber that is not yet sending.
     *
     * @param name            names the client in the log and the thread
     * @param out             the client's object stream
     * @param capacity        the most events held before dropping
     * @param heartbeatMillis milliseconds of silence before a heartbeat
     * @param log             the server log
     * @param clock           the server clock, for heartbeats
     * @param onClose         called once when the subscriber stops
     */
    public QueuedSubscriber(String name, ObjectOutputStream out,
                            int capacity, int heartbeatMillis, ServerLog log,
                            Clock clock, Runnable onClose) {
        this.name = name;
        this.out = out;
        this.queue = new LinkedBlockingQueue<>(capacity);
        this.log = log;
        this.clock = clock;
        this.heartbeatMillis = heartbeatMillis;
        this.onClose = onClose;
        this.dropped = new AtomicInteger();
        this.closed = new AtomicBoolean();
        this.writer = new Thread(this::sendEvents, "drs-events-" + name);
        this.writer.setDaemon(true);
    }

    /**
     * Starts the writer thread.
     */
    public void start() {
        this.writer.start();
    }

    /**
     * Queues an event for this client, dropping the oldest one if the queue
     * is full. Never blocks.
     *
     * @param event the event
     */
    @Override
    public void onEvent(ServerEvent event) {
        if (this.closed.get()) {
            return;
        }
        while (!this.queue.offer(event)) {
            if (this.queue.poll() != null) {
                int count = this.dropped.incrementAndGet();
                if (count <= DROPS_LOGGED || count % 100 == 0) {
                    this.log.info("Live events for " + this.name
                            + " are behind: dropped the oldest event ("
                            + count + " dropped so far).");
                }
            }
        }
    }

    /**
     * Stops sending and runs the close action once.
     */
    public void close() {
        if (this.closed.compareAndSet(false, true)) {
            this.writer.interrupt();
            this.queue.clear();
            this.onClose.run();
        }
    }

    /**
     * Tells whether the subscriber has stopped.
     *
     * @return {@code true} once closed
     */
    public boolean isClosed() {
        return this.closed.get();
    }

    /**
     * Returns the number of events waiting to be sent.
     *
     * @return the queue length
     */
    public int getQueuedCount() {
        return this.queue.size();
    }

    /**
     * Returns the number of events dropped because the queue was full.
     *
     * @return the count
     */
    public int getDroppedCount() {
        return this.dropped.get();
    }

    /**
     * Returns the name of the client.
     *
     * @return the name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Sends queued events, or a heartbeat when there are none, until the
     * subscriber closes or a write fails.
     */
    private void sendEvents() {
        try {
            while (!this.closed.get()) {
                ServerEvent event = this.queue.poll(this.heartbeatMillis,
                        TimeUnit.MILLISECONDS);
                if (event == null) {
                    event = ServerEvent.heartbeat(
                            LocalDateTime.now(this.clock));
                }
                MessageStreams.write(this.out, event);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            if (!this.closed.get()) {
                this.log.info("Live events for " + this.name
                        + " stopped: " + e.getMessage());
            }
        } finally {
            close();
        }
    }

    /**
     * Describes the subscriber.
     *
     * @return the client, queue length and drops
     */
    @Override
    public String toString() {
        return "QueuedSubscriber " + this.name + ": " + this.queue.size()
                + " queued, " + this.dropped.get() + " dropped";
    }
}
