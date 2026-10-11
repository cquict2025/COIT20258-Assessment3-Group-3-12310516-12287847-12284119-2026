package au.edu.cqu.drs.model;

/**
 * Result of comparing elapsed time against the target response time for a
 * priority. Creative feature 1 (incident escalation timer / SLA breach alerts).
 */
public enum EscalationState {

    ON_TRACK("On track"),
    AT_RISK("At risk"),
    BREACHED("Breached");

    private final String displayName;

    EscalationState(String displayName) {
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
