package au.edu.cqu.drs.service;

/**
 * Outcome of a service command: either a value or a structured error carrying
 * the code, a user-safe message and the affected field. Using one result type
 * keeps the controllers free of business logic, which is what the MVC
 * separation in Assessment 1 requires.
 *
 * @param <T> type of the value returned on success
 */
public class ServiceResult<T> {

    private final boolean success;
    private final T value;
    private final ErrorCode errorCode;
    private final String message;
    private final String affectedField;

    /**
     * Creates a service result.
     *
     * @param success       the success
     * @param value         the value
     * @param errorCode     the error code
     * @param message       the message
     * @param affectedField the affected field
     */
    private ServiceResult(boolean success, T value, ErrorCode errorCode,
                          String message, String affectedField) {
        this.success = success;
        this.value = value;
        this.errorCode = errorCode;
        this.message = message;
        this.affectedField = affectedField;
    }

    /**
     * Creates a successful result carrying a value.
     *
     * @param <T>   type of the value
     * @param value the value returned
     * @return a successful result
     */
    public static <T> ServiceResult<T> success(T value) {
        return new ServiceResult<>(true, value, null, null, null);
    }

    /**
     * Creates a failed result that names no particular field.
     *
     * @param <T>     type of the value expected on success
     * @param code    the error code
     * @param message a user-safe message
     * @return a failed result
     */
    public static <T> ServiceResult<T> failure(ErrorCode code, String message) {
        return new ServiceResult<>(false, null, code, message, null);
    }

    /**
     * Creates a failed result that names the field at fault.
     *
     * @param <T>           type of the value expected on success
     * @param code          the error code
     * @param message       a user-safe message
     * @param affectedField the field the error relates to
     * @return a failed result
     */
    public static <T> ServiceResult<T> failure(ErrorCode code, String message,
                                               String affectedField) {
        return new ServiceResult<>(false, null, code, message, affectedField);
    }

    /**
     * Tells whether this is success.
     *
     * @return {@code true} if it is success
     */
    public boolean isSuccess() {
        return this.success;
    }

    /**
     * Tells whether this is failure.
     *
     * @return {@code true} if it is failure
     */
    public boolean isFailure() {
        return !this.success;
    }

    /**
     * Returns the value.
     *
     * @return the value
     */
    public T getValue() {
        return this.value;
    }

    /**
     * Returns the error code.
     *
     * @return the error code
     */
    public ErrorCode getErrorCode() {
        return this.errorCode;
    }

    /**
     * Returns the message.
     *
     * @return the message
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * Returns the affected field.
     *
     * @return the affected field
     */
    public String getAffectedField() {
        return this.affectedField;
    }

    /**
     * Describes the result: the value on success, or the error code, affected
     * field and message on failure.
     *
     * @return a one-line summary of this result
     */
    @Override
    public String toString() {
        if (this.success) {
            return "Success: " + this.value;
        }
        return String.format("Failure [%s]%s: %s",
                this.errorCode,
                this.affectedField == null ? "" : " on " + this.affectedField,
                this.message);
    }
}
