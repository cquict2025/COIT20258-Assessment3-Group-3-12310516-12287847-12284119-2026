package au.edu.cqu.drs.service;

import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.FieldUpdate;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.IncidentStatus;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.model.ReportStatus;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.repository.FieldUpdateRepository;
import au.edu.cqu.drs.repository.IncidentRepository;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Creation, lifecycle and timeline of incidents (UC-06, UC-10, UC-12, UC-13).
 * The dashboard ordering also lives here so that every screen presents the same
 * priority ordering (FR-11).
 */
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final FieldUpdateRepository fieldUpdateRepository;
    private final AuthenticationService authenticationService;
    private final AuditService auditService;

    public IncidentService(IncidentRepository incidentRepository,
                           FieldUpdateRepository fieldUpdateRepository,
                           AuthenticationService authenticationService,
                           AuditService auditService) {
        this.incidentRepository = incidentRepository;
        this.fieldUpdateRepository = fieldUpdateRepository;
        this.authenticationService = authenticationService;
        this.auditService = auditService;
    }

    /**
     * Creates an incident from a validated report (UC-06).
     *
     * @param coordinator the signed-in coordinator
     * @param report      a report that has already been validated
     * @return the created incident, or a typed failure
     */
    public ServiceResult<Incident> createFromReport(User coordinator,
                                                    DisasterReport report) {
        if (!this.authenticationService
                .isAuthorised(coordinator, UserRole.COORDINATOR)) {
            return ServiceResult.failure(ErrorCode.PERMISSION_DENIED,
                    "Only a coordinator can create an incident.");
        }
        if (report == null) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                                         "No report was supplied.");
        }
        if (report.getStatus() != ReportStatus.VALIDATED) {
            return ServiceResult.failure(ErrorCode.CONFLICT,
                    "Validate the report before creating an incident.");
        }

        Incident incident = new Incident(null,
                report.getTypeLabel() + " - " + report.getLocation()
                        .getSuburb(),
                report.getDisasterType(),
                report.getLocation(),
                LocalDateTime.now());
        incident.addSourceReportId(report.getReportId());
        Incident saved = this.incidentRepository.save(incident);
        this.auditService.record(coordinator.getUsername(), "CREATE_INCIDENT",
                            "Incident", 
                saved.getIncidentId(), "From report " + report.getReportId());
        return ServiceResult.success(saved);
    }

    /**
     * Moves an incident to a new lifecycle state, rejecting invalid
     * transitions.
     *
     * @param coordinator the signed-in coordinator
     * @param incidentId  the incident to change
     * @param target      the requested state
     * @return the updated incident, or a typed failure
     */
    public ServiceResult<Incident> changeStatus(User coordinator,
                                                String incidentId, 
                                                IncidentStatus target) {
        if (!this.authenticationService
                .isAuthorised(coordinator, UserRole.COORDINATOR)) {
            return ServiceResult.failure(ErrorCode.PERMISSION_DENIED,
                    "Only a coordinator can change an incident status.");
        }
        Optional<Incident> found = this.incidentRepository.findById(incidentId);
        if (found.isEmpty()) {
            return ServiceResult.failure(ErrorCode.NOT_FOUND,
                                         "That incident no longer exists.");
        }

        Incident incident = found.get();
        if (!incident.getStatus().canTransitionTo(target)) {
            return ServiceResult.failure(ErrorCode.CONFLICT,
                    String.format("An incident cannot move from %s to %s.",
                            incident.getStatus().getDisplayName(), target
                                    .getDisplayName()));
        }

        IncidentStatus previous = incident.getStatus();
        incident.setStatus(target);
        incident.incrementVersion();
        Incident saved = this.incidentRepository.save(incident);
        this.auditService.record(coordinator.getUsername(), "CHANGE_STATUS",
                            "Incident", 
                incidentId, previous.name() + " -> " + target.name());
        return ServiceResult.success(saved);
    }

    /**
     * Closes an incident, which UC-13 permits only when the outcome is recorded
     * and every assignment has been resolved.
     *
     * @param coordinator   the signed-in coordinator
     * @param incidentId    the incident to close
     * @param outcome       the closure outcome, mandatory
     * @param residualRisks any risks that remain after closure
     * @return the closed incident, or a typed failure
     */
    public ServiceResult<Incident> closeIncident(User coordinator,
                                                 String incidentId, 
                                                 String outcome, String
                                                         residualRisks) {
        if (!this.authenticationService
                .isAuthorised(coordinator, UserRole.COORDINATOR)) {
            return ServiceResult.failure(ErrorCode.PERMISSION_DENIED,
                    "Only a coordinator can close an incident.");
        }
        if (outcome == null || outcome.isBlank()) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Record the closure outcome.", "outcome");
        }
        Optional<Incident> found = this.incidentRepository.findById(incidentId);
        if (found.isEmpty()) {
            return ServiceResult.failure(ErrorCode.NOT_FOUND,
                                         "That incident no longer exists.");
        }

        Incident incident = found.get();
        if (incident.hasUnresolvedAssignments()) {
            return ServiceResult.failure(ErrorCode.CONFLICT,
                    "Resolve every departmental assignment before closing this "
                            + "incident.");
        }
        if (!incident.getStatus().canTransitionTo(IncidentStatus.CLOSED)) {
            return ServiceResult.failure(ErrorCode.CONFLICT,
                    "This incident is already " + incident.getStatus()
                            .getDisplayName() + ".");
        }

        incident.setStatus(IncidentStatus.CLOSED);
        incident.setClosureOutcome(outcome);
        incident.setResidualRisks(residualRisks);
        incident.setClosedAt(LocalDateTime.now());
        incident.incrementVersion();
        Incident saved = this.incidentRepository.save(incident);
        this.auditService.record(coordinator.getUsername(), "CLOSE_INCIDENT",
                            "Incident", 
                incidentId, outcome);
        return ServiceResult.success(saved);
    }

    /**
     * Adds an attributable entry to the incident timeline (UC-10, FR-10).
     * The version the responder saw is compared with the current version so
     * that
     * an update written against stale data is rejected rather than silently
     * overwriting a more recent change.
     *
     * @param author the signed-in responder or liaison
     * @param update the timeline entry
     * @return the saved update, or a typed failure
     */
    public ServiceResult<FieldUpdate> addFieldUpdate(User author,
            FieldUpdate update) {
        if (!this.authenticationService
                .isAuthorised(author, UserRole.RESPONDER, UserRole.LIAISON,
                UserRole.COORDINATOR)) {
            return ServiceResult.failure(ErrorCode.PERMISSION_DENIED,
                    "You do not have permission to add a field update.");
        }
        if (update == null || update.getProgressNote() == null
                || update.getProgressNote().isBlank()) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Enter a progress note.", "progressNote");
        }

        Optional<Incident> found =
                this.incidentRepository.findById(update.getIncidentId());
        if (found.isEmpty()) {
            return ServiceResult.failure(ErrorCode.NOT_FOUND,
                                         "That incident no longer exists.");
        }
        Incident incident = found.get();
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            return ServiceResult.failure(ErrorCode.CONFLICT,
                    "This incident is closed. Reopen it before adding "
                            + "updates.");
        }
        if (update.getIncidentVersionSeen() > 0
                && update.getIncidentVersionSeen() != incident.getVersion()) {
            return ServiceResult.failure(ErrorCode.CONFLICT,
                    "This incident changed while you were typing. Refresh and "
                            + "try again.");
        }

        update.setSubmittedBy(author.getUsername());
        update.setSubmittedAt(LocalDateTime.now());
        FieldUpdate saved = this.fieldUpdateRepository.save(update);

        if (incident.getFirstResponseAt() == null) {
            incident.setFirstResponseAt(saved.getSubmittedAt());
        }
        incident.addFieldUpdate(saved);
        incident.incrementVersion();
        this.incidentRepository.save(incident);

        this.auditService.record(author.getUsername(), "FIELD_UPDATE",
                "Incident",
                update.getIncidentId(), saved.getProgressNote());
        return ServiceResult.success(saved);
    }

    /**
     * Returns open incidents ordered by priority and then by age, which is the
     * ordering FR-11 requires of every dashboard.
     *
     * @return open incidents, most serious and oldest first
     */
    public List<Incident> findOpenIncidentsByPriority() {
        return this.incidentRepository.findAll().stream()
                .filter(i -> i.getStatus() != IncidentStatus.CLOSED)
                .sorted(Comparator
                        .comparingInt((Incident i) -> i.getPriority().getRank())
                        .thenComparing(Incident::getCreatedAt))
                .toList();
    }

    /**
     * Filters the prioritised dashboard by priority, as the dashboard screen
     * offers.
     */
    public List<Incident> findOpenIncidentsByPriority(Priority priority) {
        return findOpenIncidentsByPriority().stream()
                .filter(i -> i.getPriority() == priority)
                .toList();
    }

    public List<FieldUpdate> findTimeline(String incidentId) {
        return this.fieldUpdateRepository.findByIncidentId(incidentId);
    }

    public Optional<Incident> findById(String incidentId) {
        return this.incidentRepository.findById(incidentId);
    }

    public List<Incident> findAll() {
        return this.incidentRepository.findAll();
    }
}
