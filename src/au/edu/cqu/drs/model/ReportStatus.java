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

    ReportStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}
