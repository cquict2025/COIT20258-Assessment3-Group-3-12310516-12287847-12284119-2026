package au.edu.cqu.drs.service;

/**
 * The typed business failures that Assessment 1 committed to. Controllers
 * translate these into actionable messages; unexpected exceptions are handled
 * separately so that a technical fault is never presented as a business rule.
 */
public enum ErrorCode {

    VALIDATION_ERROR,
    PERMISSION_DENIED,
    NOT_FOUND,
    CONFLICT,
    ADAPTER_UNAVAILABLE;

    /**
     * Returns the constant's name, which is the code shown in results and
     * logs.
     *
     * @return the constant's name
     */
    @Override
    public String toString() {
        return name();
    }
}
