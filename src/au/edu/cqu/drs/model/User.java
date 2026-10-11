package au.edu.cqu.drs.model;

import java.time.LocalDateTime;

/**
 * A staff account (UC-01, UC-14, FR-01). The class never holds a plaintext
 * password: only a salt and the hash derived from it are stored, which is what
 * NFR-04 requires.
 */
public class User {

    private String userId;
    private String username;
    private String fullName;
    private UserRole role;
    private Department department;
    private String passwordSalt;
    private String passwordHash;
    private boolean active;
    private LocalDateTime lastLoginAt;

    public User() {
        this.active = true;
    }

    public User(String userId, String username, String fullName,
                UserRole role) {
        this();
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
    }

    public String getUserId() {
        return this.userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return this.username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return this.fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public UserRole getRole() {
        return this.role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public Department getDepartment() {
        return this.department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public String getPasswordSalt() {
        return this.passwordSalt;
    }

    public void setPasswordSalt(String passwordSalt) {
        this.passwordSalt = passwordSalt;
    }

    public String getPasswordHash() {
        return this.passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isActive() {
        return this.active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getLastLoginAt() {
        return this.lastLoginAt;
    }

    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    /** True when this user holds the role required for a restricted command. */
    public boolean hasRole(UserRole required) {
        return this.role == required;
    }

    /**
     * Credentials are deliberately excluded from this summary so that a hash
     * can
     * never reach a log file or an error dialog (NFR-04).
     */
    @Override
    public String toString() {
        return String.format("User %s | %s (%s) | %s | %s",
                this.userId == null ? "unsaved" : this.userId,
                this.username,
                this.fullName,
                this.role == null ? "no role" : this.role.getDisplayName(),
                this.active ? "active" : "disabled");
    }
}
