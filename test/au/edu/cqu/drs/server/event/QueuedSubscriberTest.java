package au.edu.cqu.drs.server.event;

import au.edu.cqu.drs.protocol.MessageStreams;
import au.edu.cqu.drs.server.ServerLog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link QueuedSubscriber}: a client that stops reading must not
 * hold up the publisher or the other clients.
 */
class QueuedSubscriberTest {

    private static final int EVENTS = 600;
    private static final long WAIT_SECONDS = 10;

    private final List<QueuedSubscriber> subscribers = new ArrayList<>();
    private final CountDownLatch release = new CountDownLatch(1);

    /**
     * Releases any stalled subscriber and closes every subscriber.
     */
    @AfterEach
    void tearDown() {
        this.release.countDown();
        for (QueuedSubscriber subscriber : this.subscribers) {
            subscriber.close();
        }
    }

    /**
     * Checks that a stalled subscriber does not delay the others; its queue
     * keeps the newest 500 events.
     *
     * @throws Exception if the test cannot complete its setup or I/O
     */
    @Test
    @DisplayName("TC-EV-04 a stalled subscriber does not delay the others; "
            + "its queue keeps the newest 500 events")
    void stalledSubscriberDoesNotDelayOthers() throws Exception {
        EventBus bus = new EventBus();
        StallingStream stalled = new StallingStream(this.release);
        QueuedSubscriber slow = subscriber("stalled",
                MessageStreams.openOutput(stalled),
                QueuedSubscriber.CAPACITY);
        stalled.stall();
        bus.subscribe(slow);
        AtomicInteger firstCount = new AtomicInteger();
        AtomicInteger secondCount = new AtomicInteger();
        CountDownLatch done = new CountDownLatch(2);
        bus.subscribe(healthy("first", firstCount, done));
        bus.subscribe(healthy("second", secondCount, done));

        long started = System.nanoTime();
        for (int i = 1; i <= EVENTS; i++) {
            bus.publish(ServerEvent.about(EventType.CHANGE, "Report",
                    "R-" + i, "event " + i, LocalDateTime.now()));
        }
        long publishMillis = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - started);

        assertTrue(done.await(WAIT_SECONDS, TimeUnit.SECONDS),
                "the healthy subscribers did not receive every event");
        assertEquals(EVENTS, firstCount.get());
        assertEquals(EVENTS, secondCount.get());
        assertTrue(publishMillis < 2_000,
                "publishing took " + publishMillis + " ms");
        assertTrue(stalled.isBlocked());
        assertEquals(QueuedSubscriber.CAPACITY, slow.getQueuedCount());
        assertTrue(slow.getDroppedCount() >= EVENTS
                - QueuedSubscriber.CAPACITY - 1, slow.toString());
    }

    /**
     * Checks that a quiet channel sends heartbeats, and a closed subscriber
     * runs its close action once.
     *
     * @throws Exception if the test cannot complete its setup or I/O
     */
    @Test
    @DisplayName("TC-EV-05 a quiet channel sends heartbeats, and a closed "
            + "subscriber runs its close action once")
    void heartbeatAndClose() throws Exception {
        PipedInputStream in = new PipedInputStream(64 * 1024);
        PipedOutputStream out = new PipedOutputStream(in);
        AtomicInteger closes = new AtomicInteger();
        QueuedSubscriber subscriber = new QueuedSubscriber("quiet",
                MessageStreams.openOutput(out), 10, 50, new ServerLog(),
                Clock.systemDefaultZone(), closes::incrementAndGet);
        this.subscribers.add(subscriber);
        subscriber.start();
        ObjectInputStream reader = MessageStreams.openInput(in);

        ServerEvent first = MessageStreams.read(reader, ServerEvent.class);
        subscriber.close();
        subscriber.close();

        assertEquals(EventType.HEARTBEAT, first.getType());
        assertEquals(1, closes.get());
        assertTrue(subscriber.isClosed());
    }

    /**
     * Creates a started subscriber on a stream.
     *
     * @param name     the client name
     * @param out      the stream to write to
     * @param capacity the queue capacity
     * @return the started subscriber
     */
    private QueuedSubscriber subscriber(String name, ObjectOutputStream out,
                                        int capacity) {
        QueuedSubscriber subscriber = new QueuedSubscriber(name, out,
                capacity, QueuedSubscriber.HEARTBEAT_MILLIS,
                new ServerLog(), Clock.systemDefaultZone(), () -> { });
        this.subscribers.add(subscriber);
        subscriber.start();
        return subscriber;
    }

    /**
     * Creates a subscriber whose client reads every event and counts the
     * changes. Its queue holds every event, so only the stalled
     * subscriber can drop any.
     *
     * @param name  the client name
     * @param count counts the change events read
     * @param done  released when every event has arrived
     * @return the started subscriber
     * @throws IOException if the pipe cannot be opened
     */
    private QueuedSubscriber healthy(String name, AtomicInteger count,
                                     CountDownLatch done) throws IOException {
        PipedInputStream in = new PipedInputStream(64 * 1024);
        PipedOutputStream out = new PipedOutputStream(in);
        QueuedSubscriber subscriber = subscriber(name,
                MessageStreams.openOutput(out), EVENTS);
        Thread reader = new Thread(() -> {
            try {
                ObjectInputStream stream = MessageStreams.openInput(in);
                while (count.get() < EVENTS) {
                    ServerEvent event = MessageStreams.read(stream,
                            ServerEvent.class);
                    if (event.getType() == EventType.CHANGE) {
                        count.incrementAndGet();
                    }
                }
                done.countDown();
            } catch (IOException | ClassNotFoundException e) {
                // The test fails on the missing count.
            }
        }, "reader-" + name);
        reader.setDaemon(true);
        reader.start();
        return subscriber;
    }

    /**
     * An output stream whose client has stopped reading: once stalled,
     * every write blocks until the test ends.
     */
    private static final class StallingStream extends OutputStream {

        private final CountDownLatch release;
        private volatile boolean stalled;
        private volatile boolean blocked;

        /**
         * Creates the stream.
         *
         * @param release opened when the test ends
         */
        StallingStream(CountDownLatch release) {
            this.release = release;
        }

        /**
         * Makes every later write block.
         */
        void stall() {
            this.stalled = true;
        }

        /**
         * Tells whether a write is blocked.
         *
         * @return {@code true} once a writer is stuck
         */
        boolean isBlocked() {
            return this.blocked;
        }

        /**
         * Blocks once stalled, otherwise discards the byte.
         *
         * @param b the byte
         * @throws IOException if interrupted while blocked
         */
        @Override
        public void write(int b) throws IOException {
            if (!this.stalled) {
                return;
            }
            this.blocked = true;
            try {
                this.release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            }
            throw new IOException("the client has gone");
        }

        /**
         * Describes the stream.
         *
         * @return whether it is stalled
         */
        @Override
        public String toString() {
            return "StallingStream, stalled " + this.stalled;
        }
    }

    /**
     * Describes this test class.
     *
     * @return a short description of this object
     */
    @Override
    public String toString() {
        return "QueuedSubscriberTest: tests for QueuedSubscriber";
    }
}
