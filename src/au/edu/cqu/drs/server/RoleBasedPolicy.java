package au.edu.cqu.drs.server;

import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.protocol.Command;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The default {@link AuthorisationPolicy}: a fixed table of the roles
 * allowed to send each command.
 *
 * <p>The table mirrors the role checks the services already make, for
 * example only a coordinator may decide a report and only a liaison may
 * respond to an assignment. Commands with no restriction in the services are
 * open to any active signed-in user, except account creation, the audit log
 * and imports, which are limited to administrators (and, for the audit log,
 * coordinators). Public commands need no user at all.</p>
 */
public class RoleBasedPolicy implements AuthorisationPolicy {

    private final Map<Command, Set<UserRole>> restricted;

    /**
     * Creates the policy with the role table.
     */
    public RoleBasedPolicy() {
        this.restricted = new EnumMap<>(Command.class);
        Set<UserRole> coordinator = EnumSet.of(UserRole.COORDINATOR);
        Set<UserRole> administrator = EnumSet.of(UserRole.ADMINISTRATOR);
        restrict(coordinator, Command.DECIDE_REPORT,
                Command.CREATE_INCIDENT_FROM_REPORT,
                Command.CHANGE_INCIDENT_STATUS, Command.CLOSE_INCIDENT,
                Command.SCORE_ASSESSMENT, Command.CONFIRM_PRIORITY,
                Command.CREATE_ASSIGNMENT, Command.RETRY_DELIVERY,
                Command.ISSUE_WARNING, Command.UPDATE_WARNING,
                Command.CANCEL_WARNING);
        restrict(EnumSet.of(UserRole.LIAISON),
                Command.RESPOND_TO_ASSIGNMENT);
        restrict(EnumSet.of(UserRole.COORDINATOR, UserRole.LIAISON),
                Command.ALLOCATE_RESOURCE);
        restrict(EnumSet.of(UserRole.RESPONDER, UserRole.LIAISON,
                UserRole.COORDINATOR), Command.ADD_FIELD_UPDATE);
        restrict(EnumSet.of(UserRole.COORDINATOR, UserRole.ADMINISTRATOR),
                Command.SET_PARTNER_AVAILABLE, Command.LIST_AUDIT_EVENTS,
                Command.LIST_AUDIT_FOR_ENTITY);
        restrict(administrator, Command.REGISTER_USER, Command.IMPORT_USER,
                Command.IMPORT_REPORT, Command.IMPORT_INCIDENT,
                Command.IMPORT_ASSESSMENT, Command.IMPORT_ASSIGNMENT,
                Command.IMPORT_FIELD_UPDATE, Command.IMPORT_AUDIT);
    }

    /**
     * Allows public commands for anyone, and other commands for an active
     * user whose role the table permits.
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
        Set<UserRole> roles = this.restricted.get(command);
        return roles == null || roles.contains(user.getRole());
    }

    /**
     * Returns the roles allowed to send a command.
     *
     * @param command the command
     * @return the roles, or every role if the command is not restricted
     */
    public Set<UserRole> rolesFor(Command command) {
        Set<UserRole> roles = this.restricted.get(command);
        if (roles == null) {
            return EnumSet.allOf(UserRole.class);
        }
        return EnumSet.copyOf(roles);
    }

    /**
     * Limits commands to a set of roles.
     *
     * @param roles    the roles allowed
     * @param commands the commands to limit
     */
    private void restrict(Set<UserRole> roles, Command... commands) {
        for (Command command : commands) {
            this.restricted.put(command, roles);
        }
    }

    /**
     * Describes the policy.
     *
     * @return the number of restricted commands
     */
    @Override
    public String toString() {
        return "RoleBasedPolicy: " + this.restricted.size()
                + " restricted command(s), the rest open to any signed-in "
                + "user";
    }
}
