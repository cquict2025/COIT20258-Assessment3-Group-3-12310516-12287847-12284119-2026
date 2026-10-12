package au.edu.cqu.drs.client;

import au.edu.cqu.drs.model.Assessment;
import au.edu.cqu.drs.model.AssignmentStatus;
import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.DepartmentAssignment;
import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.DuplicateCandidate;
import au.edu.cqu.drs.model.EscalationAlert;
import au.edu.cqu.drs.model.FieldUpdate;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.model.ReportStatus;
import au.edu.cqu.drs.model.ResourceAllocation;
import au.edu.cqu.drs.protocol.ArgumentKey;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.service.ErrorCode;
import au.edu.cqu.drs.service.ServiceResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The services as the client sees them: each method sends one
 * {@link Request} to the server and returns the answer as a
 * {@link ServiceResult}, the same type the controllers used when the
 * services ran in-process (Proxy pattern).
 *
 * <p>The signed-in user is never passed: the server works it out from the
 * session token. Methods block until the server answers, so controllers call
 * them through {@link BackgroundCall}, never on the JavaFX thread.</p>
 */
public class RemoteServices {

    private final ClientSession session;

    /**
     * Creates the proxy.
     *
     * @param session the signed-in session and its connection
     */
    public RemoteServices(ClientSession session) {
        this.session = session;
    }

    /**
     * Returns the session.
     *
     * @return the session
     */
    public ClientSession getSession() {
        return this.session;
    }

    /**
     * Submits a report.
     *
     * @param report the report
     * @return the saved report
     */
    public ServiceResult<DisasterReport> submitReport(DisasterReport report) {
        return call(request(Command.SUBMIT_REPORT)
                .put(ArgumentKey.REPORT, report), DisasterReport.class);
    }

    /**
     * Saves a draft report.
     *
     * @param report the report
     * @return the saved draft
     */
    public ServiceResult<DisasterReport> saveDraft(DisasterReport report) {
        return call(request(Command.SAVE_DRAFT)
                .put(ArgumentKey.REPORT, report), DisasterReport.class);
    }

    /**
     * Lists the reports with one status.
     *
     * @param status the status
     * @return the reports
     */
    public ServiceResult<List<DisasterReport>> findReportsByStatus(
            ReportStatus status) {
        return list(request(Command.LIST_REPORTS_BY_STATUS)
                .put(ArgumentKey.REPORT_STATUS, status),
                DisasterReport.class);
    }

    /**
     * Validates, rejects or links a report.
     *
     * @param reportId the report reference
     * @param decision the new status
     * @param reason   the reason
     * @return the updated report
     */
    public ServiceResult<DisasterReport> decideReport(String reportId,
            ReportStatus decision, String reason) {
        return call(request(Command.DECIDE_REPORT)
                .put(ArgumentKey.REPORT_ID, reportId)
                .put(ArgumentKey.REPORT_STATUS, decision)
                .put(ArgumentKey.REASON, reason), DisasterReport.class);
    }

    /**
     * Finds reports that may describe the same event as a saved report.
     *
     * @param report the saved report
     * @return the candidates
     */
    public ServiceResult<List<DuplicateCandidate>> findDuplicateCandidates(
            DisasterReport report) {
        return list(request(Command.FIND_DUPLICATE_CANDIDATES)
                .put(ArgumentKey.REPORT_ID, report.getReportId()),
                DuplicateCandidate.class);
    }

    /**
     * Lists every open incident by priority.
     *
     * @return the incidents
     */
    public ServiceResult<List<Incident>> findOpenIncidents() {
        return list(request(Command.LIST_OPEN_INCIDENTS), Incident.class);
    }

    /**
     * Lists the open incidents with one priority.
     *
     * @param priority the priority
     * @return the incidents
     */
    public ServiceResult<List<Incident>> findOpenIncidents(
            Priority priority) {
        return list(request(Command.LIST_OPEN_INCIDENTS)
                .put(ArgumentKey.PRIORITY, priority), Incident.class);
    }

    /**
     * Creates an incident from a validated report.
     *
     * @param reportId the report reference
     * @return the incident
     */
    public ServiceResult<Incident> createIncidentFromReport(String reportId) {
        return call(request(Command.CREATE_INCIDENT_FROM_REPORT)
                .put(ArgumentKey.REPORT_ID, reportId), Incident.class);
    }

    /**
     * Records a field update.
     *
     * @param update the update
     * @return the saved update
     */
    public ServiceResult<FieldUpdate> addFieldUpdate(FieldUpdate update) {
        return call(request(Command.ADD_FIELD_UPDATE)
                .put(ArgumentKey.FIELD_UPDATE, update), FieldUpdate.class);
    }

    /**
     * Lists the field updates of an incident.
     *
     * @param incidentId the incident reference
     * @return the updates
     */
    public ServiceResult<List<FieldUpdate>> findTimeline(String incidentId) {
        return list(request(Command.GET_INCIDENT_TIMELINE)
                .put(ArgumentKey.INCIDENT_ID, incidentId), FieldUpdate.class);
    }

    /**
     * Scores an assessment.
     *
     * @param assessment the factor values
     * @return the scored assessment
     */
    public ServiceResult<Assessment> scoreAssessment(Assessment assessment) {
        return call(request(Command.SCORE_ASSESSMENT)
                .put(ArgumentKey.ASSESSMENT, assessment), Assessment.class);
    }

    /**
     * Confirms or overrides a recommended priority.
     *
     * @param assessment the scored assessment
     * @param priority   the confirmed priority
     * @param reason     the override reason, if any
     * @return the confirmed assessment
     */
    public ServiceResult<Assessment> confirmPriority(Assessment assessment,
            Priority priority, String reason) {
        return call(request(Command.CONFIRM_PRIORITY)
                .put(ArgumentKey.ASSESSMENT, assessment)
                .put(ArgumentKey.PRIORITY, priority)
                .put(ArgumentKey.REASON, reason), Assessment.class);
    }

    /**
     * Lists incidents at risk of, or past, their target response time.
     *
     * @return the alerts
     */
    public ServiceResult<List<EscalationAlert>> findAlerts() {
        return list(request(Command.GET_ESCALATIONS), EscalationAlert.class);
    }

    /**
     * Evaluates the escalation state of every open incident.
     *
     * @return one alert per open incident
     */
    public ServiceResult<List<EscalationAlert>> evaluateEscalations() {
        return list(request(Command.EVALUATE_ESCALATIONS),
                EscalationAlert.class);
    }

    /**
     * Recommends departments for a disaster type.
     *
     * @param type the disaster type
     * @return the departments
     */
    public ServiceResult<List<Department>> recommendDepartments(
            DisasterType type) {
        return list(request(Command.RECOMMEND_DEPARTMENTS)
                .put(ArgumentKey.DISASTER_TYPE, type), Department.class);
    }

    /**
     * Assigns an incident to a department.
     *
     * @param incidentId the incident reference
     * @param department the department
     * @param action     the action requested
     * @param urgency    the urgency
     * @return the assignment
     */
    public ServiceResult<DepartmentAssignment> createAssignment(
            String incidentId, Department department, String action,
            String urgency) {
        return call(request(Command.CREATE_ASSIGNMENT)
                .put(ArgumentKey.INCIDENT_ID, incidentId)
                .put(ArgumentKey.DEPARTMENT, department)
                .put(ArgumentKey.REQUESTED_ACTION, action)
                .put(ArgumentKey.URGENCY, urgency),
                DepartmentAssignment.class);
    }

    /**
     * Sends an undelivered assignment again.
     *
     * @param assignmentId the assignment reference
     * @return the assignment
     */
    public ServiceResult<DepartmentAssignment> retryDelivery(
            String assignmentId) {
        return call(request(Command.RETRY_DELIVERY)
                .put(ArgumentKey.ASSIGNMENT_ID, assignmentId),
                DepartmentAssignment.class);
    }

    /**
     * Records a liaison's answer to an assignment.
     *
     * @param assignmentId the assignment reference
     * @param response     accepted, rejected or clarification requested
     * @param reason       the reason
     * @param eta          the estimated arrival, if accepted
     * @return the assignment
     */
    public ServiceResult<DepartmentAssignment> respondToAssignment(
            String assignmentId, AssignmentStatus response, String reason,
            LocalDateTime eta) {
        return call(request(Command.RESPOND_TO_ASSIGNMENT)
                .put(ArgumentKey.ASSIGNMENT_ID, assignmentId)
                .put(ArgumentKey.ASSIGNMENT_RESPONSE, response)
                .put(ArgumentKey.REASON, reason)
                .put(ArgumentKey.ETA, eta), DepartmentAssignment.class);
    }

    /**
     * Allocates a resource to an assignment.
     *
     * @param allocation the allocation
     * @return the saved allocation
     */
    public ServiceResult<ResourceAllocation> allocateResource(
            ResourceAllocation allocation) {
        return call(request(Command.ALLOCATE_RESOURCE)
                .put(ArgumentKey.ALLOCATION, allocation),
                ResourceAllocation.class);
    }

    /**
     * Lists the assignments of an incident.
     *
     * @param incidentId the incident reference
     * @return the assignments
     */
    public ServiceResult<List<DepartmentAssignment>> findAssignments(
            String incidentId) {
        return list(request(Command.LIST_ASSIGNMENTS_FOR_INCIDENT)
                .put(ArgumentKey.INCIDENT_ID, incidentId),
                DepartmentAssignment.class);
    }

    /**
     * Simulates the partner interfaces going down or coming back.
     *
     * @param available whether the partners are reachable
     * @return the new availability
     */
    public ServiceResult<Boolean> setPartnerAvailable(boolean available) {
        return call(request(Command.SET_PARTNER_AVAILABLE)
                .put(ArgumentKey.AVAILABLE, available), Boolean.class);
    }

    /**
     * Creates a request carrying the session token.
     *
     * @param command the command
     * @return the request
     */
    private Request request(Command command) {
        return this.session.newRequest(command);
    }

    /**
     * Sends a request expecting one value.
     *
     * @param request the request
     * @param type    the value type
     * @param <T>     the value type
     * @return the value or the failure
     */
    private <T> ServiceResult<T> call(Request request, Class<T> type) {
        Response response = this.session.send(request);
        try {
            return response.toServiceResult(type);
        } catch (IllegalArgumentException e) {
            return unexpected();
        }
    }

    /**
     * Sends a request expecting a list.
     *
     * @param request the request
     * @param type    the element type
     * @param <T>     the element type
     * @return the list or the failure
     */
    private <T> ServiceResult<List<T>> list(Request request, Class<T> type) {
        Response response = this.session.send(request);
        if (!response.isSuccess()) {
            return ServiceResult.failure(response.getErrorCode(),
                    response.getMessage(), response.getAffectedField());
        }
        if (!(response.getValue() instanceof List<?> values)) {
            return unexpected();
        }
        List<T> typed = new ArrayList<>();
        for (Object value : values) {
            if (!type.isInstance(value)) {
                return unexpected();
            }
            typed.add(type.cast(value));
        }
        return ServiceResult.success(typed);
    }

    /**
     * The failure returned when the server's answer has the wrong shape.
     *
     * @param <T> the expected type
     * @return the failure
     */
    private static <T> ServiceResult<T> unexpected() {
        return ServiceResult.failure(ErrorCode.ADAPTER_UNAVAILABLE,
                "The server sent an answer this client does not understand."
                        + " Check that client and server are the same "
                        + "version.");
    }

    /**
     * Describes the proxy.
     *
     * @return the session it uses
     */
    @Override
    public String toString() {
        return "RemoteServices for " + this.session;
    }
}
