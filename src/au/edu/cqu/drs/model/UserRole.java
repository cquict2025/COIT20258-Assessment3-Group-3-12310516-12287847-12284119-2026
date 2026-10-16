package au.edu.cqu.drs.model;

/**
 * Least-privilege roles enforced in the service layer (FR-01, NFR-04).
 * A member of the public is deliberately not a role: UR-01 requires reporting
 * without an account.
 */
public enum UserRole {

    COORDINATOR("DRS coordinator"),
    LIAISON("Department liaison"),
    RESPONDER("Field responder"),
    ADMINISTRATOR("System administrator");

    private final String displayName;

    /**
     * Creates a constant with the name shown to users.
     *
     * @param displayName the name shown to users
     */
    UserRole(String displayName) {
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
