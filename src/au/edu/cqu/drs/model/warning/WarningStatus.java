package au.edu.cqu.drs.model.warning;

/**
 * Whether a warning is still in force. A warning past its expiry time is
 * also no longer in force; that is worked out from the time, not stored.
 */
public enum WarningStatus {

    /** In force until it expires. */
    ACTIVE("Active"),
    /** Withdrawn by a coordinator. */
    CANCELLED("Cancelled");

    private final String displayName;

    /**
     * Creates a status with the name shown on screen.
     *
     * @param displayName the display name
     */
    WarningStatus(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the name shown on screen.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Gives the display name.
     *
     * @return the display name
     */
    @Override
    public String toString() {
        return this.displayName;
    }
}
