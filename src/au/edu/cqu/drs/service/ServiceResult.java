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

    private ServiceResult(boolean success, T value, ErrorCode errorCode,
                          String message, String affectedField) {
        this.success = success;
        this.value = value;
        this.errorCode = errorCode;
        this.message = message;
        this.affectedField = affectedField;
    }

    public static <T> ServiceResult<T> success(T value) {
        return new ServiceResult<>(true, value, null, null, null);
    }

    public static <T> ServiceResult<T> failure(ErrorCode code, String message) {
        return new ServiceResult<>(false, null, code, message, null);
    }

    public static <T> ServiceResult<T> failure(ErrorCode code, String message,
                                               String affectedField) {
        return new ServiceResult<>(false, null, code, message, affectedField);
    }

    public boolean isSuccess() {
        return this.success;
    }

    public boolean isFailure() {
        return !this.success;
    }

    public T getValue() {
        return this.value;
    }

    public ErrorCode getErrorCode() {
        return this.errorCode;
    }

    public String getMessage() {
        return this.message;
    }

    public String getAffectedField() {
        return this.affectedField;
    }

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
