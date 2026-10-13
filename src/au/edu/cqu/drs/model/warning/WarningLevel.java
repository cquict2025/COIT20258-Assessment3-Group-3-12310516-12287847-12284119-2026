package au.edu.cqu.drs.model.warning;

/**
 * The three levels of the Australian Warning System, from least to most
 * serious. Each level tells the public how urgently to act.
 */
public enum WarningLevel {

    /** An incident has started; stay up to date. */
    ADVICE("Advice", "Stay informed"),
    /** Conditions are changing; act now to protect yourself. */
    WATCH_AND_ACT("Watch and Act", "Prepare to leave"),
    /** The highest level; act immediately. */
    EMERGENCY_WARNING("Emergency Warning", "Leave now");

    private final String displayName;
    private final String callToAction;

    WarningLevel(String displayName, String callToAction) {
        this.displayName = displayName;
        this.callToAction = callToAction;
    }

    /**
     * Returns the name used in the Australian Warning System.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Returns a typical call to action for the level.
     *
     * @return the call to action
     */
    public String getCallToAction() {
        return this.callToAction;
    }

    /**
     * Returns a sort key with the most serious level first.
     *
     * @return 1 for Emergency Warning, 3 for Advice
     */
    public int getUrgencyRank() {
        return values().length - ordinal();
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
