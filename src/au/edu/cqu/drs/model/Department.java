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

    Department(String displayName) {
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
