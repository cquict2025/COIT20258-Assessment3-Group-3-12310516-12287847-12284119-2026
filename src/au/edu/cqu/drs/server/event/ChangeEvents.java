package au.edu.cqu.drs.server.event;

import au.edu.cqu.drs.model.Assessment;
import au.edu.cqu.drs.model.DepartmentAssignment;
import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.FieldUpdate;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.ResourceAllocation;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.warning.Warning;
import au.edu.cqu.drs.model.warning.WarningAcknowledgement;
import au.edu.cqu.drs.model.warning.WarningKeys;
import au.edu.cqu.drs.protocol.ArgumentKey;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

/**
 * Turns a successful state-changing command into the {@link ServerEvent}
 * that the dispatcher publishes.
 *
 * <p>Each command that changes shared state is listed with the kind of
 * entity it changes and a past-tense verb. Commands that only read, sign in
 * or out, save a member of the public's draft, or import old records are
 * not listed, so they publish nothing: an import of thousands of records
 * would otherwise flood every client's feed. The summary names the entity
 * and the staff member who acted, never a reporter's personal details.</p>
 */
public final class ChangeEvents {

    /** Entity type used for report events. */
    public static final String REPORT = "Report";
    /** Entity type used for incident events. */
    public static final String INCIDENT = "Incident";
    /** Entity type used for assignment events. */
    public static final String ASSIGNMENT = "Assignment";
    /** Entity type used for warning events. */
    public static final String WARNING = "Warning";

    /** Entity type and verb for each command that publishes an event. */
    private static final Map<Command, String[]> CHANGES = changes();

    /**
     * Prevents instances; every method is static.
     */
    private ChangeEvents() {
    }

    /**
     * Tells whether a command publishes an event when it succeeds.
     *
     * @param command the command
     * @return {@code true} if it changes state other clients should see
     */
    public static boolean isPublished(Command command) {
        return CHANGES.containsKey(command);
    }

    /**
     * Returns the commands that publish an event.
     *
     * @return a read-only set of commands
     */
    public static Set<Command> publishedCommands() {
        return Collections.unmodifiableSet(CHANGES.keySet());
    }

    /**
     * Describes a performed command as an event.
     *
     * @param request  the request
     * @param response the response; only a success publishes an event
     * @param user     the signed-in user, or null for the public
     * @param time     the server time
     * @return the event, or null if the command publishes nothing
     */
    public static ServerEvent describe(Request request, Response response,
                                       User user, LocalDateTime time) {
        if (request == null || response == null || !response.isSuccess()
                || !isPublished(request.getCommand())) {
            return null;
        }
        String[] change = CHANGES.get(request.getCommand());
        String entityType = change[0];
        String entityId = entityId(response.getValue(), request);
        StringBuilder summary = new StringBuilder(entityType);
        if (entityId != null) {
            summary.append(' ').append(entityId);
        }
        summary.append(' ').append(change[1]);
        String detail = detail(request, response.getValue());
        if (detail != null) {
            summary.append(' ').append(detail);
        }
        summary.append(" by ").append(actor(user));
        return ServerEvent.about(EventType.CHANGE, entityType, entityId,
                summary.toString(), time);
    }

    /**
     * Finds the reference of the changed entity, first in the answer and
     * then in the request.
     *
     * @param value   the response value
     * @param request the request
     * @return the reference, or null if there is none
     */
    private static String entityId(Serializable value, Request request) {
        if (value instanceof DisasterReport) {
            return ((DisasterReport) value).getReportId();
        }
        if (value instanceof Incident) {
            return ((Incident) value).getIncidentId();
        }
        if (value instanceof Assessment) {
            return ((Assessment) value).getIncidentId();
        }
        if (value instanceof FieldUpdate) {
            return ((FieldUpdate) value).getIncidentId();
        }
        if (value instanceof DepartmentAssignment) {
            return ((DepartmentAssignment) value).getAssignmentId();
        }
        if (value instanceof ResourceAllocation) {
            return ((ResourceAllocation) value).getAssignmentId();
        }
        if (value instanceof Warning) {
            return ((Warning) value).getWarningId();
        }
        if (value instanceof WarningAcknowledgement) {
            return ((WarningAcknowledgement) value).getWarningId();
        }
        if (value instanceof User) {
            return ((User) value).getUsername();
        }
        String[] keys = {ArgumentKey.ASSIGNMENT_ID, ArgumentKey.INCIDENT_ID,
            ArgumentKey.REPORT_ID, WarningKeys.WARNING_ID};
        for (String key : keys) {
            Object found = request.getPayload().get(key);
            if (found instanceof String) {
                return (String) found;
            }
        }
        return null;
    }

    /**
     * Adds the new status or priority where the answer gives it.
     *
     * @param request the request
     * @param value   the response value
     * @return the detail, or null
     */
    private static String detail(Request request, Serializable value) {
        Command command = request.getCommand();
        if (command == Command.DECIDE_REPORT
                && value instanceof DisasterReport) {
            return "as " + ((DisasterReport) value).getStatus()
                    .getDisplayName();
        }
        if (command == Command.CHANGE_INCIDENT_STATUS
                && value instanceof Incident) {
            return "to " + ((Incident) value).getStatus().getDisplayName();
        }
        if (command == Command.CONFIRM_PRIORITY
                && value instanceof Incident) {
            return "as " + ((Incident) value).getPriority()
                    .getDisplayName();
        }
        if (command == Command.RESPOND_TO_ASSIGNMENT
                && value instanceof DepartmentAssignment) {
            return "as " + ((DepartmentAssignment) value).getStatus()
                    .getDisplayName();
        }
        if (command == Command.SET_PARTNER_AVAILABLE) {
            if (Boolean.TRUE.equals(value)) {
                return "back online";
            }
            return "offline";
        }
        return null;
    }

    /**
     * Names who performed the command.
     *
     * @param user the signed-in user, or null
     * @return the user's name, or a member of the public
     */
    private static String actor(User user) {
        if (user == null) {
            return "a member of the public";
        }
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return user.getUsername();
    }

    /**
     * Lists each publishing command with its entity type and verb.
     *
     * @return the table
     */
    private static Map<Command, String[]> changes() {
        Map<Command, String[]> table = new EnumMap<>(Command.class);
        table.put(Command.SUBMIT_REPORT, new String[] {REPORT, "submitted"});
        table.put(Command.DECIDE_REPORT, new String[] {REPORT, "reviewed"});
        table.put(Command.CREATE_INCIDENT_FROM_REPORT,
                new String[] {INCIDENT, "created"});
        table.put(Command.CHANGE_INCIDENT_STATUS,
                new String[] {INCIDENT, "moved"});
        table.put(Command.CLOSE_INCIDENT, new String[] {INCIDENT, "closed"});
        table.put(Command.ADD_FIELD_UPDATE,
                new String[] {INCIDENT, "received a field update"});
        table.put(Command.SCORE_ASSESSMENT,
                new String[] {INCIDENT, "assessed"});
        table.put(Command.CONFIRM_PRIORITY,
                new String[] {INCIDENT, "priority confirmed"});
        table.put(Command.CREATE_ASSIGNMENT,
                new String[] {ASSIGNMENT, "created"});
        table.put(Command.RETRY_DELIVERY,
                new String[] {ASSIGNMENT, "sent again"});
        table.put(Command.RESPOND_TO_ASSIGNMENT,
                new String[] {ASSIGNMENT, "answered"});
        table.put(Command.ALLOCATE_RESOURCE,
                new String[] {ASSIGNMENT, "given a resource"});
        table.put(Command.SET_PARTNER_AVAILABLE,
                new String[] {"Partner interfaces", "switched"});
        table.put(Command.REGISTER_USER,
                new String[] {"User account", "created"});
        table.put(Command.ISSUE_WARNING, new String[] {WARNING, "issued"});
        table.put(Command.UPDATE_WARNING, new String[] {WARNING, "updated"});
        table.put(Command.CANCEL_WARNING,
                new String[] {WARNING, "cancelled"});
        table.put(Command.ACK_WARNING,
                new String[] {WARNING, "acknowledged"});
        return table;
    }

    /**
     * Describes this helper class.
     *
     * @return the number of commands that publish events
     */
    @Override
    public String toString() {
        return "ChangeEvents: " + CHANGES.size() + " publishing command(s)";
    }
}
