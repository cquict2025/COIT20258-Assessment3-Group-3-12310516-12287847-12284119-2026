package au.edu.cqu.drs.server.event;

import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.protocol.ArgumentKey;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.MessageStreams;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.server.ServerBootstrap;
import au.edu.cqu.drs.server.ServerLog;
import au.edu.cqu.drs.server.ServerTestSupport;
import au.edu.cqu.drs.service.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link EventChannel} on a real server, on a free port with
 * in-memory storage.
 */
class EventChannelTest {

    private static final int READ_TIMEOUT_MILLIS = 5_000;

    private ServerBootstrap bootstrap;
    private final List<Socket> sockets = new ArrayList<>();

    /**
     * Starts an in-memory server for the test.
     *
     * @throws Exception if the server cannot start
     */
    @BeforeEach
    void setUp() throws Exception {
        this.bootstrap = new ServerBootstrap(
                ServerTestSupport.memoryConfig(8), new ServerLog(),
                Clock.systemDefaultZone());
        this.bootstrap.start();
    }

    /**
     * Closes every socket the test opened, then the server.
     *
     * @throws IOException if a socket cannot be closed
     */
    @AfterEach
    void tearDown() throws IOException {
        for (Socket socket : this.sockets) {
            socket.close();
        }
        this.bootstrap.close();
    }

    /**
     * Checks that a report submitted by the public reaches three subscribed
     * clients.
     *
     * @throws Exception if the test cannot complete its setup or I/O
     */
    @Test
    @DisplayName("TC-EV-08 a report submitted by the public reaches three "
            + "subscribed clients")
    void changeReachesThreeClients() throws Exception {
        List<ObjectInputStream> channels = new ArrayList<>();
        for (String account : new String[] {"coordinator", "liaison",
            "responder"}) {
            channels.add(subscribe(signIn(account)));
        }

        Response submitted = this.bootstrap.getDispatcher().dispatch(
                new Request(Command.SUBMIT_REPORT, null).put(
                        ArgumentKey.REPORT, ServerTestSupport.validReport()));
        String reportId = ((DisasterReport) submitted.getValue())
                .getReportId();

        for (ObjectInputStream channel : channels) {
            ServerEvent event = nextOfType(channel, EventType.CHANGE);
            assertEquals("Report", event.getEntityType());
            assertEquals(reportId, event.getEntityId());
            assertTrue(event.getSummary().contains("submitted"));
        }
    }

    /**
     * Checks that the clients are told how many are connected, and the count
     * drops when one leaves.
     *
     * @throws Exception if the test cannot complete its setup or I/O
     */
    @Test
    @DisplayName("TC-EV-09 the clients are told how many are connected, "
            + "and the count drops when one leaves")
    void clientCountFollowsSubscribers() throws Exception {
        ObjectInputStream first = subscribe(signIn("coordinator"));
        assertEquals(1, nextOfType(first, EventType.CLIENTS)
                .getClientCount());
        subscribe(signIn("liaison"));
        assertEquals(2, nextOfType(first, EventType.CLIENTS)
                .getClientCount());

        this.sockets.get(1).close();

        assertEquals(1, nextOfType(first, EventType.CLIENTS)
                .getClientCount());
        assertEquals(1, this.bootstrap.getEventChannel()
                .getSubscriberCount());
    }

    /**
     * Checks that subscribing without a valid session is refused and the
     * connection still answers requests.
     *
     * @throws Exception if the test cannot complete its setup or I/O
     */
    @Test
    @DisplayName("TC-EV-10 subscribing without a valid session is refused "
            + "and the connection still answers requests")
    void invalidTokenIsRefused() throws Exception {
        Socket socket = open();
        ObjectOutputStream out = MessageStreams.openOutput(socket);
        ObjectInputStream in = MessageStreams.openInput(socket);

        MessageStreams.write(out, new Request(Command.SUBSCRIBE_EVENTS,
                "not-a-token"));
        Response refused = MessageStreams.readResponse(in);
        MessageStreams.write(out, new Request(Command.PING, null));
        Response pong = MessageStreams.readResponse(in);

        assertFalse(refused.isSuccess());
        assertEquals(ErrorCode.PERMISSION_DENIED, refused.getErrorCode());
        assertTrue(pong.isSuccess());
        assertEquals(0, this.bootstrap.getEventChannel()
                .getSubscriberCount());
    }

    /**
     * Signs in a demonstration account.
     *
     * @param account the demonstration account
     * @return the session token
     */
    private String signIn(String account) {
        return ServerTestSupport.login(this.bootstrap.getDispatcher(),
                account);
    }

    /**
     * Opens a second connection and subscribes it to live events.
     *
     * @param token the session token
     * @return the stream the events arrive on
     * @throws Exception if the connection fails
     */
    private ObjectInputStream subscribe(String token) throws Exception {
        Socket socket = open();
        ObjectOutputStream out = MessageStreams.openOutput(socket);
        ObjectInputStream in = MessageStreams.openInput(socket);
        MessageStreams.write(out, new Request(Command.SUBSCRIBE_EVENTS,
                token));
        Response response = MessageStreams.readResponse(in);
        assertTrue(response.isSuccess(), response.toString());
        return in;
    }

    /**
     * Connects to the test server.
     *
     * @return the connected socket
     * @throws IOException if the server cannot be reached
     */
    private Socket open() throws IOException {
        Socket socket = new Socket("localhost",
                this.bootstrap.getServer().getPort());
        socket.setSoTimeout(READ_TIMEOUT_MILLIS);
        this.sockets.add(socket);
        return socket;
    }

    /**
     * Reads events until one of the given type arrives.
     *
     * @param in   the event stream
     * @param type the event type wanted
     * @return the first event of that type
     * @throws Exception if the stream fails or times out
     */
    private static ServerEvent nextOfType(ObjectInputStream in,
                                          EventType type) throws Exception {
        while (true) {
            ServerEvent event = MessageStreams.read(in, ServerEvent.class);
            if (event.getType() == type) {
                return event;
            }
        }
    }

    /**
     * Describes this test class.
     *
     * @return a short description of this object
     */
    @Override
    public String toString() {
        return "EventChannelTest: tests for the event channel";
    }
}
