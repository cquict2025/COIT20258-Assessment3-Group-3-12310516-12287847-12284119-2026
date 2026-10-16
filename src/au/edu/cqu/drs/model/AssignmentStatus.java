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

    /**
     * Creates a constant with the name shown to users.
     *
     * @param displayName the name shown to users
     */
    AssignmentStatus(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the display name.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Tells whether an assignment in this status no longer blocks incident
     * closure (UC-13).
     *
     * @return {@code true} for Accepted, Rejected and Completed
     */
    public boolean isResolved() {
        return this == ACCEPTED || this == REJECTED || this == COMPLETED;
    }

    /**
     * Returns the display name, so the constant reads well in lists and logs.
     *
     * @return the display name
     */
    @Override
    public String toString() {
        return this.displayName;
    }
}
