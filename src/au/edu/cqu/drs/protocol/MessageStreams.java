package au.edu.cqu.drs.protocol;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.Socket;

/**
 * Opens object streams over a socket and sends and receives protocol
 * messages through them.
 *
 * <p>Every input stream opened here has a {@link ProtocolFilter} installed
 * before anything is read, so neither the client nor the server can be
 * handed a class outside the allow-list. Output streams are flushed as soon
 * as they are opened: an {@link ObjectInputStream} waits for the stream
 * header, so without the flush both ends would wait for each other.</p>
 */
public final class MessageStreams {

    /**
     * Prevents instances; every method is static.
     */
    private MessageStreams() {
    }

    /**
     * Opens an object output stream on a socket.
     *
     * @param socket the connected socket
     * @return a buffered stream whose header has been sent
     * @throws IOException if the socket cannot be written
     */
    public static ObjectOutputStream openOutput(Socket socket)
            throws IOException {
        return openOutput(socket.getOutputStream());
    }

    /**
     * Opens an object output stream on any byte stream.
     *
     * @param out the byte stream to write to
     * @return a buffered stream whose header has been sent
     * @throws IOException if the stream cannot be written
     */
    public static ObjectOutputStream openOutput(OutputStream out)
            throws IOException {
        ObjectOutputStream stream =
                new ObjectOutputStream(new BufferedOutputStream(out));
        stream.flush();
        return stream;
    }

    /**
     * Opens a filtered object input stream on a socket.
     *
     * @param socket the connected socket
     * @return a buffered stream with the {@link ProtocolFilter} installed
     * @throws IOException if the stream header cannot be read
     */
    public static ObjectInputStream openInput(Socket socket)
            throws IOException {
        return openInput(socket.getInputStream());
    }

    /**
     * Opens a filtered object input stream on any byte stream.
     *
     * @param in the byte stream to read from
     * @return a buffered stream with the {@link ProtocolFilter} installed
     * @throws IOException if the stream header cannot be read
     */
    public static ObjectInputStream openInput(InputStream in)
            throws IOException {
        ObjectInputStream stream =
                new ObjectInputStream(new BufferedInputStream(in));
        stream.setObjectInputFilter(new ProtocolFilter());
        return stream;
    }

    /**
     * Sends one message and flushes it.
     *
     * <p>The stream is reset after each message so that an object sent again
     * after it has changed is sent in full, not as a reference to the copy
     * the receiver already has.</p>
     *
     * @param out     the stream opened by {@link #openOutput}
     * @param message the request, response or event to send
     * @throws IOException if the message cannot be written
     */
    public static void write(ObjectOutputStream out, Serializable message)
            throws IOException {
        out.writeObject(message);
        out.reset();
        out.flush();
    }

    /**
     * Reads one message of the expected type.
     *
     * @param in   the stream opened by {@link #openInput}
     * @param type the class the message must be an instance of
     * @param <T>  the expected message type
     * @return the message
     * @throws IOException            if the stream fails, the filter rejects
     *                                a class, or the message has another type
     * @throws ClassNotFoundException if a class is missing on this side
     */
    public static <T> T read(ObjectInputStream in, Class<T> type)
            throws IOException, ClassNotFoundException {
        Object message = in.readObject();
        if (!type.isInstance(message)) {
            String actual = "null";
            if (message != null) {
                actual = message.getClass().getName();
            }
            throw new InvalidObjectException("Expected "
                    + type.getSimpleName() + " but received " + actual + ".");
        }
        return type.cast(message);
    }

    /**
     * Reads one request; used by the server.
     *
     * @param in the stream opened by {@link #openInput}
     * @return the request
     * @throws IOException            if the message is not a valid request
     * @throws ClassNotFoundException if a class is missing on this side
     */
    public static Request readRequest(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        return read(in, Request.class);
    }

    /**
     * Reads one response; used by the client.
     *
     * @param in the stream opened by {@link #openInput}
     * @return the response
     * @throws IOException            if the message is not a valid response
     * @throws ClassNotFoundException if a class is missing on this side
     */
    public static Response readResponse(ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        return read(in, Response.class);
    }

    /**
     * Describes this helper class.
     *
     * @return a short description
     */
    @Override
    public String toString() {
        return "MessageStreams: filtered object streams for protocol messages";
    }
}
