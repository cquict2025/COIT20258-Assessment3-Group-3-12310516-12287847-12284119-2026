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

    UserRole(String displayName) {
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
