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

    /**
     * Creates a priority with its display name and target response time.
     *
     * @param displayName           the name shown to users
     * @param targetResponseMinutes the minutes allowed before a first response
     */
    Priority(String displayName, int targetResponseMinutes) {
        this.displayName = displayName;
        this.targetResponseMinutes = targetResponseMinutes;
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
     * Returns the target response minutes.
     *
     * @return the target response minutes
     */
    public int getTargetResponseMinutes() {
        return this.targetResponseMinutes;
    }

    /**
     * Returns the sort key so that a dashboard can order the most serious
     * incidents first.
     *
     * @return 1 for P1 up to 4 for P4
     */
    public int getRank() {
        return ordinal() + 1;
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
