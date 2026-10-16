package au.edu.cqu.drs.model;

/**
 * Categories of disaster that the DRS accepts on a public report.
 * The case study names hurricane, fire and earthquake; Assessment 1 added flood
 * and required the category set to be configurable, so OTHER carries a
 * free-text
 * description supplied by the reporter.
 */
public enum DisasterType {

    FIRE("Fire"),
    EARTHQUAKE("Earthquake"),
    HURRICANE("Hurricane"),
    FLOOD("Flood"),
    STORM("Severe storm"),
    HAZARDOUS_MATERIAL("Hazardous material"),
    OTHER("Other");

    private final String displayName;

    /**
     * Creates a constant with the name shown to users.
     *
     * @param displayName the name shown to users
     */
    DisasterType(String displayName) {
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
