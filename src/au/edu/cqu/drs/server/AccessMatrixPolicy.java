package au.edu.cqu.drs.server;

import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.protocol.Command;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * An {@link AuthorisationPolicy} that asks the security module's access
 * matrix.
 *
 * <p>The matrix is found by reflection, so the server compiles without it.
 * It must have a method {@code boolean isAllowed(UserRole, Command)}, either
 * static or on an instance created with a public no-argument constructor.
 * Public commands, missing users and inactive users are decided here before
 * the matrix is asked, and any error from the matrix refuses the request.</p>
 */
public class AccessMatrixPolicy implements AuthorisationPolicy {

    /** Class name of the security module's access matrix. */
    public static final String MATRIX_CLASS =
            "au.edu.cqu.drs.security.AccessMatrix";

    private static final Logger LOGGER =
            Logger.getLogger(AccessMatrixPolicy.class.getName());

    private final Object matrix;
    private final Method isAllowed;

    /**
     * Creates the policy around a matrix that has been found.
     *
     * @param matrix    the matrix instance, or null for a static method
     * @param isAllowed the matrix's {@code isAllowed(UserRole, Command)}
     */
    private AccessMatrixPolicy(Object matrix, Method isAllowed) {
        this.matrix = matrix;
        this.isAllowed = isAllowed;
    }

    /**
     * Looks for an access matrix class on the classpath.
     *
     * @param className fully qualified name of the matrix class
     * @return a policy using it, or null if it is missing or unusable
     */
    public static AccessMatrixPolicy find(String className) {
        Class<?> type;
        try {
            type = Class.forName(className);
        } catch (ClassNotFoundException | LinkageError e) {
            return null;
        }
        try {
            Method method = type.getMethod("isAllowed", UserRole.class,
                    Command.class);
            if (method.getReturnType() != boolean.class) {
                LOGGER.warning(className + ".isAllowed must return boolean;"
                        + " using the role-based policy.");
                return null;
            }
            Object instance = null;
            if (!Modifier.isStatic(method.getModifiers())) {
                instance = type.getConstructor().newInstance();
            }
            LOGGER.info("Authorisation delegated to " + className + ".");
            return new AccessMatrixPolicy(instance, method);
        } catch (ReflectiveOperationException | LinkageError e) {
            LOGGER.log(Level.WARNING, className + " is not usable ("
                    + e + "); using the role-based policy.");
            return null;
        }
    }

    /**
     * Allows public commands for anyone, and asks the matrix about the role
     * of an active signed-in user.
     *
     * @param user    the signed-in user, or null
     * @param command the command requested
     * @return {@code true} if the request may go ahead
     */
    @Override
    public boolean isAllowed(User user, Command command) {
        if (command == null) {
            return false;
        }
        if (!command.isLoginRequired()) {
            return true;
        }
        if (user == null || !user.isActive() || user.getRole() == null) {
            return false;
        }
        try {
            Object answer = this.isAllowed.invoke(this.matrix,
                    user.getRole(), command);
            return Boolean.TRUE.equals(answer);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Access matrix failed for "
                    + command.name() + "; request refused.", e);
            return false;
        }
    }

    /**
     * Describes the policy.
     *
     * @return the matrix class in use
     */
    @Override
    public String toString() {
        return "AccessMatrixPolicy: delegating to "
                + this.isAllowed.getDeclaringClass().getName();
    }
}
