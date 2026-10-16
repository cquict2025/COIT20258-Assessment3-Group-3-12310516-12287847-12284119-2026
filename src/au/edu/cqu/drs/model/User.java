package au.edu.cqu.drs.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * A staff account (UC-01, UC-14, FR-01). The class never holds a plaintext
 * password: only a salt and the hash derived from it are stored, which is what
 * NFR-04 requires.
 */
public class User implements Serializable {

    /** Version of the serialised form sent between client and server. */
    private static final long serialVersionUID = 1L;

    private String userId;
    private String username;
    private String fullName;
    private UserRole role;
    private Department department;
    private String passwordSalt;
    private String passwordHash;
    private boolean active;
    private LocalDateTime lastLoginAt;

    /**
     * Creates an empty user.
     */
    public User() {
        this.active = true;
    }

    /**
     * Creates a user.
     *
     * @param userId   the user id
     * @param username the username
     * @param fullName the full name
     * @param role     the role
     */
    public User(String userId, String username, String fullName,
                UserRole role) {
        this();
        this.userId = userId;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
    }

    /**
     * Returns the user id.
     *
     * @return the user id
     */
    public String getUserId() {
        return this.userId;
    }

    /**
     * Sets the user id.
     *
     * @param userId the user id
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Returns the username.
     *
     * @return the username
     */
    public String getUsername() {
        return this.username;
    }

    /**
     * Sets the username.
     *
     * @param username the username
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * Returns the full name.
     *
     * @return the full name
     */
    public String getFullName() {
        return this.fullName;
    }

    /**
     * Sets the full name.
     *
     * @param fullName the full name
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * Returns the role.
     *
     * @return the role
     */
    public UserRole getRole() {
        return this.role;
    }

    /**
     * Sets the role.
     *
     * @param role the role
     */
    public void setRole(UserRole role) {
        this.role = role;
    }

    /**
     * Returns the department.
     *
     * @return the department
     */
    public Department getDepartment() {
        return this.department;
    }

    /**
     * Sets the department.
     *
     * @param department the department
     */
    public void setDepartment(Department department) {
        this.department = department;
    }

    /**
     * Returns the password salt.
     *
     * @return the password salt
     */
    public String getPasswordSalt() {
        return this.passwordSalt;
    }

    /**
     * Sets the password salt.
     *
     * @param passwordSalt the password salt
     */
    public void setPasswordSalt(String passwordSalt) {
        this.passwordSalt = passwordSalt;
    }

    /**
     * Returns the password hash.
     *
     * @return the password hash
     */
    public String getPasswordHash() {
        return this.passwordHash;
    }

    /**
     * Sets the password hash.
     *
     * @param passwordHash the password hash
     */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * Tells whether this is active.
     *
     * @return {@code true} if it is active
     */
    public boolean isActive() {
        return this.active;
    }

    /**
     * Sets the active.
     *
     * @param active the active
     */
    public void setActive(boolean active) {
        this.active = active;
    }

    /**
     * Returns the last login at.
     *
     * @return the last login at
     */
    public LocalDateTime getLastLoginAt() {
        return this.lastLoginAt;
    }

    /**
     * Sets the last login at.
     *
     * @param lastLoginAt the last login at
     */
    public void setLastLoginAt(LocalDateTime lastLoginAt) {
        this.lastLoginAt = lastLoginAt;
    }

    /**
     * Tells whether this user holds the role required for a restricted
     * command.
     *
     * @param required the role the command needs
     * @return {@code true} if the user has exactly that role
     */
    public boolean hasRole(UserRole required) {
        return this.role == required;
    }

    /**
     * Summarises the account. Credentials are deliberately excluded so
     * that a hash can never reach a log file or an error dialog (NFR-04).
     *
     * @return the user ID, username, full name, role and whether active
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
