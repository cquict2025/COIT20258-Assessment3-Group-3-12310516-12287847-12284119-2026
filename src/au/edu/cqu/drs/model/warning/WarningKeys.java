package au.edu.cqu.drs.model.warning;

/**
 * Names of the request arguments used by the warning commands, shared by
 * the client screens and the server module so the two always agree.
 */
public final class WarningKeys {

    /** A {@link Warning} to issue or update. */
    public static final String WARNING = "warning";

    /** A warning's code, for example WRN-0001. */
    public static final String WARNING_ID = "warningId";

    /** Why a warning is being cancelled. */
    public static final String REASON = "reason";

    private WarningKeys() {
        // Holds constants only.
    }
}
