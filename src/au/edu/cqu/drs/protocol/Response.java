package au.edu.cqu.drs.protocol;

import au.edu.cqu.drs.service.ErrorCode;
import au.edu.cqu.drs.service.ServiceResult;

import java.io.Serializable;

/**
 * The server's answer to one {@link Request}.
 *
 * <p>A response mirrors {@link ServiceResult}: on success it carries the
 * value produced, and on failure the same {@link ErrorCode}, user-safe
 * message and affected field that the service returned. It repeats the
 * request ID so the client can match the answer to its question.</p>
 */
public class Response implements Serializable {

    /** Version of the serialised form sent between client and server. */
    private static final long serialVersionUID = 1L;

    private final String requestId;
    private final boolean success;
    private final Serializable value;
    private final ErrorCode errorCode;
    private final String message;
    private final String affectedField;

    /**
     * Creates a response; use the factory methods instead.
     *
     * @param requestId     ID of the request answered, or null if unknown
     * @param success       whether the command succeeded
     * @param value         value produced on success
     * @param errorCode     error code on failure
     * @param message       user-safe message on failure
     * @param affectedField name of the field concerned, or null
     */
    private Response(String requestId, boolean success, Serializable value,
                     ErrorCode errorCode, String message,
                     String affectedField) {
        this.requestId = requestId;
        this.success = success;
        this.value = value;
        this.errorCode = errorCode;
        this.message = message;
        this.affectedField = affectedField;
    }

    /**
     * Creates a successful response.
     *
     * @param requestId ID of the request answered
     * @param value     the value produced, which may be null
     * @return the response
     */
    public static Response success(String requestId, Serializable value) {
        return new Response(requestId, true, value, null, null, null);
    }

    /**
     * Creates a failed response.
     *
     * @param requestId ID of the request answered, or null if unknown
     * @param errorCode the type of failure; must not be null
     * @param message   a message safe to show to the user
     * @return the response
     */
    public static Response failure(String requestId, ErrorCode errorCode,
                                   String message) {
        return failure(requestId, errorCode, message, null);
    }

    /**
     * Creates a failed response that names the field concerned.
     *
     * @param requestId     ID of the request answered, or null if unknown
     * @param errorCode     the type of failure; must not be null
     * @param message       a message safe to show to the user
     * @param affectedField the field concerned, or null
     * @return the response
     */
    public static Response failure(String requestId, ErrorCode errorCode,
                                   String message, String affectedField) {
        if (errorCode == null) {
            throw new IllegalArgumentException(
                    "A failed response needs an error code.");
        }
        return new Response(requestId, false, null, errorCode, message,
                affectedField);
    }

    /**
     * Converts the outcome of a service call into a response.
     *
     * @param requestId ID of the request answered
     * @param result    the service result; must not be null
     * @return a response carrying the same value or error
     * @throws IllegalArgumentException if the value is not serialisable
     */
    public static Response fromServiceResult(String requestId,
                                             ServiceResult<?> result) {
        if (result.isFailure()) {
            return failure(requestId, result.getErrorCode(),
                    result.getMessage(), result.getAffectedField());
        }
        Object produced = result.getValue();
        if (produced != null && !(produced instanceof Serializable)) {
            throw new IllegalArgumentException(
                    produced.getClass().getName() + " is not Serializable.");
        }
        return success(requestId, (Serializable) produced);
    }

    /**
     * Converts the response back into a service result on the client.
     *
     * @param type the class the value must be an instance of
     * @param <T>  the expected value type
     * @return a result carrying the same value or error
     * @throws IllegalArgumentException if the value has another type
     */
    public <T> ServiceResult<T> toServiceResult(Class<T> type) {
        if (!this.success) {
            return ServiceResult.failure(this.errorCode, this.message,
                    this.affectedField);
        }
        if (this.value != null && !type.isInstance(this.value)) {
            throw new IllegalArgumentException("Response value should be "
                    + type.getSimpleName() + " but is "
                    + this.value.getClass().getSimpleName() + ".");
        }
        return ServiceResult.success(type.cast(this.value));
    }

    /**
     * Returns the ID of the request answered.
     *
     * @return the request ID, or null if the request could not be read
     */
    public String getRequestId() {
        return this.requestId;
    }

    /**
     * Tells whether the command succeeded.
     *
     * @return {@code true} on success
     */
    public boolean isSuccess() {
        return this.success;
    }

    /**
     * Returns the value produced.
     *
     * @return the value, or null on failure
     */
    public Serializable getValue() {
        return this.value;
    }

    /**
     * Returns the type of failure.
     *
     * @return the error code, or null on success
     */
    public ErrorCode getErrorCode() {
        return this.errorCode;
    }

    /**
     * Returns the message to show the user.
     *
     * @return the message, or null on success
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * Returns the name of the field the failure concerns.
     *
     * @return the field name, or null
     */
    public String getAffectedField() {
        return this.affectedField;
    }

    /**
     * Describes the response for logs.
     *
     * @return the request ID with the value or the error
     */
    @Override
    public String toString() {
        if (this.success) {
            return String.format("Response %s: OK, %s", this.requestId,
                    this.value);
        }
        String field = "";
        if (this.affectedField != null) {
            field = " on " + this.affectedField;
        }
        return String.format("Response %s: FAILED %s%s, %s", this.requestId,
                this.errorCode, field, this.message);
    }
}
