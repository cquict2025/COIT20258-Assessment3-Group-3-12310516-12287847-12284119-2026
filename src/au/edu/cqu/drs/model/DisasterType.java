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

    DisasterType(String displayName) {
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
