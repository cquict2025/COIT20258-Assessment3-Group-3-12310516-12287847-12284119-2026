package au.edu.cqu.drs.service;

import au.edu.cqu.drs.model.AuditEvent;
import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.IncidentStatus;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.model.warning.EvacuationCentre;
import au.edu.cqu.drs.model.warning.Warning;
import au.edu.cqu.drs.model.warning.WarningAcknowledgement;
import au.edu.cqu.drs.model.warning.WarningLevel;
import au.edu.cqu.drs.model.warning.WarningStatus;
import au.edu.cqu.drs.repository.memory.InMemoryAuditRepository;
import au.edu.cqu.drs.repository.memory.InMemoryFieldUpdateRepository;
import au.edu.cqu.drs.repository.memory.InMemoryIncidentRepository;
import au.edu.cqu.drs.repository.memory.InMemoryUserRepository;
import au.edu.cqu.drs.repository.memory.InMemoryWarningRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for every rule of Feature 1, public warnings. */
class WarningServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Australia/Melbourne");
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 10, 13, 10, 0);

    private InMemoryIncidentRepository incidents;
    private IncidentService incidentService;
    private InMemoryWarningRepository warnings;
    private InMemoryAuditRepository audit;
    private WarningService service;
    private User coordinator;
    private User liaison;
    private Incident p1Incident;
    private Incident p3Incident;

    @BeforeEach
    void setUp() {
        this.incidents = new InMemoryIncidentRepository();
        this.warnings = new InMemoryWarningRepository();
        this.audit = new InMemoryAuditRepository();
        AuditService auditService = new AuditService(this.audit);
        AuthenticationService authentication = new AuthenticationService(
                new InMemoryUserRepository(), auditService);
        Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        this.incidentService = new IncidentService(this.incidents,
                new InMemoryFieldUpdateRepository(), authentication,
                auditService);
        this.service = new WarningService(this.warnings,
                this.incidentService, authentication, auditService, clock);
        this.coordinator = new User("USR-001", "coordinator",
                "Alex Coordinator", UserRole.COORDINATOR);
        this.liaison = new User("USR-002", "liaison", "Sam Liaison",
                UserRole.LIAISON);
        this.liaison.setDepartment(Department.SCHOOLS);
        this.p1Incident = incident(Priority.P1);
        this.p3Incident = incident(Priority.P3);
    }

    @Test
    @DisplayName("TC-WS-01 a coordinator issues a warning as version 1")
    void coordinatorIssuesWarning() {
        ServiceResult<Warning> result =
                this.service.issue(this.coordinator, draft(this.p1Incident));

        assertTrue(result.isSuccess(), result.getMessage());
        Warning issued = result.getValue();
        assertEquals("WRN-0001", issued.getWarningId());
        assertEquals(1, issued.getVersion());
        assertEquals(WarningStatus.ACTIVE, issued.getStatus());
        assertEquals("coordinator", issued.getIssuedBy());
        assertEquals(NOW, issued.getIssuedAt());
    }

    @Test
    @DisplayName("TC-WS-02 a liaison or responder cannot issue a warning")
    void onlyCoordinatorIssues() {
        User responder = new User("USR-003", "responder", "Jo",
                UserRole.RESPONDER);

        assertEquals(ErrorCode.PERMISSION_DENIED, this.service
                .issue(this.liaison, draft(this.p1Incident)).getErrorCode());
        assertEquals(ErrorCode.PERMISSION_DENIED, this.service
                .issue(responder, draft(this.p1Incident)).getErrorCode());
        assertEquals(ErrorCode.PERMISSION_DENIED, this.service
                .issue(null, draft(this.p1Incident)).getErrorCode());
        assertTrue(this.warnings.findLatestVersions().isEmpty());
    }

    @Test
    @DisplayName("TC-WS-03 an Emergency Warning is refused for a P3 incident")
    void emergencyNeedsP1OrP2() {
        Warning draft = draft(this.p3Incident);
        draft.setLevel(WarningLevel.EMERGENCY_WARNING);

        ServiceResult<Warning> result =
                this.service.issue(this.coordinator, draft);

        assertEquals(ErrorCode.VALIDATION_ERROR, result.getErrorCode());
        assertEquals("level", result.getAffectedField());
    }

    @Test
    @DisplayName("TC-WS-04 an Emergency Warning is allowed for P1 and P2")
    void emergencyAllowedForP1AndP2() {
        Warning forP1 = draft(this.p1Incident);
        forP1.setLevel(WarningLevel.EMERGENCY_WARNING);
        Warning forP2 = draft(incident(Priority.P2));
        forP2.setLevel(WarningLevel.EMERGENCY_WARNING);

        assertTrue(this.service.issue(this.coordinator, forP1).isSuccess());
        assertTrue(this.service.issue(this.coordinator, forP2).isSuccess());
    }

    @Test
    @DisplayName("TC-WS-05 an expiry now or in the past is refused")
    void expiryMustBeInTheFuture() {
        Warning atNow = draft(this.p1Incident);
        atNow.setExpiresAt(NOW);
        Warning missing = draft(this.p1Incident);
        missing.setExpiresAt(null);

        assertEquals("expiresAt", this.service.issue(this.coordinator,
                atNow).getAffectedField());
        assertEquals("expiresAt", this.service.issue(this.coordinator,
                missing).getAffectedField());
    }

    @Test
    @DisplayName("TC-WS-06 a negative capacity or unnamed centre is "
            + "refused; capacity 0 is allowed")
    void evacuationCentreRules() {
        Warning negative = draft(this.p1Incident);
        negative.setEvacuationCentre(new EvacuationCentre("Town Hall", -1));
        Warning unnamed = draft(this.p1Incident);
        unnamed.setEvacuationCentre(new EvacuationCentre(" ", 100));
        Warning full = draft(this.p1Incident);
        full.setEvacuationCentre(new EvacuationCentre("Town Hall", 0));

        assertEquals("evacuationCapacity", this.service.issue(
                this.coordinator, negative).getAffectedField());
        assertEquals("evacuationCentre", this.service.issue(
                this.coordinator, unnamed).getAffectedField());
        assertTrue(this.service.issue(this.coordinator, full).isSuccess());
    }

    @Test
    @DisplayName("TC-WS-07 a missing area, message, level or incident is "
            + "named in the error")
    void requiredFieldsAreNamed() {
        Warning noArea = draft(this.p1Incident);
        noArea.setAffectedArea("");
        Warning noMessage = draft(this.p1Incident);
        noMessage.setMessage(null);
        Warning noLevel = draft(this.p1Incident);
        noLevel.setLevel(null);
        Warning noIncident = draft(this.p1Incident);
        noIncident.setIncidentId("INC-9999");

        assertEquals("affectedArea", this.service.issue(this.coordinator,
                noArea).getAffectedField());
        assertEquals("message", this.service.issue(this.coordinator,
                noMessage).getAffectedField());
        assertEquals("level", this.service.issue(this.coordinator,
                noLevel).getAffectedField());
        ServiceResult<Warning> missing =
                this.service.issue(this.coordinator, noIncident);
        assertEquals(ErrorCode.NOT_FOUND, missing.getErrorCode());
        assertEquals("incidentId", missing.getAffectedField());
    }

    @Test
    @DisplayName("TC-WS-08 a warning cannot be issued for a closed incident")
    void closedIncidentRefused() {
        this.p1Incident.setStatus(IncidentStatus.CLOSED);
        this.incidents.save(this.p1Incident);

        assertEquals(ErrorCode.CONFLICT, this.service.issue(this.coordinator,
                draft(this.p1Incident)).getErrorCode());
    }

    @Test
    @DisplayName("TC-WS-09 an update is stored as a new version and the "
            + "old version is kept")
    void updateAddsVersion() {
        Warning issued = this.service.issue(this.coordinator,
                draft(this.p1Incident)).getValue();
        Warning changed = issued.copy();
        changed.setLevel(WarningLevel.EMERGENCY_WARNING);
        changed.setRecommendedAction("Leave now via Dock Road");

        ServiceResult<Warning> result =
                this.service.update(this.coordinator, changed);

        assertTrue(result.isSuccess(), result.getMessage());
        assertEquals(2, result.getValue().getVersion());
        List<Warning> history =
                this.service.history(issued.getWarningId()).getValue();
        assertEquals(2, history.size());
        assertEquals(WarningLevel.WATCH_AND_ACT, history.get(0).getLevel());
        assertEquals(WarningLevel.EMERGENCY_WARNING,
                history.get(1).getLevel());
    }

    @Test
    @DisplayName("TC-WS-10 an update cannot move a warning to another "
            + "incident")
    void updateKeepsIncident() {
        Warning issued = this.service.issue(this.coordinator,
                draft(this.p1Incident)).getValue();
        Warning changed = issued.copy();
        changed.setIncidentId(this.p3Incident.getIncidentId());

        Warning stored = this.service.update(this.coordinator, changed)
                .getValue();

        assertEquals(this.p1Incident.getIncidentId(), stored.getIncidentId());
    }

    @Test
    @DisplayName("TC-WS-11 cancelling needs a reason, stores a cancelled "
            + "version and takes the warning off the public board")
    void cancelWarning() {
        Warning issued = this.service.issue(this.coordinator,
                draft(this.p1Incident)).getValue();
        String code = issued.getWarningId();

        assertEquals("reason", this.service.cancel(this.coordinator, code,
                " ").getAffectedField());
        ServiceResult<Warning> cancelled = this.service.cancel(
                this.coordinator, code, "Fire contained");

        assertTrue(cancelled.isSuccess());
        assertEquals(WarningStatus.CANCELLED, cancelled.getValue().getStatus());
        assertEquals(2, cancelled.getValue().getVersion());
        assertTrue(this.service.listPublic().isEmpty());
        assertEquals(ErrorCode.CONFLICT, this.service.cancel(
                this.coordinator, code, "again").getErrorCode());
        assertEquals(ErrorCode.CONFLICT, this.service.update(
                this.coordinator, issued).getErrorCode());
    }

    @Test
    @DisplayName("TC-WS-12 every issue, update and cancel is audited")
    void changesAreAudited() {
        Warning issued = this.service.issue(this.coordinator,
                draft(this.p1Incident)).getValue();
        this.service.update(this.coordinator, issued.copy());
        this.service.cancel(this.coordinator, issued.getWarningId(),
                "Fire contained");

        List<String> actions = this.audit.findByEntity("Warning",
                issued.getWarningId()).stream()
                .map(AuditEvent::getAction).sorted().toList();

        assertEquals(List.of("CANCEL_WARNING", "ISSUE_WARNING",
                "UPDATE_WARNING"), actions);
    }

    @Test
    @DisplayName("TC-WS-13 a liaison acknowledges once for their "
            + "department, and the coordinator sees who has not")
    void liaisonAcknowledges() {
        Warning issued = this.service.issue(this.coordinator,
                draft(this.p1Incident)).getValue();
        String code = issued.getWarningId();

        ServiceResult<WarningAcknowledgement> first =
                this.service.acknowledge(this.liaison, code);
        ServiceResult<WarningAcknowledgement> second =
                this.service.acknowledge(this.liaison, code);

        assertTrue(first.isSuccess(), first.getMessage());
        assertEquals(Department.SCHOOLS, first.getValue().getDepartment());
        assertEquals(ErrorCode.CONFLICT, second.getErrorCode());
        Warning listed = this.service.listForStaff().get(0);
        assertEquals(1, listed.getAcknowledgements().size());
        List<Department> pending = WarningService.pendingDepartments(listed);
        assertFalse(pending.contains(Department.SCHOOLS));
        assertEquals(Department.values().length - 1, pending.size());
    }

    @Test
    @DisplayName("TC-WS-14 only a liaison with a department can acknowledge, "
            + "and only a warning in force")
    void acknowledgementRules() {
        Warning issued = this.service.issue(this.coordinator,
                draft(this.p1Incident)).getValue();
        String code = issued.getWarningId();
        User noDepartment = new User("USR-009", "liaison2", "Kim",
                UserRole.LIAISON);

        assertEquals(ErrorCode.PERMISSION_DENIED, this.service
                .acknowledge(this.coordinator, code).getErrorCode());
        assertEquals(ErrorCode.VALIDATION_ERROR, this.service
                .acknowledge(noDepartment, code).getErrorCode());
        assertEquals(ErrorCode.NOT_FOUND, this.service
                .acknowledge(this.liaison, "WRN-9999").getErrorCode());
        this.service.cancel(this.coordinator, code, "Fire contained");
        assertEquals(ErrorCode.CONFLICT, this.service
                .acknowledge(this.liaison, code).getErrorCode());
    }

    @Test
    @DisplayName("TC-WS-15 the public board shows warnings in force, most "
            + "urgent first, without staff details")
    void publicBoardOrderAndPrivacy() {
        this.service.issue(this.coordinator, draft(this.p1Incident));
        Warning emergency = draft(this.p1Incident);
        emergency.setLevel(WarningLevel.EMERGENCY_WARNING);
        this.service.issue(this.coordinator, emergency);
        Warning expired = draft(this.p1Incident);
        expired.setExpiresAt(NOW.plusMinutes(1));
        this.service.issue(this.coordinator, expired);
        WarningService later = new WarningService(this.warnings,
                this.incidentService, new AuthenticationService(
                        new InMemoryUserRepository(),
                        new AuditService(this.audit)),
                new AuditService(this.audit),
                Clock.fixed(NOW.plusMinutes(5).atZone(ZONE).toInstant(),
                        ZONE));

        List<Warning> board = later.listPublic();

        assertEquals(2, board.size());
        assertEquals(WarningLevel.EMERGENCY_WARNING, board.get(0).getLevel());
        assertNull(board.get(0).getIssuedBy());
        assertTrue(board.get(0).getAcknowledgements().isEmpty());
    }

    @Test
    @DisplayName("TC-WS-16 the history of an unknown warning is NOT_FOUND")
    void unknownHistory() {
        assertEquals(ErrorCode.NOT_FOUND,
                this.service.history("WRN-9999").getErrorCode());
        assertEquals(ErrorCode.NOT_FOUND,
                this.service.history(null).getErrorCode());
    }

    private Incident incident(Priority priority) {
        Incident incident = new Incident(null, "Fire - Laverton",
                DisasterType.FIRE,
                new Location("2 Dock Road", "Laverton", -37.86, 144.77),
                NOW.minusHours(1));
        incident.setPriority(priority);
        return this.incidents.save(incident);
    }

    private Warning draft(Incident incident) {
        Warning warning = new Warning(incident.getIncidentId(),
                WarningLevel.WATCH_AND_ACT, "Laverton, Altona North",
                "Smoke from a warehouse fire is drifting south-east.",
                NOW.plusHours(6));
        warning.setRecommendedAction("Keep windows and doors closed.");
        return warning;
    }
}
