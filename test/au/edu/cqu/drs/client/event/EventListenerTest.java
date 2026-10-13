package au.edu.cqu.drs.client.event;

import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.protocol.ArgumentKey;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.server.ServerBootstrap;
import au.edu.cqu.drs.server.ServerLog;
import au.edu.cqu.drs.server.ServerTestSupport;
import au.edu.cqu.drs.server.event.EventType;
import au.edu.cqu.drs.server.event.ServerEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.ServerSocket;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link EventListener} against a real server on a free port.
 * Deliveries run on the listener's own thread instead of the JavaFX one.
 */
class EventListenerTest {

    private static final long WAIT_SECONDS = 5;

    private ServerBootstrap bootstrap;
    private EventListener listener;
    private final BlockingQueue<ServerEvent> events =
            new LinkedBlockingQueue<>();
    private final BlockingQueue<String> statuses =
            new LinkedBlockingQueue<>();

    @BeforeEach
    void setUp() throws Exception {
        this.bootstrap = new ServerBootstrap(
                ServerTestSupport.memoryConfig(4), new ServerLog(),
                Clock.systemDefaultZone());
        this.bootstrap.start();
    }

    @AfterEach
    void tearDown() {
        if (this.listener != null) {
            this.listener.stop();
        }
        this.bootstrap.close();
    }

    @Test
    @DisplayName("TC-EV-11 a signed-in client receives a change made by "
            + "someone else, and the connected-client count")
    void receivesChanges() throws Exception {
        String token = ServerTestSupport.login(
                this.bootstrap.getDispatcher(), "coordinator");
        start(this.bootstrap.getServer().getPort(), token, 50);
        assertEquals(EventListener.LIVE, nextStatus());
        assertEquals(EventType.CLIENTS, next().getType());

        Response submitted = this.bootstrap.getDispatcher().dispatch(
                new Request(Command.SUBMIT_REPORT, null).put(
                        ArgumentKey.REPORT, ServerTestSupport.validReport()));
        ServerEvent change = next();

        assertEquals(EventType.CHANGE, change.getType());
        assertEquals(((DisasterReport) submitted.getValue()).getReportId(),
                change.getEntityId());
        assertEquals(1, this.listener.getClientCount());
        List<ServerEvent> recent = this.listener.getRecentEvents();
        assertEquals(1, recent.size());
        assertEquals(change.getEntityId(), recent.get(0).getEntityId());
    }

    @Test
    @DisplayName("TC-EV-12 a refused subscription stops the listener with "
            + "the server's reason")
    void refusedSubscriptionStops() throws Exception {
        start(this.bootstrap.getServer().getPort(), "expired-token", 50);

        String status = nextStatus();

        assertTrue(status.startsWith("Live updates stopped"), status);
        assertTrue(waitUntilStopped());
    }

    @Test
    @DisplayName("TC-EV-13 when the server is unavailable the listener "
            + "keeps trying to reconnect")
    void unavailableServerIsRetried() throws Exception {
        int closedPort;
        try (ServerSocket probe = new ServerSocket(0)) {
            closedPort = probe.getLocalPort();
        }
        start(closedPort, "token", 50);

        assertEquals(EventListener.RECONNECTING, nextStatus());
        Thread.sleep(200);
        assertTrue(this.listener.isRunning());
        this.listener.stop();
        assertTrue(waitUntilStopped());
    }

    /**
     * Starts a listener whose deliveries run straight away.
     */
    private void start(int port, String token, int retryMillis) {
        this.listener = new EventListener("localhost", port, token,
                Runnable::run, retryMillis);
        this.listener.addListener(this.events::add);
        this.listener.addStatusListener(this.statuses::add);
        this.listener.start();
    }

    /**
     * Waits for the next event.
     */
    private ServerEvent next() throws InterruptedException {
        ServerEvent event = this.events.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(event, "no event arrived");
        return event;
    }

    /**
     * Waits for the next status.
     */
    private String nextStatus() throws InterruptedException {
        String status = this.statuses.poll(WAIT_SECONDS, TimeUnit.SECONDS);
        assertNotNull(status, "no status arrived");
        return status;
    }

    /**
     * Waits for the listener to stop running.
     */
    private boolean waitUntilStopped() throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_SECONDS * 1000;
        while (this.listener.isRunning()
                && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        return !this.listener.isRunning();
    }
}
