package au.edu.cqu.drs.model;

/**
 * Confirmed operational priority of an incident (FR-04, FR-05).
 * Each priority carries the target first-response time in minutes that the
 * escalation monitor measures against (creative feature 1).
 */
public enum Priority {

    P1("P1 - Critical", 15),
    P2("P2 - High", 60),
    P3("P3 - Moderate", 240),
    P4("P4 - Low", 1440);

    private final String displayName;
    private final int targetResponseMinutes;

    Priority(String displayName, int targetResponseMinutes) {
        this.displayName = displayName;
        this.targetResponseMinutes = targetResponseMinutes;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    public int getTargetResponseMinutes() {
        return this.targetResponseMinutes;
    }

    /**
     * Sort key so that a dashboard can order the most serious incidents first.
     */
    public int getRank() {
        return ordinal() + 1;
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}
