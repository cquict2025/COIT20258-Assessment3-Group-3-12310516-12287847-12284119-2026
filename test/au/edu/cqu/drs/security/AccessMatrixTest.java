package au.edu.cqu.drs.security;

import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.server.AccessMatrixPolicy;
import au.edu.cqu.drs.server.AuthorisationPolicy;
import au.edu.cqu.drs.server.RoleBasedPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the access-rights matrix and how the server picks it up. */
class AccessMatrixTest {

    /** Rules deliberately tighter than the server's role-based default. */
    private static final Set<Command> TIGHTENED =
            EnumSet.of(Command.ACK_WARNING);

    @Test
    @DisplayName("TC-SEC-10 every command has a row in the matrix")
    void everyCommandHasARule() {
        for (Command command : Command.values()) {
            assertTrue(AccessMatrix.hasRule(command),
                    command + " has no row; decide who may use it");
        }
    }

    @Test
    @DisplayName("TC-SEC-11 only a coordinator issues or cancels a warning")
    void onlyCoordinatorControlsWarnings() {
        for (UserRole role : UserRole.values()) {
            boolean coordinator = role == UserRole.COORDINATOR;
            assertEquals(coordinator,
                    AccessMatrix.isAllowed(role, Command.ISSUE_WARNING));
            assertEquals(coordinator,
                    AccessMatrix.isAllowed(role, Command.CANCEL_WARNING));
        }
        assertEquals(EnumSet.of(UserRole.LIAISON),
                AccessMatrix.rolesFor(Command.ACK_WARNING));
    }

    @Test
    @DisplayName("TC-SEC-12 a responder cannot decide reports, create "
            + "accounts or read the audit log")
    void responderIsLeastPrivileged() {
        assertFalse(AccessMatrix.isAllowed(UserRole.RESPONDER,
                Command.DECIDE_REPORT));
        assertFalse(AccessMatrix.isAllowed(UserRole.RESPONDER,
                Command.REGISTER_USER));
        assertFalse(AccessMatrix.isAllowed(UserRole.RESPONDER,
                Command.LIST_AUDIT_EVENTS));
        assertTrue(AccessMatrix.isAllowed(UserRole.RESPONDER,
                Command.ADD_FIELD_UPDATE));
        assertFalse(AccessMatrix.isAllowed(null, Command.LIST_INCIDENTS));
        assertFalse(AccessMatrix.isAllowed(UserRole.COORDINATOR, null));
    }

    @Test
    @DisplayName("TC-SEC-13 the matrix keeps every rule of the server's "
            + "default policy, apart from the ones tightened on purpose")
    void agreesWithRoleBasedPolicy() {
        RoleBasedPolicy defaults = new RoleBasedPolicy();
        for (Command command : Command.values()) {
            if (!command.isLoginRequired() || TIGHTENED.contains(command)) {
                continue;
            }
            assertEquals(defaults.rolesFor(command),
                    AccessMatrix.rolesFor(command), command.name());
        }
    }

    @Test
    @DisplayName("TC-SEC-14 the server uses this matrix for authorisation")
    void serverPicksUpTheMatrix() {
        AuthorisationPolicy policy = AuthorisationPolicy.createDefault();
        assertInstanceOf(AccessMatrixPolicy.class, policy);

        User liaison = new User(null, "liaison", "Sam", UserRole.LIAISON);
        User responder = new User(null, "jo", "Jo", UserRole.RESPONDER);
        assertTrue(policy.isAllowed(liaison, Command.ACK_WARNING));
        assertFalse(policy.isAllowed(responder, Command.ACK_WARNING));
        assertTrue(policy.isAllowed(null, Command.LIST_PUBLIC_WARNINGS));
        assertNotNull(AccessMatrix.toMarkdown());
    }
}
