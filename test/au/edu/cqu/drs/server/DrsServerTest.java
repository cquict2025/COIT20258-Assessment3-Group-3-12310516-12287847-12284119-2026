package au.edu.cqu.drs.server;

import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.LoginResult;
import au.edu.cqu.drs.protocol.MessageStreams;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.service.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link DrsServer} and {@link ClientHandler} on a real socket,
 * on a free port with in-memory storage.
 */
class DrsServerTest {

    private static final int CLIENTS = 10;
    private static final long WAIT_MILLIS = 5_000;

    private ServerBootstrap bootstrap;

    @BeforeEach
    void setUp() throws Exception {
        this.bootstrap = new ServerBootstrap(
                ServerTestSupport.memoryConfig(CLIENTS), new ServerLog(),
                Clock.systemDefaultZone());
        this.bootstrap.start();
    }

    @AfterEach
    void tearDown() {
        this.bootstrap.close();
    }

    @Test
    @DisplayName("TC-DS-01 ten clients sign in and list incidents at the "
            + "same time")
    void tenConcurrentClients() throws Exception {
        String[] accounts = {"coordinator", "liaison", "responder", "admin"};
        CountDownLatch ready = new CountDownLatch(CLIENTS);
        ExecutorService clients = Executors.newFixedThreadPool(CLIENTS);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < CLIENTS; i++) {
            String account = accounts[i % accounts.length];
            results.add(clients.submit(signInAndList(account, ready)));
        }

        Set<String> tokens = new HashSet<>();
        for (Future<String> result : results) {
            tokens.add(result.get(10, TimeUnit.SECONDS));
        }
        clients.shutdown();

        assertEquals(CLIENTS, tokens.size());
        assertTrue(waitForClients(0));
        assertTrue(this.bootstrap.getLog().recentLines().stream()
                .anyMatch(line -> line.contains("[drs-worker-")));
    }

    @Test
    @DisplayName("TC-DS-02 a message that is not a valid request is "
            + "answered with an error and the connection closes")
    void badRequestClosesConnection() throws Exception {
        try (Socket socket = connect()) {
            ObjectOutputStream out = MessageStreams.openOutput(socket);
            ObjectInputStream in = MessageStreams.openInput(socket);

            MessageStreams.write(out, new File("not-a-request"));
            Response response = MessageStreams.readResponse(in);

            assertFalse(response.isSuccess());
            assertEquals(ErrorCode.VALIDATION_ERROR, response.getErrorCode());
            assertNull(response.getRequestId());
            assertThrows(EOFException.class,
                    () -> MessageStreams.readResponse(in));
        }
        assertTrue(waitForClients(0));
    }

    @Test
    @DisplayName("TC-DS-03 the client count follows connections and drops "
            + "when a client vanishes")
    void countsConnectedClients() throws Exception {
        Socket first = connect();
        Socket second = connect();
        MessageStreams.openOutput(first);
        MessageStreams.openOutput(second);

        assertTrue(waitForClients(2));
        first.close();
        assertTrue(waitForClients(1));
        second.close();
        assertTrue(waitForClients(0));
    }

    @Test
    @DisplayName("TC-DS-04 settings reject bad values and MySQL is refused "
            + "until it is installed")
    void settingsValidation() throws Exception {
        Properties badPort = ServerConfig.defaults();
        badPort.setProperty("port", "70000");
        Properties badBackend = ServerConfig.defaults();
        badBackend.setProperty("repository", "oracle");
        Properties mysql = ServerConfig.defaults();
        mysql.setProperty("repository", "MySQL");

        ServerConfig defaults = new ServerConfig(ServerConfig.defaults());
        ServerConfig mysqlConfig = new ServerConfig(mysql);

        assertEquals(5050, defaults.getPort());
        assertEquals(32, defaults.getThreads());
        assertEquals(ServerConfig.Backend.SQLITE, defaults.getBackend());
        assertThrows(IllegalArgumentException.class,
                () -> new ServerConfig(badPort));
        assertThrows(IllegalArgumentException.class,
                () -> new ServerConfig(badBackend));
        assertThrows(IllegalStateException.class,
                () -> new ServerContext(mysqlConfig,
                        Clock.systemDefaultZone()));
    }

    /**
     * Makes a client that signs in, lists incidents and returns its token.
     *
     * @param account the account to use
     * @param ready   released once every client is connected
     * @return the client task
     */
    private Callable<String> signInAndList(String account,
                                           CountDownLatch ready) {
        return () -> {
            try (Socket socket = connect()) {
                ObjectOutputStream out = MessageStreams.openOutput(socket);
                ObjectInputStream in = MessageStreams.openInput(socket);
                ready.countDown();
                ready.await(WAIT_MILLIS, TimeUnit.MILLISECONDS);
                MessageStreams.write(out,
                        ServerTestSupport.loginRequest(account));
                Response login = MessageStreams.readResponse(in);
                String token =
                        ((LoginResult) login.getValue()).getSessionToken();
                MessageStreams.write(out,
                        new Request(Command.LIST_INCIDENTS, token));
                Response list = MessageStreams.readResponse(in);
                if (!list.isSuccess()) {
                    throw new IllegalStateException(list.toString());
                }
                return token;
            }
        };
    }

    /**
     * Connects to the test server.
     *
     * @return the socket
     * @throws IOException if the connection fails
     */
    private Socket connect() throws IOException {
        return new Socket("localhost",
                this.bootstrap.getServer().getPort());
    }

    /**
     * Waits until the server reports a client count.
     *
     * @param expected the count to wait for
     * @return {@code true} if it was reached in time
     * @throws InterruptedException if interrupted while waiting
     */
    private boolean waitForClients(int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WAIT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            if (this.bootstrap.getServer().getConnectedClients()
                    == expected) {
                return true;
            }
            Thread.sleep(20);
        }
        return false;
    }
}
