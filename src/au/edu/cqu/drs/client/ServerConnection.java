package au.edu.cqu.drs.client;

import au.edu.cqu.drs.protocol.MessageStreams;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.service.ErrorCode;

import java.io.EOFException;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * The client's single connection to the DRS server.
 *
 * <p>{@link #send} is synchronised, so only one request is ever in flight
 * and responses cannot be mixed up between screens. The connection opens on
 * the first request. If the server has closed it (for example after a
 * restart), the request is sent once more on a new connection. A request
 * that gets no answer within the read timeout is not sent again, because
 * the server may already have performed it. Every failure comes back as a
 * failed {@link Response}; this class never throws to its caller.</p>
 */
public class ServerConnection implements AutoCloseable {

    /** Default server host. */
    public static final String DEFAULT_HOST = "localhost";

    /** Default server port. */
    public static final int DEFAULT_PORT = 5050;

    /** How long to wait for an answer. */
    public static final int READ_TIMEOUT_MILLIS = 10_000;

    /** How long to wait for the connection to open. */
    public static final int CONNECT_TIMEOUT_MILLIS = 5_000;

    /** Message shown when the server cannot be reached. */
    public static final String UNAVAILABLE = "Server unavailable. Check that "
            + "the DRS server is running, then try again.";

    /** Message shown when the server does not answer in time. */
    public static final String NO_ANSWER = "The server did not answer in "
            + "time. Refresh to check whether your change was saved.";

    private final String host;
    private final int port;
    private final int readTimeoutMillis;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;

    /**
     * Creates a connection with the default read timeout. Nothing is opened
     * until the first request.
     *
     * @param host the server host
     * @param port the server port
     */
    public ServerConnection(String host, int port) {
        this(host, port, READ_TIMEOUT_MILLIS);
    }

    /**
     * Creates a connection. Nothing is opened until the first request.
     *
     * @param host              the server host
     * @param port              the server port
     * @param readTimeoutMillis how long to wait for an answer
     */
    public ServerConnection(String host, int port, int readTimeoutMillis) {
        this.host = host;
        this.port = port;
        this.readTimeoutMillis = readTimeoutMillis;
    }

    /**
     * Sends a request and waits for the answer.
     *
     * @param request the request
     * @return the server's response, or a failed response with
     *         {@link ErrorCode#ADAPTER_UNAVAILABLE} if the server cannot be
     *         reached
     */
    public synchronized Response send(Request request) {
        try {
            return exchange(request);
        } catch (SocketTimeoutException e) {
            close();
            return unavailable(request, NO_ANSWER);
        } catch (EOFException | SocketException e) {
            close();
            return retry(request);
        } catch (IOException | ClassNotFoundException e) {
            close();
            return unavailable(request, UNAVAILABLE);
        }
    }

    /**
     * Tells whether a connection is open now.
     *
     * @return {@code true} if connected
     */
    public synchronized boolean isConnected() {
        return this.socket != null && !this.socket.isClosed();
    }

    /**
     * Closes the connection; the next request opens a new one.
     */
    @Override
    public synchronized void close() {
        if (this.socket != null) {
            try {
                this.socket.close();
            } catch (IOException e) {
                // Already broken; nothing more to do.
            }
        }
        this.socket = null;
        this.out = null;
        this.in = null;
    }

    /**
     * Returns the server host.
     *
     * @return the host
     */
    public String getHost() {
        return this.host;
    }

    /**
     * Returns the server port.
     *
     * @return the port
     */
    public int getPort() {
        return this.port;
    }

    /**
     * Sends the request once more on a new connection.
     *
     * @param request the request
     * @return the response, or a failed response
     */
    private Response retry(Request request) {
        try {
            return exchange(request);
        } catch (SocketTimeoutException e) {
            close();
            return unavailable(request, NO_ANSWER);
        } catch (IOException | ClassNotFoundException e) {
            close();
            return unavailable(request, UNAVAILABLE);
        }
    }

    /**
     * Opens the connection if needed, writes the request and reads the
     * answer.
     *
     * @param request the request
     * @return the response
     * @throws IOException            if the connection fails or the answer
     *                                is for another request
     * @throws ClassNotFoundException if the answer cannot be rebuilt
     */
    private Response exchange(Request request)
            throws IOException, ClassNotFoundException {
        if (!isConnected()) {
            open();
        }
        MessageStreams.write(this.out, request);
        Response response = MessageStreams.readResponse(this.in);
        if (response.getRequestId() != null
                && !response.getRequestId().equals(request.getRequestId())) {
            throw new InvalidObjectException(
                    "The answer was for another request.");
        }
        return response;
    }

    /**
     * Opens the socket and its filtered streams.
     *
     * @throws IOException if the server cannot be reached
     */
    private void open() throws IOException {
        Socket opened = new Socket();
        try {
            opened.connect(new InetSocketAddress(this.host, this.port),
                    CONNECT_TIMEOUT_MILLIS);
            opened.setSoTimeout(this.readTimeoutMillis);
            this.out = MessageStreams.openOutput(opened);
            this.in = MessageStreams.openInput(opened);
            this.socket = opened;
        } catch (IOException e) {
            opened.close();
            throw e;
        }
    }

    /**
     * Builds the failed response returned when the server is unreachable.
     *
     * @param request the request
     * @param message the message for the user
     * @return the response
     */
    private static Response unavailable(Request request, String message) {
        return Response.failure(request.getRequestId(),
                ErrorCode.ADAPTER_UNAVAILABLE, message);
    }

    /**
     * Describes the connection.
     *
     * @return the server address and state
     */
    @Override
    public String toString() {
        String state = "not connected";
        if (isConnected()) {
            state = "connected";
        }
        return "ServerConnection to " + this.host + ":" + this.port + ", "
                + state;
    }
}
