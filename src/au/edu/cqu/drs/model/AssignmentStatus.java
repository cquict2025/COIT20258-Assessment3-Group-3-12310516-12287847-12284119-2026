package au.edu.cqu.drs.model;

/**
 * State of a request sent to a partner department (FR-08).
 * PENDING_TRANSMISSION and DELIVERY_FAILED exist because Assessment 1 (UR-10)
 * requires a technical delivery failure to stay visible and retryable instead
 * of
 * being mistaken for a departmental rejection.
 */
public enum AssignmentStatus {

    PENDING_TRANSMISSION("Pending transmission"),
    DELIVERY_FAILED("Delivery failed"),
    AWAITING_RESPONSE("Awaiting response"),
    ACCEPTED("Accepted"),
    REJECTED("Rejected"),
    CLARIFICATION_REQUESTED("Clarification requested"),
    COMPLETED("Completed");

    private final String displayName;

    AssignmentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * An assignment is resolved when it no longer blocks incident closure
     * (UC-13).
     */
    public boolean isResolved() {
        return this == ACCEPTED || this == REJECTED || this == COMPLETED;
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}
