package au.edu.cqu.drs.service;

import au.edu.cqu.drs.TestFixtures;
import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.FieldUpdate;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.IncidentStatus;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.model.ReportStatus;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the incident lifecycle, timeline and dashboard ordering (FR-06,
        FR-10, FR-11). */
class IncidentServiceTest {

    private TestFixtures fixtures;
    private User coordinator;
    private User responder;

    /**
     * Creates fresh fixtures, a coordinator and a responder.
     */
    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures();
        coordinator = fixtures.createUser("coordinator", UserRole.COORDINATOR);
        responder = fixtures.createUser("responder", UserRole.RESPONDER);
    }

    /**
     * Checks that an incident can only be created from a validated report.
     */
    @Test
    @DisplayName("TC-IN-01 an incident can only be created from a validated "
            + "report")
    void incidentRequiresValidatedReport() {
        DisasterReport submitted = fixtures.reportService.submitReport(
                fixtures.validReport(DisasterType.FIRE, "Laverton", -37.86,
                                     144.77, 
                        LocalDateTime.now().minusMinutes(5))).getValue();

        ServiceResult<Incident> refused =
                fixtures.incidentService.createFromReport(coordinator,
                                                          submitted);
        assertTrue(refused.isFailure());
        assertEquals(ErrorCode.CONFLICT, refused.getErrorCode());

        DisasterReport validated =
                fixtures.reportService.decideReport(coordinator,
                
                
                
                
                
                
                
                
                submitted.getReportId(), ReportStatus
                        .VALIDATED, null).getValue();
        ServiceResult<Incident> created =
                fixtures.incidentService.createFromReport(coordinator,
                                                          validated);

        assertTrue(created.isSuccess());
        assertNotNull(created.getValue().getIncidentId());
        assertTrue(created.getValue().getSourceReportIds()
                .contains(submitted.getReportId()));
    }

    /**
     * Checks that a permitted lifecycle transition is accepted.
     */
    @Test
    @DisplayName("TC-IN-02 a permitted lifecycle transition is accepted")
    void permittedTransitionIsAccepted() {
        Incident incident = openIncident(Priority.P2);

        ServiceResult<Incident> result = fixtures.incidentService
                .changeStatus(coordinator, incident.getIncidentId(),
                              IncidentStatus.MONITORING);

        assertTrue(result.isSuccess());
        assertEquals(IncidentStatus.MONITORING, result.getValue().getStatus());
    }

    /**
     * Checks that an invalid lifecycle transition is refused.
     */
    @Test
    @DisplayName("TC-IN-03 an invalid lifecycle transition is refused")
    void invalidTransitionIsRefused() {
        Incident incident = openIncident(Priority.P2);
        fixtures.incidentService.closeIncident(coordinator,
                                               incident.getIncidentId(), 
                "Resolved on site", "None");

        ServiceResult<Incident> result = fixtures.incidentService
                .changeStatus(coordinator, incident.getIncidentId(),
                              IncidentStatus.MONITORING);

        assertTrue(result.isFailure());
        assertEquals(ErrorCode.CONFLICT, result.getErrorCode());
    }

    /**
     * Checks that closure without an outcome is refused.
     */
    @Test
    @DisplayName("TC-IN-04 closure without an outcome is refused")
    void closureWithoutOutcomeIsRefused() {
        Incident incident = openIncident(Priority.P3);

        ServiceResult<Incident> result = fixtures.incidentService
                .closeIncident(coordinator, incident.getIncidentId(), "  ",
                               "None");

        assertTrue(result.isFailure());
        assertEquals("outcome", result.getAffectedField());
    }

    /**
     * Checks that closure is blocked while a departmental request is
     * unresolved.
     */
    @Test
    @DisplayName("TC-IN-05 closure is blocked while a departmental request is "
            + "unresolved")
    void closureBlockedByUnresolvedAssignment() {
        Incident incident = openIncident(Priority.P1);
        fixtures.coordinationService.createAssignment(coordinator,
                                                      incident.getIncidentId(), 
                Department.FIRE_AND_EMERGENCY, "Suppress fire", "Immediate");

        ServiceResult<Incident> result = fixtures.incidentService.closeIncident(
                coordinator, incident.getIncidentId(), "Fire extinguished",
                        "Smouldering debris");

        assertTrue(result.isFailure());
        assertEquals(ErrorCode.CONFLICT, result.getErrorCode());
    }

    /**
     * Checks that a field update records the author, the time and the first
     * response.
     */
    @Test
    @DisplayName("TC-IN-06 a field update records the author, the time and the "
            + "first response")
    void fieldUpdateRecordsProvenance() {
        Incident incident = openIncident(Priority.P1);

        FieldUpdate update = new FieldUpdate();
        update.setIncidentId(incident.getIncidentId());
        update.setProgressNote("Crews on scene, two appliances committed");

        ServiceResult<FieldUpdate> result =
                fixtures.incidentService.addFieldUpdate(responder, update);

        assertTrue(result.isSuccess());
        assertEquals("responder", result.getValue().getSubmittedBy());
        assertNotNull(result.getValue().getSubmittedAt());
        assertNotNull(fixtures.incidentRepository
                .findById(incident.getIncidentId())
                .orElseThrow().getFirstResponseAt());
    }

    /**
     * Checks that an update without a progress note is refused.
     */
    @Test
    @DisplayName("TC-IN-07 an update without a progress note is refused")
    void updateWithoutNoteIsRefused() {
        Incident incident = openIncident(Priority.P2);
        FieldUpdate update = new FieldUpdate();
        update.setIncidentId(incident.getIncidentId());
        update.setProgressNote("   ");

        ServiceResult<FieldUpdate> result =
                fixtures.incidentService.addFieldUpdate(responder, update);

        assertTrue(result.isFailure());
        assertEquals("progressNote", result.getAffectedField());
    }

    /**
     * Checks that an update written against a stale incident version is
     * refused.
     */
    @Test
    @DisplayName("TC-IN-08 an update written against a stale incident version "
            + "is refused")
    void staleUpdateIsRefused() {
        Incident incident = openIncident(Priority.P2);
        fixtures.incidentService.changeStatus(coordinator,
                                              incident.getIncidentId(), 
                IncidentStatus.MONITORING);

        FieldUpdate update = new FieldUpdate();
        update.setIncidentId(incident.getIncidentId());
        update.setProgressNote("Written from an old screen");
        update.setIncidentVersionSeen(1);

        ServiceResult<FieldUpdate> result =
                fixtures.incidentService.addFieldUpdate(responder, update);

        assertTrue(result.isFailure());
        assertEquals(ErrorCode.CONFLICT, result.getErrorCode());
    }

    /**
     * Checks that the dashboard orders open incidents by priority and then by
     * age.
     */
    @Test
    @DisplayName("TC-IN-09 the dashboard orders open incidents by priority and "
            + "then by age")
    void dashboardOrdersByPriorityThenAge() {
        Incident oldP2 = openIncidentAt(Priority.P2,
                                        LocalDateTime.now().minusHours(3));
        Incident newP1 = openIncidentAt(Priority.P1,
                                        LocalDateTime.now().minusMinutes(5));
        Incident newP2 = openIncidentAt(Priority.P2,
                                        LocalDateTime.now().minusMinutes(1));

        List<Incident> ordered =
                fixtures.incidentService.findOpenIncidentsByPriority();

        assertEquals(newP1.getIncidentId(), ordered.get(0).getIncidentId());
        assertEquals(oldP2.getIncidentId(), ordered.get(1).getIncidentId());
        assertEquals(newP2.getIncidentId(), ordered.get(2).getIncidentId());
    }

    /**
     * Checks that a closed incident is excluded from the open dashboard.
     */
    @Test
    @DisplayName("TC-IN-10 a closed incident is excluded from the open "
            + "dashboard")
    void closedIncidentIsExcluded() {
        Incident incident = openIncident(Priority.P4);
        fixtures.incidentService.closeIncident(coordinator,
                                               incident.getIncidentId(), 
                "No action required", null);

        assertTrue(fixtures.incidentService.findOpenIncidentsByPriority()
                .stream()
                .noneMatch(i -> i.getIncidentId()
                        .equals(incident.getIncidentId())));
    }

    /**
     * Saves a fire incident with the given priority, created two minutes ago.
     *
     * @param priority the incident priority
     * @return the saved incident
     */
    private Incident openIncident(Priority priority) {
        return openIncidentAt(priority, LocalDateTime.now().minusMinutes(2));
    }

    /**
     * Saves a fire incident with the given priority and creation time.
     *
     * @param priority  the incident priority
     * @param createdAt when the incident was created
     * @return the saved incident
     */
    private Incident openIncidentAt(Priority priority,
                                    LocalDateTime createdAt) {
        Incident incident = new Incident(null, "Test incident",
                                         DisasterType.FIRE, 
                new Location("1 Test Street", "Laverton", -37.86,
                             144.77), createdAt);
        incident.setPriority(priority);
        return fixtures.incidentRepository.save(incident);
    }

    /**
     * Describes this test class.
     *
     * @return a short description of this object
     */
    @Override
    public String toString() {
        return "IncidentServiceTest: tests for IncidentService";
    }
}
