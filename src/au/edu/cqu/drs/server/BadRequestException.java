package au.edu.cqu.drs.server;

/**
 * Thrown by a handler when a request lacks an argument or carries one that
 * cannot be used. The dispatcher turns it into a VALIDATION_ERROR response
 * naming the argument.
 */
public class BadRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String field;

    /**
     * Creates the exception.
     *
     * @param field   the argument concerned
     * @param message a message safe to show to the user
     */
    public BadRequestException(String field, String message) {
        super(message);
        this.field = field;
    }

    /**
     * Returns the argument concerned.
     *
     * @return the argument name
     */
    public String getField() {
        return this.field;
    }

    /**
     * Describes the problem.
     *
     * @return the argument and message
     */
    @Override
    public String toString() {
        return "BadRequestException on " + this.field + ": " + getMessage();
    }
}
