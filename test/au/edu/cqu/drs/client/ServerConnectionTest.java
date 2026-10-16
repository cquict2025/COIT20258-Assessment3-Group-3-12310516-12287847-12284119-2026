package au.edu.cqu.drs.client;

import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.ReportStatus;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.MessageStreams;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.server.ServerBootstrap;
import au.edu.cqu.drs.server.ServerLog;
import au.edu.cqu.drs.server.ServerTestSupport;
import au.edu.cqu.drs.service.ErrorCode;
import au.edu.cqu.drs.service.ServiceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ServerConnection}, {@link ClientSession} and
 * {@link RemoteServices} against a real server on a free port, and against
 * scripted servers for the failure paths.
 */
class ServerConnectionTest {

    /**
     * Checks that a client signs in and works through the real server.
     *
     * @throws Exception if the test cannot complete its setup or I/O
     */
    @Test
    @DisplayName("TC-SC-01 a client signs in and works through the real "
            + "server")
    void worksAgainstRealServer() throws Exception {
        try (ServerBootstrap server = startServer();
             ClientSession session = new ClientSession(
                     new ServerConnection("localhost", port(server)))) {
            RemoteServices services = new RemoteServices(session);

            ServiceResult<User> login = session.login("coordinator",
                    ServerTestSupport.PASSWORD);
            ServiceResult<DisasterReport> report = services.submitReport(
                    ServerTestSupport.validReport());
            ServiceResult<DisasterReport> decided = services.decideReport(
                    report.getValue().getReportId(), ReportStatus.VALIDATED,
                    "Confirmed");
            ServiceResult<Incident> incident =
                    services.createIncidentFromReport(
                            report.getValue().getReportId());
            ServiceResult<List<Incident>> open = services.findOpenIncidents();

            assertTrue(login.isSuccess());
            assertNull(login.getValue().getPasswordHash());
            assertTrue(session.isSignedIn());
            assertTrue(decided.isSuccess(), decided.toString());
            assertTrue(incident.isSuccess(), incident.toString());
            assertEquals(1, open.getValue().size());
            assertEquals(incident.getValue().getIncidentId(),
                    open.getValue().get(0).getIncidentId());
        }
    }

    /**
     * Checks that a session that is not signed in can submit but not read
     * incidents, and logout ends the session.
     *
     * @throws Exception if the test cannot complete its setup or I/O
     */
    @Test
    @DisplayName("TC-SC-02 a session that is not signed in can submit but "
            + "not read incidents, and logout ends the session")
    void publicAndLoggedOutAccess() throws Exception {
        try (ServerBootstrap server = startServer();
             ClientSession session = new ClientSession(
                     new ServerConnection("localhost", port(server)))) {
            RemoteServices services = new RemoteServices(session);

            ServiceResult<DisasterReport> submitted = services.submitReport(
                    ServerTestSupport.validReport());
            ServiceResult<List<Incident>> refused =
                    services.findOpenIncidents();
            session.login("responder", ServerTestSupport.PASSWORD);
            String token = session.getSessionToken();
            session.logout();
            Response afterLogout = session.send(
                    new Request(Command.LIST_INCIDENTS, token));

            assertTrue(submitted.isSuccess());
            assertEquals(ErrorCode.PERMISSION_DENIED,
                    refused.getErrorCode());
            assertFalse(session.isSignedIn());
            assertEquals(ErrorCode.PERMISSION_DENIED,
                    afterLogout.getErrorCode());
        }
    }

    /**
     * Checks that with no server running the answer is 'Server unavailable'.
     *
     * @throws Exception if the test cannot complete its setup or I/O
     */
    @Test
    @DisplayName("TC-SC-03 with no server running the answer is 'Server "
            + "unavailable'")
    void serverUnavailable() throws Exception {
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }
        ServerConnection connection =
                new ServerConnection("localhost", freePort);
        Request ping = new Request(Command.PING, null);

        Response response = connection.send(ping);

        assertFalse(response.isSuccess());
        assertEquals(ErrorCode.ADAPTER_UNAVAILABLE, response.getErrorCode());
        assertEquals(ServerConnection.UNAVAILABLE, response.getMessage());
        assertEquals(ping.getRequestId(), response.getRequestId());
        assertFalse(connection.isConnected());
    }

    /**
     * Checks that a connection the server has closed is reopened once and the
     * request sent again.
     *
     * @throws Exception if the test cannot complete its setup or I/O
     */
    @Test
    @DisplayName("TC-SC-04 a connection the server has closed is reopened "
            + "once and the request sent again")
    void reconnectsOnce() throws Exception {
        AtomicInteger received = new AtomicInteger();
        try (ServerSocket fake = new ServerSocket(0)) {
            CompletableFuture<Void> script = CompletableFuture.runAsync(() ->
                    dropThenAnswer(fake, received));
            ServerConnection connection =
                    new ServerConnection("localhost", fake.getLocalPort());

            Response response = connection.send(
                    new Request(Command.PING, null));

            assertTrue(response.isSuccess(), response.toString());
            assertEquals("pong", response.getValue());
            assertEquals(2, received.get());
            connection.close();
            script.get(5, TimeUnit.SECONDS);
        }
    }

    /**
     * Checks that a request with no answer in time is not sent again.
     *
     * @throws Exception if the test cannot complete its setup or I/O
     */
    @Test
    @DisplayName("TC-SC-05 a request with no answer in time is not sent "
            + "again")
    void timeoutIsNotRetried() throws Exception {
        AtomicInteger received = new AtomicInteger();
        try (ServerSocket fake = new ServerSocket(0)) {
            CompletableFuture<Void> script = CompletableFuture.runAsync(() ->
                    readButNeverAnswer(fake, received));
            ServerConnection connection = new ServerConnection("localhost",
                    fake.getLocalPort(), 300);

            Response response = connection.send(
                    new Request(Command.PING, null));

            assertEquals(ErrorCode.ADAPTER_UNAVAILABLE,
                    response.getErrorCode());
            assertEquals(ServerConnection.NO_ANSWER, response.getMessage());
            assertEquals(1, received.get());
            assertFalse(connection.isConnected());
            fake.close();
            script.get(5, TimeUnit.SECONDS);
        }
    }

    /**
     * Starts a real server on a free port with in-memory storage.
     *
     * @return the running server
     * @throws IOException if it cannot start
     */
    private static ServerBootstrap startServer() throws IOException {
        ServerBootstrap server = new ServerBootstrap(
                ServerTestSupport.memoryConfig(4), new ServerLog(),
                Clock.systemDefaultZone());
        server.start();
        return server;
    }

    /**
     * Returns the port a test server listens on.
     *
     * @param server the server
     * @return the port
     */
    private static int port(ServerBootstrap server) {
        return server.getServer().getPort();
    }

    /**
     * Reads one request and drops the connection, then answers the next
     * connection's request with "pong".
     *
     * @param fake     the listening socket
     * @param received counts the requests read
     */
    private static void dropThenAnswer(ServerSocket fake,
                                       AtomicInteger received) {
        try {
            try (Socket first = fake.accept()) {
                ObjectOutputStream out = MessageStreams.openOutput(first);
                ObjectInputStream in = MessageStreams.openInput(first);
                MessageStreams.readRequest(in);
                received.incrementAndGet();
                out.flush();
            }
            try (Socket second = fake.accept()) {
                ObjectOutputStream out = MessageStreams.openOutput(second);
                ObjectInputStream in = MessageStreams.openInput(second);
                Request request = MessageStreams.readRequest(in);
                received.incrementAndGet();
                MessageStreams.write(out,
                        Response.success(request.getRequestId(), "pong"));
                in.read();
            }
        } catch (IOException | ClassNotFoundException e) {
            // The client has gone; the test checks what it received.
        }
    }

    /**
     * Reads requests but never answers, until the socket closes.
     *
     * @param fake     the listening socket
     * @param received counts the requests read
     */
    private static void readButNeverAnswer(ServerSocket fake,
                                           AtomicInteger received) {
        try {
            while (true) {
                try (Socket socket = fake.accept()) {
                    MessageStreams.openOutput(socket);
                    ObjectInputStream in = MessageStreams.openInput(socket);
                    MessageStreams.readRequest(in);
                    received.incrementAndGet();
                    in.read();
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            // The listening socket was closed by the test.
        }
    }

    /**
     * Describes this test class.
     *
     * @return a short description of this object
     */
    @Override
    public String toString() {
        return "ServerConnectionTest: tests for ServerConnection";
    }
}
