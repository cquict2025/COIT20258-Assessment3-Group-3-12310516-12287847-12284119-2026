package au.edu.cqu.drs.repository;

import au.edu.cqu.drs.model.Assessment;
import au.edu.cqu.drs.model.AssignmentStatus;
import au.edu.cqu.drs.model.AuditEvent;
import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.DepartmentAssignment;
import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.FieldUpdate;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.IncidentStatus;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.model.ReportStatus;
import au.edu.cqu.drs.model.ResourceAllocation;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.repository.sqlite.Database;
import au.edu.cqu.drs.repository.sqlite.SqliteAssessmentRepository;
import au.edu.cqu.drs.repository.sqlite.SqliteAssignmentRepository;
import au.edu.cqu.drs.repository.sqlite.SqliteAuditRepository;
import au.edu.cqu.drs.repository.sqlite.SqliteFieldUpdateRepository;
import au.edu.cqu.drs.repository.sqlite.SqliteIncidentRepository;
import au.edu.cqu.drs.repository.sqlite.SqliteReportRepository;
import au.edu.cqu.drs.repository.sqlite.SqliteUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the SQLite adapters.
 *
 * <p>The rest of the suite runs on in-memory doubles so that it stays fast and
 * independent of the file system. These tests exist because that leaves the
 * real SQL unexercised: they open a database in a temporary directory, write
 * through every adapter and read the rows back, so a mistake in a statement or
 * in the schema is caught by the build rather than by the marker.</p>
 */
class SqliteRepositoryIntegrationTest {

    @TempDir
    Path tempDirectory;

    private Database database;
    private SqliteUserRepository users;
    private SqliteReportRepository reports;
    private SqliteIncidentRepository incidents;
    private SqliteAssessmentRepository assessments;
    private SqliteAssignmentRepository assignments;
    private SqliteFieldUpdateRepository fieldUpdates;
    private SqliteAuditRepository audit;

    @BeforeEach
    void setUp() {
        database = new Database(
                tempDirectory.resolve("drs-test.db").toString());
        users = new SqliteUserRepository(database);
        reports = new SqliteReportRepository(database);
        incidents = new SqliteIncidentRepository(database);
        assessments = new SqliteAssessmentRepository(database);
        assignments = new SqliteAssignmentRepository(database);
        fieldUpdates = new SqliteFieldUpdateRepository(database);
        audit = new SqliteAuditRepository(database);
    }

    @AfterEach
    void tearDown() {
        database.close();
    }

    @Test
    @DisplayName("TC-DB-01 a staff account round-trips through the users "
            + "table")
    void userRoundTrip() {
        User user = new User(null, "coordinator", "Alex Coordinator",
                UserRole.COORDINATOR);
        user.setPasswordSalt("salt");
        user.setPasswordHash("hash");
        user.setDepartment(Department.FIRE_AND_EMERGENCY);

        users.save(user);
        User loaded = users.findByUsername("coordinator").orElseThrow();

        assertNotNull(loaded.getUserId());
        assertEquals("Alex Coordinator", loaded.getFullName());
        assertEquals(UserRole.COORDINATOR, loaded.getRole());
        assertEquals(Department.FIRE_AND_EMERGENCY, loaded.getDepartment());
        assertTrue(loaded.isActive());
    }

    @Test
    @DisplayName("TC-DB-02 a report round-trips with its location and status")
    void reportRoundTrip() {
        DisasterReport report = new DisasterReport();
        report.setDisasterType(DisasterType.FIRE);
        report.setLocation(
                new Location("2 Dock Road", "Laverton", -37.8636, 144.7700));
        report.setObservedAt(LocalDateTime.of(2026, 9, 18, 11, 30));
        report.setDescription("Flames visible from the street");
        report.setStatus(ReportStatus.SUBMITTED);

        reports.save(report);
        DisasterReport loaded =
                reports.findById(report.getReportId()).orElseThrow();

        assertEquals(DisasterType.FIRE, loaded.getDisasterType());
        assertEquals("Laverton", loaded.getLocation().getSuburb());
        assertEquals(-37.8636, loaded.getLocation().getLatitude(), 0.00001);
        assertEquals(LocalDateTime.of(2026, 9, 18, 11, 30),
                loaded.getObservedAt());
        assertEquals(1, reports.findByStatus(ReportStatus.SUBMITTED).size());
    }

    @Test
    @DisplayName("TC-DB-03 an incident keeps its source report links")
    void incidentKeepsSourceReports() {
        DisasterReport report = savedReport();
        Incident incident = newIncident();
        incident.addSourceReportId(report.getReportId());

        incidents.save(incident);
        Incident loaded =
                incidents.findById(incident.getIncidentId()).orElseThrow();

        assertEquals(1, loaded.getSourceReportIds().size());
        assertEquals(report.getReportId(), loaded.getSourceReportIds().get(0));
        assertEquals(Priority.P1, loaded.getPriority());
        assertEquals(IncidentStatus.ACTIVE, loaded.getStatus());
    }

    @Test
    @DisplayName("TC-DB-04 the latest assessment version is returned")
    void latestAssessmentVersionIsReturned() {
        Incident incident = incidents.save(newIncident());

        Assessment first = new Assessment(incident.getIncidentId(), 3, 3, 3, 4);
        first.setVersion(1);
        first.setCalculatedScore(60);
        first.setRecommendedPriority(Priority.P2);
        assessments.save(first);

        Assessment second =
                new Assessment(incident.getIncidentId(), 5, 5, 5, 5);
        second.setVersion(2);
        second.setCalculatedScore(100);
        second.setRecommendedPriority(Priority.P1);
        assessments.save(second);

        Assessment latest = assessments
                .findLatestForIncident(incident.getIncidentId()).orElseThrow();

        assertEquals(2, latest.getVersion());
        assertEquals(100, latest.getCalculatedScore());
        assertEquals(2, assessments
                .findByIncidentId(incident.getIncidentId()).size());
    }

    @Test
    @DisplayName("TC-DB-05 an assignment round-trips with its allocations")
    void assignmentRoundTripWithAllocations() {
        Incident incident = incidents.save(newIncident());
        DepartmentAssignment assignment = new DepartmentAssignment(null,
                incident.getIncidentId(), Department.FIRE_AND_EMERGENCY,
                "Suppress fire", "Immediate");
        assignment.setCreatedAt(LocalDateTime.now());
        assignment.setStatus(AssignmentStatus.AWAITING_RESPONSE);
        assignments.save(assignment);

        LocalDateTime from = LocalDateTime.of(2026, 9, 18, 9, 0);
        assignments.saveAllocation(new ResourceAllocation(null,
                assignment.getAssignmentId(), "Pumper 12",
                Department.FIRE_AND_EMERGENCY, 1, from, from.plusHours(4)));

        DepartmentAssignment loaded = assignments
                .findById(assignment.getAssignmentId()).orElseThrow();

        assertEquals(Department.FIRE_AND_EMERGENCY, loaded.getDepartment());
        assertEquals(AssignmentStatus.AWAITING_RESPONSE, loaded.getStatus());
        assertEquals(1, loaded.getAllocations().size());
        assertEquals("Pumper 12",
                loaded.getAllocations().get(0).getResourceName());
        assertEquals(1, assignments
                .findAllocationsByResourceName("pumper 12").size());
    }

    @Test
    @DisplayName("TC-DB-06 the timeline is returned in submission order")
    void timelineIsOrdered() {
        Incident incident = incidents.save(newIncident());
        fieldUpdates.save(update(incident, "Crews dispatched",
                LocalDateTime.of(2026, 9, 18, 11, 0)));
        fieldUpdates.save(update(incident, "Fire contained",
                LocalDateTime.of(2026, 9, 18, 12, 0)));

        var timeline = fieldUpdates.findByIncidentId(incident.getIncidentId());

        assertEquals(2, timeline.size());
        assertEquals("Crews dispatched", timeline.get(0).getProgressNote());
        assertEquals("Fire contained", timeline.get(1).getProgressNote());
    }

    @Test
    @DisplayName("TC-DB-07 audit events can be read back by entity")
    void auditEventsAreQueryableByEntity() {
        audit.save(new AuditEvent("coordinator", "CREATE_INCIDENT", "Incident",
                "INC-0001", "From report RPT-0001", LocalDateTime.now()));
        audit.save(new AuditEvent("liaison", "RESPOND_ASSIGNMENT", "Assignment",
                "ASG-0001", "ACCEPTED", LocalDateTime.now()));

        assertEquals(2, audit.findAll().size());
        assertEquals(1, audit.findByEntity("Incident", "INC-0001").size());
        assertEquals("coordinator",
                audit.findByEntity("Incident", "INC-0001").get(0).getActor());
    }

    @Test
    @DisplayName("TC-DB-08 committed data survives closing and reopening")
    void dataSurvivesReopening() {
        savedReport();
        database.close();

        Database reopened = new Database(
                tempDirectory.resolve("drs-test.db").toString());
        SqliteReportRepository afterRestart =
                new SqliteReportRepository(reopened);

        assertFalse(afterRestart.findAll().isEmpty(),
                "NFR-03 requires the last committed data to be available "
                        + "after an interruption");
        reopened.close();
    }

    private DisasterReport savedReport() {
        DisasterReport report = new DisasterReport();
        report.setDisasterType(DisasterType.FIRE);
        report.setLocation(
                new Location("2 Dock Road", "Laverton", -37.8636, 144.7700));
        report.setObservedAt(LocalDateTime.now().minusMinutes(10));
        report.setDescription("Flames visible");
        report.setStatus(ReportStatus.SUBMITTED);
        return reports.save(report);
    }

    private Incident newIncident() {
        Incident incident = new Incident(null, "Warehouse fire - Laverton",
                DisasterType.FIRE,
                new Location("2 Dock Road", "Laverton", -37.8636, 144.7700),
                LocalDateTime.now().minusMinutes(5));
        incident.setPriority(Priority.P1);
        return incident;
    }

    private FieldUpdate update(Incident incident, String note,
            LocalDateTime at) {
        FieldUpdate fieldUpdate = new FieldUpdate();
        fieldUpdate.setIncidentId(incident.getIncidentId());
        fieldUpdate.setSubmittedBy("responder");
        fieldUpdate.setSubmittedAt(at);
        fieldUpdate.setProgressNote(note);
        return fieldUpdate;
    }
}
