package au.edu.cqu.drs.server;

import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.protocol.Command;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RoleBasedPolicy} and {@link AccessMatrixPolicy}.
 */
class AuthorisationPolicyTest {

    private static final String MATRICES =
            "au.edu.cqu.drs.server.FakeAccessMatrices$";

    private final RoleBasedPolicy policy = new RoleBasedPolicy();

    /**
     * Checks that public commands are allowed without a user and others are
     * refused.
     */
    @Test
    @DisplayName("TC-AP-01 public commands are allowed without a user and "
            + "others are refused")
    void publicCommandsNeedNoUser() {
        assertTrue(this.policy.isAllowed(null, Command.PING));
        assertTrue(this.policy.isAllowed(null, Command.LOGIN));
        assertTrue(this.policy.isAllowed(null, Command.SUBMIT_REPORT));
        assertTrue(this.policy.isAllowed(null,
                Command.LIST_PUBLIC_WARNINGS));
        assertFalse(this.policy.isAllowed(null, Command.LIST_INCIDENTS));
        assertFalse(this.policy.isAllowed(null, Command.LOGOUT));
        assertFalse(this.policy.isAllowed(user(UserRole.COORDINATOR), null));
    }

    /**
     * Checks that the policy mirrors the role checks in the services.
     */
    @Test
    @DisplayName("TC-AP-02 the policy mirrors the role checks in the "
            + "services")
    void mirrorsServiceRoles() {
        User coordinator = user(UserRole.COORDINATOR);
        User liaison = user(UserRole.LIAISON);
        User responder = user(UserRole.RESPONDER);

        assertTrue(this.policy.isAllowed(coordinator, Command.DECIDE_REPORT));
        assertFalse(this.policy.isAllowed(liaison, Command.DECIDE_REPORT));
        assertTrue(this.policy.isAllowed(liaison,
                Command.RESPOND_TO_ASSIGNMENT));
        assertFalse(this.policy.isAllowed(coordinator,
                Command.RESPOND_TO_ASSIGNMENT));
        assertTrue(this.policy.isAllowed(liaison,
                Command.ALLOCATE_RESOURCE));
        assertFalse(this.policy.isAllowed(responder,
                Command.ALLOCATE_RESOURCE));
        assertTrue(this.policy.isAllowed(responder,
                Command.ADD_FIELD_UPDATE));
        assertFalse(this.policy.isAllowed(user(UserRole.ADMINISTRATOR),
                Command.ADD_FIELD_UPDATE));
        assertEquals(EnumSet.of(UserRole.COORDINATOR),
                this.policy.rolesFor(Command.ISSUE_WARNING));
    }

    /**
     * Checks that accounts and imports are for administrators; reads are open
     * to any signed-in user.
     */
    @Test
    @DisplayName("TC-AP-03 accounts and imports are for administrators; "
            + "reads are open to any signed-in user")
    void administratorAndOpenCommands() {
        User admin = user(UserRole.ADMINISTRATOR);
        User responder = user(UserRole.RESPONDER);

        assertTrue(this.policy.isAllowed(admin, Command.REGISTER_USER));
        assertTrue(this.policy.isAllowed(admin, Command.IMPORT_INCIDENT));
        assertFalse(this.policy.isAllowed(user(UserRole.COORDINATOR),
                Command.IMPORT_INCIDENT));
        assertTrue(this.policy.isAllowed(responder, Command.LIST_INCIDENTS));
        assertTrue(this.policy.isAllowed(responder, Command.LOGOUT));
        assertEquals(EnumSet.allOf(UserRole.class),
                this.policy.rolesFor(Command.LIST_INCIDENTS));
    }

    /**
     * Checks that an inactive user is refused everything that needs a login.
     */
    @Test
    @DisplayName("TC-AP-04 an inactive user is refused everything that "
            + "needs a login")
    void inactiveUserRefused() {
        User inactive = user(UserRole.COORDINATOR);
        inactive.setActive(false);

        for (Command command : Command.values()) {
            assertEquals(!command.isLoginRequired(),
                    this.policy.isAllowed(inactive, command),
                    command.name());
        }
    }

    /**
     * Checks that the access matrix is used through a static or an instance
     * method.
     */
    @Test
    @DisplayName("TC-AP-05 the access matrix is used through a static or an "
            + "instance method")
    void delegatesToMatrix() {
        AccessMatrixPolicy staticMatrix =
                AccessMatrixPolicy.find(MATRICES + "StaticMatrix");
        AccessMatrixPolicy instanceMatrix =
                AccessMatrixPolicy.find(MATRICES + "InstanceMatrix");

        assertNotNull(staticMatrix);
        assertTrue(staticMatrix.isAllowed(user(UserRole.COORDINATOR),
                Command.IMPORT_AUDIT));
        assertFalse(staticMatrix.isAllowed(user(UserRole.ADMINISTRATOR),
                Command.IMPORT_AUDIT));
        assertTrue(staticMatrix.isAllowed(null, Command.PING));
        assertFalse(staticMatrix.isAllowed(null, Command.LIST_INCIDENTS));
        assertNotNull(instanceMatrix);
        assertTrue(instanceMatrix.isAllowed(user(UserRole.RESPONDER),
                Command.LIST_INCIDENTS));
        assertFalse(instanceMatrix.isAllowed(user(UserRole.RESPONDER),
                Command.LIST_REPORTS));
        assertTrue(instanceMatrix.toString().contains("InstanceMatrix"));
    }

    /**
     * Checks that a missing, faulty or wrongly typed matrix falls back safely.
     */
    @Test
    @DisplayName("TC-AP-06 a missing, faulty or wrongly typed matrix falls "
            + "back safely")
    void matrixFallbacks() {
        AccessMatrixPolicy failing =
                AccessMatrixPolicy.find(MATRICES + "FailingMatrix");

        assertNull(AccessMatrixPolicy.find("au.edu.cqu.drs.no.Matrix"));
        assertNull(AccessMatrixPolicy.find(MATRICES + "WrongTypeMatrix"));
        assertNotNull(failing);
        assertFalse(failing.isAllowed(user(UserRole.COORDINATOR),
                Command.LIST_INCIDENTS));
        // The security module's AccessMatrix is installed, so the default
        // is now the matrix; the fallback is covered by the nulls above.
        assertInstanceOf(AccessMatrixPolicy.class,
                AuthorisationPolicy.createDefault());
    }

    /**
     * Creates an active user with a role.
     *
     * @param role the role
     * @return the user
     */
    private static User user(UserRole role) {
        return new User("USR-1", role.name().toLowerCase(), "Test User",
                role);
    }

    /**
     * Describes this test class.
     *
     * @return a short description of this object
     */
    @Override
    public String toString() {
        return "AuthorisationPolicyTest: tests for the authorisation policies";
    }
}
