package au.edu.cqu.drs.server;

import au.edu.cqu.drs.model.Assessment;
import au.edu.cqu.drs.model.AssignmentStatus;
import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.FieldUpdate;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.IncidentStatus;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.model.ReportStatus;
import au.edu.cqu.drs.model.ResourceAllocation;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.protocol.ArgumentKey;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.LoginResult;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.service.ErrorCode;
import au.edu.cqu.drs.service.ServiceResult;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The handlers for every command the core server serves, each calling one
 * of the existing services.
 *
 * <p>The services are used unchanged. Handlers only read the arguments, call
 * the service with the signed-in user, and wrap the outcome in a
 * {@link Response}. Lists are copied into {@link ArrayList}s, and users are
 * sent without their password salt and hash.</p>
 */
public class CoreCommands {

    private final ServerContext context;
    private final SessionManager sessions;
    private final EnumMap<Command, CommandHandler> handlers;

    /**
     * Creates the handlers.
     *
     * @param context  the services
     * @param sessions the session manager used by LOGIN and LOGOUT
     */
    public CoreCommands(ServerContext context, SessionManager sessions) {
        this.context = context;
        this.sessions = sessions;
        this.handlers = new EnumMap<>(Command.class);
        registerSessionCommands();
        registerReportCommands();
        registerIncidentCommands();
        registerAssessmentCommands();
        registerCoordinationCommands();
        registerOversightCommands();
    }

    /**
     * Returns every core handler.
     *
     * @return a read-only map of handlers by command
     */
    public Map<Command, CommandHandler> handlers() {
        return Collections.unmodifiableMap(this.handlers);
    }

    /**
     * Makes a copy of a user without the password salt and hash, for
     * sending to a client.
     *
     * @param user the user, may be null
     * @return the copy, or null
     */
    public static User publicCopy(User user) {
        if (user == null) {
            return null;
        }
        User copy = new User(user.getUserId(), user.getUsername(),
                user.getFullName(), user.getRole());
        copy.setDepartment(user.getDepartment());
        copy.setActive(user.isActive());
        copy.setLastLoginAt(user.getLastLoginAt());
        return copy;
    }

    /**
     * PING, LOGIN and LOGOUT.
     */
    private void registerSessionCommands() {
        this.handlers.put(Command.PING, (request, user) ->
                Response.success(request.getRequestId(), "pong"));
        this.handlers.put(Command.LOGIN, this::login);
        this.handlers.put(Command.LOGOUT, (request, user) ->
                Response.success(request.getRequestId(),
                        this.sessions.remove(request.getSessionToken())));
    }

    /**
     * Report and duplicate detection commands.
     */
    private void registerReportCommands() {
        this.handlers.put(Command.SUBMIT_REPORT, (request, user) ->
                result(request, this.context.getReportService().submitReport(
                        required(request, ArgumentKey.REPORT,
                                DisasterReport.class))));
        this.handlers.put(Command.SAVE_DRAFT, (request, user) ->
                result(request, this.context.getReportService().saveDraft(
                        required(request, ArgumentKey.REPORT,
                                DisasterReport.class))));
        this.handlers.put(Command.DECIDE_REPORT, (request, user) ->
                result(request, this.context.getReportService().decideReport(
                        user,
                        required(request, ArgumentKey.REPORT_ID,
                                String.class),
                        required(request, ArgumentKey.REPORT_STATUS,
                                ReportStatus.class),
                        request.get(ArgumentKey.REASON, String.class))));
        this.handlers.put(Command.LIST_REPORTS, (request, user) ->
                list(request, this.context.getReportService().findAll()));
        this.handlers.put(Command.LIST_REPORTS_BY_STATUS, (request, user) ->
                list(request, this.context.getReportService().findByStatus(
                        required(request, ArgumentKey.REPORT_STATUS,
                                ReportStatus.class))));
        this.handlers.put(Command.GET_REPORT, (request, user) ->
                found(request, this.context.getReportService().findById(
                        required(request, ArgumentKey.REPORT_ID,
                                String.class)), "report"));
        this.handlers.put(Command.FIND_DUPLICATE_CANDIDATES,
                (request, user) -> list(request,
                        this.context.getDuplicateDetectionService()
                                .findCandidates(report(request))));
        this.handlers.put(Command.REPORT_SIMILARITY, (request, user) ->
                Response.success(request.getRequestId(),
                        this.context.getDuplicateDetectionService()
                                .similarity(report(request),
                                        required(request,
                                                ArgumentKey.OTHER_REPORT,
                                                DisasterReport.class))));
    }

    /**
     * Incident lifecycle and field update commands.
     */
    private void registerIncidentCommands() {
        this.handlers.put(Command.CREATE_INCIDENT_FROM_REPORT,
                (request, user) -> result(request,
                        this.context.getIncidentService().createFromReport(
                                user, report(request))));
        this.handlers.put(Command.CHANGE_INCIDENT_STATUS, (request, user) ->
                result(request, this.context.getIncidentService()
                        .changeStatus(user, incidentId(request),
                                required(request,
                                        ArgumentKey.INCIDENT_STATUS,
                                        IncidentStatus.class))));
        this.handlers.put(Command.CLOSE_INCIDENT, (request, user) ->
                result(request, this.context.getIncidentService()
                        .closeIncident(user, incidentId(request),
                                request.get(ArgumentKey.OUTCOME,
                                        String.class),
                                request.get(ArgumentKey.RESIDUAL_RISKS,
                                        String.class))));
        this.handlers.put(Command.ADD_FIELD_UPDATE, (request, user) ->
                result(request, this.context.getIncidentService()
                        .addFieldUpdate(user, required(request,
                                ArgumentKey.FIELD_UPDATE,
                                FieldUpdate.class))));
        this.handlers.put(Command.LIST_OPEN_INCIDENTS, this::listOpen);
        this.handlers.put(Command.GET_INCIDENT_TIMELINE, (request, user) ->
                list(request, this.context.getIncidentService()
                        .findTimeline(incidentId(request))));
        this.handlers.put(Command.GET_INCIDENT, (request, user) ->
                found(request, this.context.getIncidentService()
                        .findById(incidentId(request)), "incident"));
        this.handlers.put(Command.LIST_INCIDENTS, (request, user) ->
                list(request, this.context.getIncidentService().findAll()));
    }

    /**
     * Severity scoring and priority commands.
     */
    private void registerAssessmentCommands() {
        this.handlers.put(Command.CALCULATE_SCORE, (request, user) ->
                Response.success(request.getRequestId(),
                        this.context.getAssessmentService().calculateScore(
                                required(request, ArgumentKey.THREAT_TO_LIFE,
                                        Integer.class),
                                required(request, ArgumentKey.SCALE,
                                        Integer.class),
                                required(request,
                                        ArgumentKey.INFRASTRUCTURE_IMPACT,
                                        Integer.class))));
        this.handlers.put(Command.RECOMMEND_PRIORITY, (request, user) ->
                Response.success(request.getRequestId(),
                        this.context.getAssessmentService()
                                .recommendPriority(required(request,
                                        ArgumentKey.SCORE, Integer.class))));
        this.handlers.put(Command.SCORE_ASSESSMENT, (request, user) ->
                result(request, this.context.getAssessmentService().score(
                        user, required(request, ArgumentKey.ASSESSMENT,
                                Assessment.class))));
        this.handlers.put(Command.CONFIRM_PRIORITY, (request, user) ->
                result(request, this.context.getAssessmentService()
                        .confirmPriority(user,
                                required(request, ArgumentKey.ASSESSMENT,
                                        Assessment.class),
                                required(request, ArgumentKey.PRIORITY,
                                        Priority.class),
                                request.get(ArgumentKey.REASON,
                                        String.class))));
        this.handlers.put(Command.GET_LATEST_ASSESSMENT, (request, user) ->
                found(request, this.context.getAssessmentService()
                        .findLatestForIncident(incidentId(request)),
                        "assessment"));
    }

    /**
     * Department assignment and resource commands.
     */
    private void registerCoordinationCommands() {
        this.handlers.put(Command.RECOMMEND_DEPARTMENTS, (request, user) ->
                list(request, this.context.getCoordinationService()
                        .recommendDepartments(required(request,
                                ArgumentKey.DISASTER_TYPE,
                                DisasterType.class))));
        this.handlers.put(Command.CREATE_ASSIGNMENT, (request, user) ->
                result(request, this.context.getCoordinationService()
                        .createAssignment(user, incidentId(request),
                                required(request, ArgumentKey.DEPARTMENT,
                                        Department.class),
                                request.get(ArgumentKey.REQUESTED_ACTION,
                                        String.class),
                                request.get(ArgumentKey.URGENCY,
                                        String.class))));
        this.handlers.put(Command.RETRY_DELIVERY, (request, user) ->
                result(request, this.context.getCoordinationService()
                        .retryDelivery(user, assignmentId(request))));
        this.handlers.put(Command.RESPOND_TO_ASSIGNMENT, (request, user) ->
                result(request, this.context.getCoordinationService()
                        .respondToAssignment(user, assignmentId(request),
                                required(request,
                                        ArgumentKey.ASSIGNMENT_RESPONSE,
                                        AssignmentStatus.class),
                                request.get(ArgumentKey.REASON,
                                        String.class),
                                request.get(ArgumentKey.ETA,
                                        LocalDateTime.class))));
        this.handlers.put(Command.ALLOCATE_RESOURCE, (request, user) ->
                result(request, this.context.getCoordinationService()
                        .allocateResource(user, required(request,
                                ArgumentKey.ALLOCATION,
                                ResourceAllocation.class))));
        this.handlers.put(Command.LIST_ASSIGNMENTS_FOR_INCIDENT,
                (request, user) -> list(request,
                        this.context.getCoordinationService()
                                .findByIncident(incidentId(request))));
        this.handlers.put(Command.LIST_ASSIGNMENTS, (request, user) ->
                list(request, this.context.getCoordinationService()
                        .findAll()));
        this.handlers.put(Command.SET_PARTNER_AVAILABLE, (request, user) -> {
            boolean available = required(request, ArgumentKey.AVAILABLE,
                    Boolean.class);
            this.context.getPartnerGateway().setAvailable(available);
            return Response.success(request.getRequestId(), available);
        });
    }

    /**
     * Escalation, account and audit commands.
     */
    private void registerOversightCommands() {
        this.handlers.put(Command.GET_ESCALATIONS, (request, user) ->
                list(request, this.context.getEscalationService()
                        .findAlerts(now())));
        this.handlers.put(Command.EVALUATE_ESCALATIONS, (request, user) ->
                list(request, this.context.getEscalationService()
                        .evaluateAllOpen(now())));
        this.handlers.put(Command.EVALUATE_INCIDENT_ESCALATION,
                this::evaluateIncident);
        this.handlers.put(Command.REGISTER_USER, this::register);
        this.handlers.put(Command.LIST_AUDIT_EVENTS, (request, user) ->
                list(request, this.context.getAuditService().findAll()));
        this.handlers.put(Command.LIST_AUDIT_FOR_ENTITY, (request, user) ->
                list(request, this.context.getAuditService().findForEntity(
                        required(request, ArgumentKey.ENTITY_TYPE,
                                String.class),
                        required(request, ArgumentKey.ENTITY_ID,
                                String.class))));
    }

    /**
     * Signs a user in and starts a session.
     *
     * @param request the request
     * @param user    always null; LOGIN is public
     * @return the token and user, or the authentication failure
     */
    private Response login(Request request, User user) {
        ServiceResult<User> result = this.context.getAuthenticationService()
                .login(request.get(ArgumentKey.USERNAME, String.class),
                        request.get(ArgumentKey.PASSWORD, String.class));
        if (result.isFailure()) {
            return Response.fromServiceResult(request.getRequestId(),
                    result);
        }
        String token = this.sessions.create(result.getValue());
        return Response.success(request.getRequestId(),
                new LoginResult(token, publicCopy(result.getValue())));
    }

    /**
     * Creates an account and returns it without password details.
     *
     * @param request the request
     * @param user    the administrator
     * @return the new user, or the failure
     */
    private Response register(Request request, User user) {
        ServiceResult<User> result = this.context.getAuthenticationService()
                .register(required(request, ArgumentKey.USERNAME,
                                String.class),
                        request.get(ArgumentKey.FULL_NAME, String.class),
                        required(request, ArgumentKey.ROLE, UserRole.class),
                        request.get(ArgumentKey.PASSWORD, String.class));
        if (result.isFailure()) {
            return Response.fromServiceResult(request.getRequestId(),
                    result);
        }
        return Response.success(request.getRequestId(),
                publicCopy(result.getValue()));
    }

    /**
     * Lists open incidents, for one priority if one is given.
     *
     * @param request the request
     * @param user    the signed-in user
     * @return the incidents
     */
    private Response listOpen(Request request, User user) {
        Priority priority = request.get(ArgumentKey.PRIORITY,
                Priority.class);
        if (priority == null) {
            return list(request, this.context.getIncidentService()
                    .findOpenIncidentsByPriority());
        }
        return list(request, this.context.getIncidentService()
                .findOpenIncidentsByPriority(priority));
    }

    /**
     * Evaluates the escalation state of one incident.
     *
     * @param request the request
     * @param user    the signed-in user
     * @return the alert, or NOT_FOUND
     */
    private Response evaluateIncident(Request request, User user) {
        Optional<Incident> incident = this.context.getIncidentService()
                .findById(incidentId(request));
        if (incident.isEmpty()) {
            return notFound(request, "incident");
        }
        return Response.success(request.getRequestId(),
                this.context.getEscalationService()
                        .evaluate(incident.get(), now()));
    }

    /**
     * Reads the report to act on: the stored report named by REPORT_ID, or
     * the report sent in REPORT.
     *
     * @param request the request
     * @return the report
     * @throws BadRequestException if neither is usable
     */
    private DisasterReport report(Request request) {
        String reportId = request.get(ArgumentKey.REPORT_ID, String.class);
        if (reportId == null) {
            return required(request, ArgumentKey.REPORT,
                    DisasterReport.class);
        }
        return this.context.getReportService().findById(reportId)
                .orElseThrow(() -> new BadRequestException(
                        ArgumentKey.REPORT_ID,
                        "No report has the reference " + reportId + "."));
    }

    /**
     * Reads the incident reference argument.
     *
     * @param request the request
     * @return the reference
     */
    private static String incidentId(Request request) {
        return required(request, ArgumentKey.INCIDENT_ID, String.class);
    }

    /**
     * Reads the assignment reference argument.
     *
     * @param request the request
     * @return the reference
     */
    private static String assignmentId(Request request) {
        return required(request, ArgumentKey.ASSIGNMENT_ID, String.class);
    }

    /**
     * Reads an argument that must be present.
     *
     * @param request the request
     * @param key     the argument name
     * @param type    the expected type
     * @param <T>     the expected type
     * @return the value
     * @throws BadRequestException if the argument is missing or null
     */
    private static <T> T required(Request request, String key,
                                  Class<T> type) {
        T value = request.get(key, type);
        if (value == null) {
            throw new BadRequestException(key, "The request is missing "
                    + key + ".");
        }
        return value;
    }

    /**
     * Returns the server's current time.
     *
     * @return now, by the server clock
     */
    private LocalDateTime now() {
        return LocalDateTime.now(this.context.getClock());
    }

    /**
     * Wraps a service result.
     *
     * @param request the request
     * @param result  the outcome
     * @return the response
     */
    private static Response result(Request request,
                                   ServiceResult<?> result) {
        return Response.fromServiceResult(request.getRequestId(), result);
    }

    /**
     * Wraps a list in a serialisable copy.
     *
     * @param request the request
     * @param values  the values
     * @return the response
     */
    private static Response list(Request request, List<?> values) {
        return Response.success(request.getRequestId(),
                new ArrayList<>(values));
    }

    /**
     * Wraps an optional value, or answers NOT_FOUND.
     *
     * @param request the request
     * @param value   the value found, if any
     * @param what    what was looked for, for the message
     * @return the response
     */
    private static Response found(Request request,
                                  Optional<? extends Serializable> value,
                                  String what) {
        if (value.isEmpty()) {
            return notFound(request, what);
        }
        return Response.success(request.getRequestId(), value.get());
    }

    /**
     * Answers NOT_FOUND.
     *
     * @param request the request
     * @param what    what was looked for
     * @return the response
     */
    private static Response notFound(Request request, String what) {
        return Response.failure(request.getRequestId(), ErrorCode.NOT_FOUND,
                "No " + what + " was found with that reference.");
    }

    /**
     * Describes the handlers.
     *
     * @return the number of commands served
     */
    @Override
    public String toString() {
        return "CoreCommands: " + this.handlers.size() + " command(s)";
    }
}
