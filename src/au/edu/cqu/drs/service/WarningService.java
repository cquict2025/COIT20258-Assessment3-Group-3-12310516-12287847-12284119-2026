package au.edu.cqu.drs.service;

import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.IncidentStatus;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.model.warning.EvacuationCentre;
import au.edu.cqu.drs.model.warning.Warning;
import au.edu.cqu.drs.model.warning.WarningAcknowledgement;
import au.edu.cqu.drs.model.warning.WarningLevel;
import au.edu.cqu.drs.model.warning.WarningStatus;
import au.edu.cqu.drs.repository.IncidentRepository;
import au.edu.cqu.drs.repository.WarningRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Feature 1: issuing, updating and cancelling public warnings, and their
 * acknowledgement by department liaisons.
 *
 * <p>The rules:</p>
 * <ul>
 *   <li>only a coordinator may issue, update or cancel a warning;</li>
 *   <li>a warning must be about an incident that exists and is not
 *       closed;</li>
 *   <li>an Emergency Warning is allowed only for a P1 or P2 incident;</li>
 *   <li>the expiry time must be in the future;</li>
 *   <li>an evacuation centre needs a name and a capacity of at least 0;</li>
 *   <li>every change is stored as a new version, with an audit record, so
 *       the history shows what the public was told and when;</li>
 *   <li>a cancelled warning cannot be changed again;</li>
 *   <li>only a department liaison may acknowledge, once per department,
 *       and only a warning that is in force.</li>
 * </ul>
 */
public class WarningService {

    /** Longest affected-area text, matching the database column. */
    public static final int MAX_AREA_LENGTH = 200;

    private static final Set<Priority> EMERGENCY_PRIORITIES =
            EnumSet.of(Priority.P1, Priority.P2);

    private final WarningRepository warningRepository;
    private final IncidentRepository incidentRepository;
    private final AuthenticationService authenticationService;
    private final AuditService auditService;
    private final Clock clock;

    /**
     * Creates the service.
     *
     * @param warningRepository     where warnings are stored
     * @param incidentRepository    used to check the incident and its
     *                              priority
     * @param authenticationService used for role checks
     * @param auditService          records every change
     * @param clock                 the server clock, so tests can fix the
     *                              time
     */
    public WarningService(WarningRepository warningRepository,
            IncidentRepository incidentRepository,
            AuthenticationService authenticationService,
            AuditService auditService, Clock clock) {
        this.warningRepository = warningRepository;
        this.incidentRepository = incidentRepository;
        this.authenticationService = authenticationService;
        this.auditService = auditService;
        this.clock = clock;
    }

    /**
     * Issues a new warning as version 1.
     *
     * @param coordinator the signed-in coordinator
     * @param draft       the warning's content
     * @return the issued warning, or a typed failure naming the field at
     *         fault
     */
    public ServiceResult<Warning> issue(User coordinator, Warning draft) {
        if (!isCoordinator(coordinator)) {
            return denied("issue");
        }
        if (draft == null) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "No warning was supplied.");
        }
        ServiceResult<Warning> invalid = validate(draft);
        if (invalid != null) {
            return invalid;
        }
        Warning issued = draft.copy();
        issued.setWarningId(null);
        issued.setVersion(1);
        issued.setStatus(WarningStatus.ACTIVE);
        return store(coordinator, issued, "ISSUE_WARNING",
                issued.getLevel().getDisplayName() + " for "
                        + issued.getAffectedArea());
    }

    /**
     * Stores a changed warning as its next version.
     *
     * @param coordinator the signed-in coordinator
     * @param changed     the new content, with the warning's code set
     * @return the new version, or a typed failure
     */
    public ServiceResult<Warning> update(User coordinator, Warning changed) {
        if (!isCoordinator(coordinator)) {
            return denied("update");
        }
        if (changed == null || changed.getWarningId() == null) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Choose the warning to update.");
        }
        ServiceResult<Warning> current = currentActive(
                changed.getWarningId(), "updated");
        if (current.isFailure()) {
            return current;
        }
        Warning next = changed.copy();
        next.setIncidentId(current.getValue().getIncidentId());
        ServiceResult<Warning> invalid = validate(next);
        if (invalid != null) {
            return invalid;
        }
        next.setVersion(current.getValue().getVersion() + 1);
        next.setStatus(WarningStatus.ACTIVE);
        return store(coordinator, next, "UPDATE_WARNING",
                "Version " + next.getVersion() + ": "
                        + next.getLevel().getDisplayName());
    }

    /**
     * Cancels a warning by storing a cancelled version.
     *
     * @param coordinator the signed-in coordinator
     * @param warningId   the warning's code
     * @param reason      why it is cancelled, kept in the audit log
     * @return the cancelled version, or a typed failure
     */
    public ServiceResult<Warning> cancel(User coordinator, String warningId,
            String reason) {
        if (!isCoordinator(coordinator)) {
            return denied("cancel");
        }
        if (reason == null || reason.isBlank()) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Give a reason for cancelling the warning.", "reason");
        }
        ServiceResult<Warning> current =
                currentActive(warningId, "cancelled");
        if (current.isFailure()) {
            return current;
        }
        Warning next = current.getValue().copy();
        next.setVersion(next.getVersion() + 1);
        next.setStatus(WarningStatus.CANCELLED);
        return store(coordinator, next, "CANCEL_WARNING", reason.trim());
    }

    /**
     * Records that the liaison's department has seen a warning.
     *
     * @param liaison   the signed-in department liaison
     * @param warningId the warning's code
     * @return the acknowledgement, or a typed failure
     */
    public ServiceResult<WarningAcknowledgement> acknowledge(User liaison,
            String warningId) {
        if (!this.authenticationService.isAuthorised(liaison,
                UserRole.LIAISON)) {
            return ServiceResult.failure(ErrorCode.PERMISSION_DENIED,
                    "Only a department liaison can acknowledge a warning.");
        }
        if (liaison.getDepartment() == null) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Your account has no department. Ask an administrator "
                            + "to set one.");
        }
        Optional<Warning> found = latest(warningId);
        if (found.isEmpty()) {
            return notFound(warningId);
        }
        if (!found.get().isInForceAt(now())) {
            return ServiceResult.failure(ErrorCode.CONFLICT,
                    warningId + " is no longer in force.");
        }
        boolean already = this.warningRepository
                .findAcknowledgements(warningId).stream()
                .anyMatch(a -> a.getDepartment() == liaison.getDepartment());
        if (already) {
            return ServiceResult.failure(ErrorCode.CONFLICT,
                    liaison.getDepartment().getDisplayName()
                            + " has already acknowledged " + warningId + ".");
        }
        WarningAcknowledgement acknowledgement = new WarningAcknowledgement(
                warningId, liaison.getDepartment(), liaison.getUsername(),
                now());
        try {
            this.warningRepository.saveAcknowledgement(acknowledgement);
        } catch (IllegalStateException e) {
            return ServiceResult.failure(ErrorCode.CONFLICT, e.getMessage());
        }
        this.auditService.record(liaison.getUsername(), "ACK_WARNING",
                "Warning", warningId,
                liaison.getDepartment().getDisplayName());
        return ServiceResult.success(acknowledgement);
    }

    /**
     * Lists the newest version of every warning for staff, each with its
     * acknowledgements.
     *
     * @return the warnings, most urgent and most recent first
     */
    public ArrayList<Warning> listForStaff() {
        ArrayList<Warning> warnings =
                new ArrayList<>(this.warningRepository.findLatestVersions());
        for (Warning warning : warnings) {
            warning.setAcknowledgements(this.warningRepository
                    .findAcknowledgements(warning.getWarningId()));
        }
        warnings.sort(byUrgency());
        return warnings;
    }

    /**
     * Lists the warnings in force for the public board. The issuer and the
     * acknowledgements are left out.
     *
     * @return the warnings in force, most urgent first
     */
    public ArrayList<Warning> listPublic() {
        LocalDateTime now = now();
        ArrayList<Warning> warnings = new ArrayList<>();
        for (Warning warning : this.warningRepository.findLatestVersions()) {
            if (warning.isInForceAt(now)) {
                warning.setIssuedBy(null);
                warning.setAcknowledgements(null);
                warnings.add(warning);
            }
        }
        warnings.sort(byUrgency());
        return warnings;
    }

    /**
     * Returns every version of a warning.
     *
     * @param warningId the warning's code
     * @return the versions, oldest first, or NOT_FOUND
     */
    public ServiceResult<ArrayList<Warning>> history(String warningId) {
        List<Warning> versions = warningId == null ? List.of()
                : this.warningRepository.findHistory(warningId);
        if (versions.isEmpty()) {
            return notFound(warningId);
        }
        return ServiceResult.success(new ArrayList<>(versions));
    }

    /**
     * Lists the departments that have not yet acknowledged a warning.
     *
     * @param warning a warning with its acknowledgements filled in
     * @return the departments still to acknowledge
     */
    public static List<Department> pendingDepartments(Warning warning) {
        Set<Department> pending = EnumSet.allOf(Department.class);
        for (WarningAcknowledgement ack : warning.getAcknowledgements()) {
            pending.remove(ack.getDepartment());
        }
        return new ArrayList<>(pending);
    }

    /**
     * Describes the service.
     *
     * @return the storage in use
     */
    @Override
    public String toString() {
        return "WarningService: " + this.warningRepository;
    }

    private ServiceResult<Warning> store(User coordinator, Warning warning,
            String action, String detail) {
        warning.setIssuedBy(coordinator.getUsername());
        warning.setIssuedAt(now());
        try {
            this.warningRepository.saveVersion(warning);
        } catch (IllegalStateException e) {
            return ServiceResult.failure(ErrorCode.CONFLICT,
                    "Someone else changed this warning first. Reload it and "
                            + "try again.");
        }
        this.auditService.record(coordinator.getUsername(), action,
                "Warning", warning.getWarningId(), detail);
        return ServiceResult.success(warning);
    }

    /**
     * Checks the content of a warning. Returns null when it is valid.
     */
    private ServiceResult<Warning> validate(Warning warning) {
        if (warning.getIncidentId() == null
                || warning.getIncidentId().isBlank()) {
            return invalid("Choose the incident the warning is about.",
                    "incidentId");
        }
        Optional<Incident> incident =
                this.incidentRepository.findById(warning.getIncidentId());
        if (incident.isEmpty()) {
            return ServiceResult.failure(ErrorCode.NOT_FOUND,
                    "Incident " + warning.getIncidentId()
                            + " does not exist.", "incidentId");
        }
        if (incident.get().getStatus() == IncidentStatus.CLOSED) {
            return ServiceResult.failure(ErrorCode.CONFLICT,
                    "Incident " + warning.getIncidentId()
                            + " is closed.", "incidentId");
        }
        if (warning.getLevel() == null) {
            return invalid("Choose a warning level.", "level");
        }
        if (warning.getLevel() == WarningLevel.EMERGENCY_WARNING
                && !EMERGENCY_PRIORITIES.contains(
                        incident.get().getPriority())) {
            return invalid("An Emergency Warning needs a P1 or P2 incident. "
                    + warning.getIncidentId() + " is "
                    + incident.get().getPriority() + ".", "level");
        }
        if (isBlank(warning.getAffectedArea())) {
            return invalid("Enter the affected area.", "affectedArea");
        }
        if (warning.getAffectedArea().length() > MAX_AREA_LENGTH) {
            return invalid("Keep the affected area to " + MAX_AREA_LENGTH
                    + " characters.", "affectedArea");
        }
        if (isBlank(warning.getMessage())) {
            return invalid("Enter the warning message.", "message");
        }
        if (warning.getExpiresAt() == null) {
            return invalid("Enter when the warning expires.", "expiresAt");
        }
        if (!warning.getExpiresAt().isAfter(now())) {
            return invalid("The expiry time must be in the future.",
                    "expiresAt");
        }
        EvacuationCentre centre = warning.getEvacuationCentre();
        if (centre != null) {
            if (isBlank(centre.getName())) {
                return invalid("Name the evacuation centre, or leave both "
                        + "centre fields empty.", "evacuationCentre");
            }
            if (centre.getCapacity() < 0) {
                return invalid("Capacity cannot be negative.",
                        "evacuationCapacity");
            }
        }
        return null;
    }

    private ServiceResult<Warning> currentActive(String warningId,
            String change) {
        Optional<Warning> found = latest(warningId);
        if (found.isEmpty()) {
            return notFound(warningId);
        }
        if (found.get().getStatus() == WarningStatus.CANCELLED) {
            return ServiceResult.failure(ErrorCode.CONFLICT, warningId
                    + " was cancelled and cannot be " + change + ".");
        }
        return ServiceResult.success(found.get());
    }

    private Optional<Warning> latest(String warningId) {
        return warningId == null ? Optional.empty()
                : this.warningRepository.findLatest(warningId);
    }

    private boolean isCoordinator(User user) {
        return this.authenticationService.isAuthorised(user,
                UserRole.COORDINATOR);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(this.clock);
    }

    private static Comparator<Warning> byUrgency() {
        return Comparator.comparingInt(
                (Warning w) -> w.getStatus() == WarningStatus.ACTIVE ? 0 : 1)
                .thenComparingInt(w -> w.getLevel().getUrgencyRank())
                .thenComparing(Warning::getIssuedAt,
                        Comparator.nullsLast(Comparator.reverseOrder()));
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private static <T> ServiceResult<T> invalid(String message,
            String field) {
        return ServiceResult.failure(ErrorCode.VALIDATION_ERROR, message,
                field);
    }

    private static <T> ServiceResult<T> denied(String action) {
        return ServiceResult.failure(ErrorCode.PERMISSION_DENIED,
                "Only a coordinator can " + action + " a warning.");
    }

    private static <T> ServiceResult<T> notFound(String warningId) {
        return ServiceResult.failure(ErrorCode.NOT_FOUND,
                "Warning " + warningId + " does not exist.");
    }
}
