package au.edu.cqu.drs.protocol;

import au.edu.cqu.drs.model.User;

import java.io.Serializable;

/**
 * The value returned by a successful LOGIN: the session token to send with
 * later requests and the signed-in user, without password details.
 */
public class LoginResult implements Serializable {

    /** Version of the serialised form sent between client and server. */
    private static final long serialVersionUID = 1L;

    private final String sessionToken;
    private final User user;

    /**
     * Creates the result.
     *
     * @param sessionToken the new session token
     * @param user         the signed-in user, without password details
     */
    public LoginResult(String sessionToken, User user) {
        this.sessionToken = sessionToken;
        this.user = user;
    }

    /**
     * Returns the session token.
     *
     * @return the token
     */
    public String getSessionToken() {
        return this.sessionToken;
    }

    /**
     * Returns the signed-in user.
     *
     * @return the user
     */
    public User getUser() {
        return this.user;
    }

    /**
     * Describes the result without revealing the token.
     *
     * @return the username and role
     */
    @Override
    public String toString() {
        String name = "nobody";
        if (this.user != null) {
            name = this.user.getUsername() + " (" + this.user.getRole() + ")";
        }
        return "LoginResult: signed in as " + name;
    }
}
