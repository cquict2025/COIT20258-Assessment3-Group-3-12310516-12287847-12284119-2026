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

    /**
     * Creates a constant with the name shown to users.
     *
     * @param displayName the name shown to users
     */
    EscalationState(String displayName) {
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
