package au.edu.cqu.drs.model;

/**
 * The external partner organisations named in the case study. Each is reachable
 * only through the PartnerGateway contract, because the DRS coordinates these
 * departments but does not control their vehicles, staff or operations.
 */
public enum Department {

    FIRE_AND_EMERGENCY("Fire and emergency"),
    HOSPITAL("Hospital"),
    ELECTRICITY("Electricity"),
    TRANSPORTATION("Transportation"),
    WASTE_MANAGEMENT("Waste management"),
    WATER_SUPPLY("Water supply"),
    SCHOOLS("Schools"),
    LAW_ENFORCEMENT("Law enforcement");

    private final String displayName;

    /**
     * Creates a constant with the name shown to users.
     *
     * @param displayName the name shown to users
     */
    Department(String displayName) {
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
