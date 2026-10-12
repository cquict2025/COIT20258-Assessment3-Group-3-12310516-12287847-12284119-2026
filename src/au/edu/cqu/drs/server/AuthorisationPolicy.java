package au.edu.cqu.drs.server;

import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.protocol.Command;

/**
 * Decides whether a user may send a command, before any handler runs.
 *
 * <p>The services still check roles themselves; this policy is the first
 * gate, so a refused request never reaches a service or a module. The
 * default is {@link RoleBasedPolicy}. When the security module's
 * {@code au.edu.cqu.drs.security.AccessMatrix} is on the classpath,
 * {@link #createDefault()} uses it instead through
 * {@link AccessMatrixPolicy}.</p>
 */
public interface AuthorisationPolicy {

    /**
     * Decides whether a user may send a command.
     *
     * @param user    the signed-in user, or null when nobody is signed in
     * @param command the command requested
     * @return {@code true} if the request may go ahead
     */
    boolean isAllowed(User user, Command command);

    /**
     * Returns the policy the server should use: the access matrix when it
     * is installed, otherwise the role-based default.
     *
     * @return the policy
     */
    static AuthorisationPolicy createDefault() {
        AuthorisationPolicy matrix =
                AccessMatrixPolicy.find(AccessMatrixPolicy.MATRIX_CLASS);
        if (matrix != null) {
            return matrix;
        }
        return new RoleBasedPolicy();
    }
}
