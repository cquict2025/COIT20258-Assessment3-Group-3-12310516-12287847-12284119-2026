package au.edu.cqu.drs.security;

import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.protocol.Command;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The access-rights matrix: which roles may send each command (spec 2.5).
 *
 * <p>The server's {@code AccessMatrixPolicy} finds this class by name and
 * asks {@link #isAllowed(UserRole, Command)} before every request from a
 * signed-in user, so the rule is enforced on the server and never only by
 * hiding a button in the GUI. Commands that need no sign-in (for example
 * {@code SUBMIT_REPORT} and {@code LIST_PUBLIC_WARNINGS}) are let through by
 * the policy before the matrix is asked.</p>
 *
 * <p>Every command has an explicit row. A command with no row is refused for
 * every role, so a new command stays closed until someone decides who may
 * use it, and a test fails until they do.</p>
 */
public final class AccessMatrix {

    private static final Set<UserRole> EVERYONE =
            Collections.unmodifiableSet(EnumSet.allOf(UserRole.class));

    private static final Map<Command, Set<UserRole>> RULES = buildRules();

    /**
     * Prevents instances; every member is static.
     */
    private AccessMatrix() {
        // Holds the matrix only.
    }

    /**
     * Tells whether a role may send a command.
     *
     * @param role    the signed-in user's role
     * @param command the command requested
     * @return true if the matrix allows it; false for a null argument or a
     *         command with no row
     */
    public static boolean isAllowed(UserRole role, Command command) {
        if (role == null || command == null) {
            return false;
        }
        Set<UserRole> roles = RULES.get(command);
        return roles != null && roles.contains(role);
    }

    /**
     * Returns the roles allowed to send a command.
     *
     * @param command the command
     * @return the roles, empty if the command has no row
     */
    public static Set<UserRole> rolesFor(Command command) {
        Set<UserRole> roles = RULES.get(command);
        return roles == null ? EnumSet.noneOf(UserRole.class)
                : EnumSet.copyOf(roles);
    }

    /**
     * Tells whether a command has a row in the matrix.
     *
     * @param command the command
     * @return true if someone has decided who may use it
     */
    public static boolean hasRule(Command command) {
        return RULES.containsKey(command);
    }

    /**
     * Writes the matrix as a Markdown table, one row per command and one
     * column per role, for the security section of the report.
     *
     * @return the table
     */
    public static String toMarkdown() {
        StringBuilder table = new StringBuilder("| Command |");
        StringBuilder rule = new StringBuilder("|---|");
        for (UserRole role : UserRole.values()) {
            table.append(' ').append(role.getDisplayName()).append(" |");
            rule.append(":-:|");
        }
        table.append('\n').append(rule).append('\n');
        for (Command command : Command.values()) {
            table.append("| ").append(command.name());
            if (!command.isLoginRequired()) {
                table.append(" (public)");
            }
            table.append(" |");
            for (UserRole role : UserRole.values()) {
                table.append(isAllowed(role, command) ? " ✓ |" : "   |");
            }
            table.append('\n');
        }
        return table.toString();
    }

    /**
     * Prints the matrix, so it can be pasted into the report.
     *
     * @param args not used
     */
    public static void main(String[] args) {
        System.out.print(toMarkdown());
    }

    /**
     * Builds the matrix: which roles may send each command, grouped as public,
     * any staff member, coordinator, liaison, field work, oversight and
     * administrator.
     *
     * @return an unmodifiable map from each command to the roles allowed it
     */
    private static Map<Command, Set<UserRole>> buildRules() {
        Map<Command, Set<UserRole>> rules = new EnumMap<>(Command.class);
        Set<UserRole> coordinator = EnumSet.of(UserRole.COORDINATOR);
        Set<UserRole> liaison = EnumSet.of(UserRole.LIAISON);
        Set<UserRole> administrator = EnumSet.of(UserRole.ADMINISTRATOR);

        // Public: the server lets these through without a sign-in.
        allow(rules, EVERYONE, Command.PING, Command.LOGIN,
                Command.SUBMIT_REPORT, Command.SAVE_DRAFT,
                Command.LIST_PUBLIC_WARNINGS);

        // Any signed-in staff member: reading the shared picture.
        allow(rules, EVERYONE, Command.LOGOUT, Command.SUBSCRIBE_EVENTS,
                Command.LIST_REPORTS, Command.LIST_REPORTS_BY_STATUS,
                Command.GET_REPORT, Command.FIND_DUPLICATE_CANDIDATES,
                Command.REPORT_SIMILARITY, Command.LIST_OPEN_INCIDENTS,
                Command.GET_INCIDENT_TIMELINE, Command.GET_INCIDENT,
                Command.LIST_INCIDENTS, Command.CALCULATE_SCORE,
                Command.RECOMMEND_PRIORITY, Command.GET_LATEST_ASSESSMENT,
                Command.RECOMMEND_DEPARTMENTS,
                Command.LIST_ASSIGNMENTS_FOR_INCIDENT,
                Command.LIST_ASSIGNMENTS, Command.GET_ESCALATIONS,
                Command.EVALUATE_ESCALATIONS,
                Command.EVALUATE_INCIDENT_ESCALATION,
                Command.LIST_WARNINGS);

        // Coordinator: every decision about reports, incidents, priority,
        // tasking and public warnings.
        allow(rules, coordinator, Command.DECIDE_REPORT,
                Command.CREATE_INCIDENT_FROM_REPORT,
                Command.CHANGE_INCIDENT_STATUS, Command.CLOSE_INCIDENT,
                Command.SCORE_ASSESSMENT, Command.CONFIRM_PRIORITY,
                Command.CREATE_ASSIGNMENT, Command.RETRY_DELIVERY,
                Command.ISSUE_WARNING, Command.UPDATE_WARNING,
                Command.CANCEL_WARNING);

        // Department liaison: answers for their department.
        allow(rules, liaison, Command.RESPOND_TO_ASSIGNMENT,
                Command.ACK_WARNING);
        allow(rules, EnumSet.of(UserRole.COORDINATOR, UserRole.LIAISON),
                Command.ALLOCATE_RESOURCE);

        // Anyone working an incident can add to its timeline.
        allow(rules, EnumSet.of(UserRole.RESPONDER, UserRole.LIAISON,
                UserRole.COORDINATOR), Command.ADD_FIELD_UPDATE);

        // Oversight: the audit log and partner availability.
        allow(rules, EnumSet.of(UserRole.COORDINATOR,
                UserRole.ADMINISTRATOR), Command.SET_PARTNER_AVAILABLE,
                Command.LIST_AUDIT_EVENTS, Command.LIST_AUDIT_FOR_ENTITY);

        // Administrator: accounts and the Assessment 2 data import.
        allow(rules, administrator, Command.REGISTER_USER,
                Command.IMPORT_USER, Command.IMPORT_REPORT,
                Command.IMPORT_INCIDENT, Command.IMPORT_ASSESSMENT,
                Command.IMPORT_ASSIGNMENT, Command.IMPORT_FIELD_UPDATE,
                Command.IMPORT_AUDIT);
        return Collections.unmodifiableMap(rules);
    }

    /**
     * Gives a set of roles permission to send each of the given commands.
     *
     * @param rules    the matrix being built
     * @param roles    the roles allowed
     * @param commands the commands they may send
     * @throws IllegalStateException if a command already has a row, so no
     *                               command can be given two rules by mistake
     */
    private static void allow(Map<Command, Set<UserRole>> rules,
            Set<UserRole> roles, Command... commands) {
        for (Command command : commands) {
            if (rules.containsKey(command)) {
                throw new IllegalStateException(command
                        + " has two rows in the access matrix.");
            }
            rules.put(command,
                    Collections.unmodifiableSet(EnumSet.copyOf(roles)));
        }
    }

    /**
     * Describes the matrix.
     *
     * @return how many commands have a rule
     */
    @Override
    public String toString() {
        return "AccessMatrix[" + RULES.size() + " commands]";
    }
}
