package au.edu.cqu.drs.protocol;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One message from a client asking the server to perform a {@link Command}.
 *
 * <p>The request carries a unique ID that the matching {@link Response}
 * repeats, the session token of the signed-in user (absent for public
 * commands), the arguments as named values, the time it was created and an
 * optional signature. The signature is left empty here; the security module
 * fills it for actions that must be signed.</p>
 */
public class Request implements Serializable {

    /** Version of the serialised form sent between client and server. */
    private static final long serialVersionUID = 1L;

    /** Characters of the session token shown by {@link #toString()}. */
    private static final int TOKEN_PREFIX_LENGTH = 4;

    private final String requestId;
    private final Command command;
    private final String sessionToken;
    private final HashMap<String, Serializable> payload;
    private final Instant timestamp;
    private byte[] signature;

    /**
     * Creates a request with a new random ID, stamped with the current time.
     *
     * @param command      the operation to perform; must not be null
     * @param sessionToken the session token, or null for a public command
     */
    public Request(Command command, String sessionToken) {
        if (command == null) {
            throw new IllegalArgumentException("A request needs a command.");
        }
        this.requestId = UUID.randomUUID().toString();
        this.command = command;
        this.sessionToken = sessionToken;
        this.payload = new HashMap<>();
        this.timestamp = Instant.now();
        this.signature = new byte[0];
    }

    /**
     * Adds a named argument, replacing any value already under that name.
     *
     * @param key   the argument name; must not be null or blank
     * @param value the value, which may be null
     * @return this request, so arguments can be chained
     */
    public Request put(String key, Serializable value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("An argument needs a name.");
        }
        this.payload.put(key, value);
        return this;
    }

    /**
     * Reads a named argument as the expected type.
     *
     * @param key  the argument name
     * @param type the class the value must be an instance of
     * @param <T>  the expected type
     * @return the value, or null when the argument is absent or null
     * @throws IllegalArgumentException if the value has another type
     */
    public <T> T get(String key, Class<T> type) {
        Object value = this.payload.get(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Argument '" + key
                    + "' should be " + type.getSimpleName() + " but is "
                    + value.getClass().getSimpleName() + ".");
        }
        return type.cast(value);
    }

    /**
     * Tells whether a named argument was sent.
     *
     * @param key the argument name
     * @return {@code true} if the payload has the name, even with null
     */
    public boolean has(String key) {
        return this.payload.containsKey(key);
    }

    /**
     * Returns the unique ID that the response will repeat.
     *
     * @return the request ID
     */
    public String getRequestId() {
        return this.requestId;
    }

    /**
     * Returns the operation requested.
     *
     * @return the command
     */
    public Command getCommand() {
        return this.command;
    }

    /**
     * Returns the session token of the sender.
     *
     * @return the token, or null for a public command
     */
    public String getSessionToken() {
        return this.sessionToken;
    }

    /**
     * Returns the arguments as a read-only map.
     *
     * @return the named arguments
     */
    public Map<String, Serializable> getPayload() {
        return Collections.unmodifiableMap(this.payload);
    }

    /**
     * Returns when the client created the request. The server uses its own
     * clock for anything it records.
     *
     * @return the creation time
     */
    public Instant getTimestamp() {
        return this.timestamp;
    }

    /**
     * Returns a copy of the signature.
     *
     * @return the signature bytes, empty when the request is not signed
     */
    public byte[] getSignature() {
        return this.signature.clone();
    }

    /**
     * Stores the signature for a signed action.
     *
     * @param signature the signature bytes, or null to clear it
     */
    public void setSignature(byte[] signature) {
        if (signature == null) {
            this.signature = new byte[0];
        } else {
            this.signature = signature.clone();
        }
    }

    /**
     * Describes the request for logs without revealing the session token.
     *
     * @return the ID, command, argument names, time and signature size
     */
    @Override
    public String toString() {
        String token = "none";
        if (this.sessionToken != null) {
            int shown = Math.min(TOKEN_PREFIX_LENGTH,
                    this.sessionToken.length());
            token = this.sessionToken.substring(0, shown) + "...";
        }
        return String.format(
                "Request %s: %s, token %s, arguments %s, at %s, "
                        + "signature %d bytes",
                this.requestId, this.command.name(), token,
                this.payload.keySet(), this.timestamp,
                this.signature.length);
    }
}
