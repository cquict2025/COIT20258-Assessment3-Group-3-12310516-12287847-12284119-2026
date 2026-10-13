package au.edu.cqu.drs.server;

import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.MessageStreams;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.server.event.EventChannel;
import au.edu.cqu.drs.service.ErrorCode;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

/**
 * Serves one connected client on a worker thread: reads a request, has the
 * {@link RequestDispatcher} perform it, sends the response, and repeats
 * until the client leaves.
 *
 * <p>The connection is closed cleanly when the client disconnects, stays
 * silent past the timeout, or sends something that is not a valid request.
 * In the last case the client is told why before the connection closes,
 * because a rejected object leaves the stream unusable.</p>
 *
 * <p>A connection that sends {@link Command#SUBSCRIBE_EVENTS} is handed to
 * the {@link EventChannel}, which keeps it open to push live events; the
 * handler then returns without closing it.</p>
 */
public class ClientHandler implements Runnable {

    private final Socket socket;
    private final RequestDispatcher dispatcher;
    private final ServerLog log;
    private final int timeoutMillis;
    private final Runnable onClose;
    private final EventChannel events;

    /**
     * Creates a handler for a server without live events.
     *
     * @param socket        the client's socket
     * @param dispatcher    performs the requests
     * @param log           the server log
     * @param timeoutMillis how long the client may stay silent
     * @param onClose       called once when the connection ends
     */
    public ClientHandler(Socket socket, RequestDispatcher dispatcher,
                         ServerLog log, int timeoutMillis,
                         Runnable onClose) {
        this(socket, dispatcher, log, timeoutMillis, onClose, null);
    }

    /**
     * Creates a handler for an accepted connection.
     *
     * @param socket        the client's socket
     * @param dispatcher    performs the requests
     * @param log           the server log
     * @param timeoutMillis how long the client may stay silent
     * @param onClose       called once when this handler is done
     * @param events        the live event channel, or null if there is none
     */
    public ClientHandler(Socket socket, RequestDispatcher dispatcher,
                         ServerLog log, int timeoutMillis,
                         Runnable onClose, EventChannel events) {
        this.socket = socket;
        this.dispatcher = dispatcher;
        this.log = log;
        this.timeoutMillis = timeoutMillis;
        this.onClose = onClose;
        this.events = events;
    }

    /**
     * Serves the client until it leaves.
     */
    @Override
    public void run() {
        String client = describeClient();
        this.log.info("Client connected: " + client);
        boolean handedOver = false;
        try {
            this.socket.setSoTimeout(this.timeoutMillis);
            ObjectOutputStream out = MessageStreams.openOutput(this.socket);
            ObjectInputStream in = MessageStreams.openInput(this.socket);
            handedOver = serve(in, out, client);
        } catch (IOException e) {
            this.log.info("Connection to " + client + " failed: "
                    + e.getMessage());
        } finally {
            if (handedOver) {
                this.log.info("Client " + client + " now receives live "
                        + "events.");
            } else {
                closeSocket();
                this.log.info("Client disconnected: " + client);
            }
            this.onClose.run();
        }
    }

    /**
     * Reads and answers requests until the connection ends or is handed to
     * the live event channel.
     *
     * @param in     the filtered input stream
     * @param out    the output stream
     * @param client the client's address, for the log
     * @return {@code true} if the event channel now owns the connection
     * @throws IOException if a response cannot be sent
     */
    private boolean serve(ObjectInputStream in, ObjectOutputStream out,
                          String client) throws IOException {
        while (true) {
            Request request;
            try {
                request = MessageStreams.readRequest(in);
            } catch (EOFException | SocketException e) {
                return false;
            } catch (SocketTimeoutException e) {
                this.log.info("Closing idle connection to " + client + ".");
                return false;
            } catch (ObjectStreamException | ClassNotFoundException e) {
                this.log.info("Rejected a message from " + client + ": "
                        + e.getMessage());
                MessageStreams.write(out, Response.failure(null,
                        ErrorCode.VALIDATION_ERROR,
                        "The request could not be read."));
                return false;
            }
            if (request.getCommand() == Command.SUBSCRIBE_EVENTS
                    && this.events != null) {
                if (this.events.open(request, this.socket, in, out,
                        client)) {
                    return true;
                }
                this.log.info("SUBSCRIBE_EVENTS from " + client
                        + ": FAILED " + ErrorCode.PERMISSION_DENIED);
                continue;
            }
            Response response = this.dispatcher.dispatch(request);
            this.log.info(request.getCommand().name() + " from " + client
                    + ": " + outcome(response));
            MessageStreams.write(out, response);
        }
    }

    /**
     * Closes the client's socket, ignoring any error.
     */
    private void closeSocket() {
        try {
            this.socket.close();
        } catch (IOException e) {
            // Already closed; nothing more to do.
        }
    }

    /**
     * Summarises a response for the log.
     *
     * @param response the response
     * @return OK, or FAILED with the error code
     */
    private static String outcome(Response response) {
        if (response.isSuccess()) {
            return "OK";
        }
        return "FAILED " + response.getErrorCode();
    }

    /**
     * Names the client by its address and port.
     *
     * @return the remote address
     */
    private String describeClient() {
        return this.socket.getInetAddress().getHostAddress() + ":"
                + this.socket.getPort();
    }

    /**
     * Describes the handler.
     *
     * @return the client served
     */
    @Override
    public String toString() {
        return "ClientHandler for " + describeClient();
    }
}
