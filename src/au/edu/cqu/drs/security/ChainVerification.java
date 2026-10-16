package au.edu.cqu.drs.security;

/**
 * The result of checking the audit chain: whether it is intact and, if not,
 * the first record that fails.
 */
public final class ChainVerification {

    private final int checked;
    private final int unchained;
    private final String firstBrokenEventId;
    private final String reason;

    /**
     * Creates a chain verification.
     *
     * @param checked            the checked
     * @param unchained          the unchained
     * @param firstBrokenEventId the first broken event id
     * @param reason             the reason
     */
    private ChainVerification(int checked, int unchained,
            String firstBrokenEventId, String reason) {
        this.checked = checked;
        this.unchained = unchained;
        this.firstBrokenEventId = firstBrokenEventId;
        this.reason = reason;
    }

    /**
     * Records an intact chain.
     *
     * @param checked   how many chained records were checked
     * @param unchained how many older records, saved before the chain was
     *                  introduced, have no hash to check
     * @return the result
     */
    public static ChainVerification intact(int checked, int unchained) {
        return new ChainVerification(checked, unchained, null, null);
    }

    /**
     * Records a broken chain.
     *
     * @param checked   how many records were checked before the failure
     * @param unchained how many older records have no hash
     * @param eventId   the code of the first record that fails
     * @param reason    what is wrong with it
     * @return the result
     */
    public static ChainVerification broken(int checked, int unchained,
            String eventId, String reason) {
        return new ChainVerification(checked, unchained, eventId, reason);
    }

    /**
     * Tells whether every chained record checked out.
     *
     * @return true if nothing was changed or removed
     */
    public boolean isIntact() {
        return this.firstBrokenEventId == null;
    }

    /**
     * Returns how many chained records were checked.
     *
     * @return the count
     */
    public int getChecked() {
        return this.checked;
    }

    /**
     * Returns how many older records have no hash to check.
     *
     * @return the count
     */
    public int getUnchained() {
        return this.unchained;
    }

    /**
     * Returns the code of the first record that fails.
     *
     * @return the code, or null if the chain is intact
     */
    public String getFirstBrokenEventId() {
        return this.firstBrokenEventId;
    }

    /**
     * Returns what is wrong with the first failing record.
     *
     * @return the reason, or null if the chain is intact
     */
    public String getReason() {
        return this.reason;
    }

    /**
     * Describes the result.
     *
     * @return one line for a log or the report
     */
    @Override
    public String toString() {
        String older = this.unchained == 0 ? ""
                : " (" + this.unchained + " older record(s) have no hash)";
        if (isIntact()) {
            return "Audit chain intact: " + this.checked
                    + " record(s) checked" + older;
        }
        return "Audit chain broken at " + this.firstBrokenEventId + ": "
                + this.reason + older;
    }
}
