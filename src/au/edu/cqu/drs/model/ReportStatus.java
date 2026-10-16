package au.edu.cqu.drs.model;

/**
 * Lifecycle of a public disaster report before it becomes an incident.
 * Assessment 1 (FR-06) listed report states and incident states in a single
 * sequence; the revised specification separates them because a report is
 * evidence while an incident is the authoritative operational record.
 */
public enum ReportStatus {

    DRAFT("Draft"),
    SUBMITTED("Submitted"),
    VALIDATED("Validated"),
    REJECTED("Rejected"),
    LINKED("Linked to existing incident");

    private final String displayName;

    /**
     * Creates a constant with the name shown to users.
     *
     * @param displayName the name shown to users
     */
    ReportStatus(String displayName) {
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
     * Returns the display name, so the constant reads well in lists and logs.
     *
     * @return the display name
     */
    @Override
    public String toString() {
        return this.displayName;
    }
}
