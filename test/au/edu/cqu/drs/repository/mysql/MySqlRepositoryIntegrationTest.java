package au.edu.cqu.drs.repository.mysql;

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
import au.edu.cqu.drs.model.warning.EvacuationCentre;
import au.edu.cqu.drs.model.warning.Warning;
import au.edu.cqu.drs.model.warning.WarningAcknowledgement;
import au.edu.cqu.drs.model.warning.WarningLevel;
import au.edu.cqu.drs.model.warning.WarningStatus;
import au.edu.cqu.drs.security.ChainVerification;
import au.edu.cqu.drs.security.FieldCipher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for the MySQL adapters, run against a real MySQL server
 * in a separate {@code drs_test} database.
 *
 * <p>They are skipped, with a message saying why, when MySQL is not
 * reachable, so the build still passes on a machine without MySQL. To run
 * them, create the test account once:</p>
 *
 * <pre>
 * CREATE USER 'drs_test'@'localhost' IDENTIFIED BY 'drs_test';
 * GRANT ALL PRIVILEGES ON drs_test.* TO 'drs_test'@'localhost';
 * </pre>
 *
 * <p>The environment variables {@code DRS_TEST_MYSQL_HOST},
 * {@code DRS_TEST_MYSQL_PORT}, {@code DRS_TEST_MYSQL_USER} and
 * {@code DRS_TEST_MYSQL_PASSWORD} override those defaults.</p>
 */
class MySqlRepositoryIntegrationTest {

    private static MySqlDatabase database;
    private static String skipReason;

    private MySqlUserRepository users;
    private MySqlReportRepository reports;
    private MySqlIncidentRepository incidents;
    private MySqlAssessmentRepository assessments;
    private MySqlAssignmentRepository assignments;
    private MySqlFieldUpdateRepository fieldUpdates;
    private MySqlAuditRepository audit;

    /**
     * Opens a freshly emptied drs_test database once before all the tests. If
     * MySQL cannot be reached, records the reason so that every test is
     * skipped with that message.
     */
    @BeforeAll
    static void connect() {
        try {
            database = MySqlTestDatabase.openFresh();
        } catch (IllegalStateException e) {
            skipReason = "MySQL integration tests skipped: "
                    + e.getMessage();
            System.out.println(skipReason);
        }
    }

    /**
     * Closes the shared database after all the tests, if it was opened.
     */
    @AfterAll
    static void disconnect() {
        if (database != null) {
            database.close();
        }
    }

    /**
     * Skips the test when MySQL is not available; otherwise empties every DRS
     * table and creates fresh repositories over the shared database.
     *
     * @throws Exception if the database cannot be reached or a query fails
     */
    @BeforeEach
    void emptyTables() throws Exception {
        Assumptions.assumeTrue(database != null, () -> skipReason);
        database.withConnection(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String table : MySqlSchema.TABLES) {
                    statement.execute("TRUNCATE TABLE " + table);
                }
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
            return null;
        });
        users = new MySqlUserRepository(database);
        reports = new MySqlReportRepository(database);
        incidents = new MySqlIncidentRepository(database);
        assessments = new MySqlAssessmentRepository(database);
        assignments = new MySqlAssignmentRepository(database);
        fieldUpdates = new MySqlFieldUpdateRepository(database);
        audit = new MySqlAuditRepository(database);
    }

    /**
     * Checks that a saved staff account is read back with all its details and
     * is given the first user code.
     */
    @Test
    @DisplayName("TC-MY-10 a staff account round-trips and gets a code")
    void userRoundTrip() {
        User user = new User(null, "coordinator", "Alex Coordinator",
                UserRole.COORDINATOR);
        user.setPasswordSalt("salt");
        user.setPasswordHash("hash");
        user.setDepartment(Department.FIRE_AND_EMERGENCY);

        users.save(user);
        User loaded = users.findByUsername("coordinator").orElseThrow();

        assertEquals("USR-001", loaded.getUserId());
        assertEquals("Alex Coordinator", loaded.getFullName());
        assertEquals(UserRole.COORDINATOR, loaded.getRole());
        assertEquals(Department.FIRE_AND_EMERGENCY, loaded.getDepartment());
        assertTrue(loaded.isActive());
    }

    /**
     * Checks that saving an existing account again updates its row instead of
     * adding a second one.
     */
    @Test
    @DisplayName("TC-MY-11 saving an existing account updates its row")
    void savingAgainUpdates() {
        User user = new User(null, "liaison", "Sam Liaison",
                UserRole.LIAISON);
        user.setPasswordSalt("salt");
        user.setPasswordHash("hash");
        users.save(user);

        user.setActive(false);
        user.setLastLoginAt(LocalDateTime.of(2026, 10, 12, 9, 0));
        users.save(user);

        assertEquals(1, users.findAll().size());
        User loaded = users.findById(user.getUserId()).orElseThrow();
        assertFalse(loaded.isActive());
        assertEquals(LocalDateTime.of(2026, 10, 12, 9, 0),
                loaded.getLastLoginAt());
    }

    /**
     * Checks that a second account with the same user name is refused and the
     * first account is left unchanged.
     */
    @Test
    @DisplayName("TC-MY-12 a duplicate user name is refused")
    void duplicateUsernameIsRefused() {
        User first = new User(null, "admin", "Robin", UserRole.ADMINISTRATOR);
        first.setPasswordSalt("salt");
        first.setPasswordHash("hash");
        users.save(first);
        User second = new User(null, "admin", "Other",
                UserRole.ADMINISTRATOR);
        second.setPasswordSalt("salt");
        second.setPasswordHash("hash");

        assertThrows(IllegalStateException.class, () -> users.save(second));
        assertEquals("Robin",
                users.findByUsername("admin").orElseThrow().getFullName());
    }

    /**
     * Checks that a saved report is read back with its code, type, location
     * and observed time, and can be found by its status.
     */
    @Test
    @DisplayName("TC-MY-13 a report round-trips with its location and status")
    void reportRoundTrip() {
        DisasterReport report = newReport();
        report.setObservedAt(LocalDateTime.of(2026, 9, 18, 11, 30));

        reports.save(report);
        DisasterReport loaded =
                reports.findById(report.getReportId()).orElseThrow();

        assertEquals("RPT-0001", loaded.getReportId());
        assertEquals(DisasterType.FIRE, loaded.getDisasterType());
        assertEquals("Laverton", loaded.getLocation().getSuburb());
        assertEquals(-37.8636, loaded.getLocation().getLatitude(), 0.00001);
        assertEquals(LocalDateTime.of(2026, 9, 18, 11, 30),
                loaded.getObservedAt());
        assertEquals(1, reports.findByStatus(ReportStatus.SUBMITTED).size());
        assertTrue(reports.findByStatus(ReportStatus.REJECTED).isEmpty());
    }

    /**
     * Checks that the reporter's name and contact are stored encrypted in the
     * reports table but are returned as plain text through the repository.
     *
     * @throws Exception if the database cannot be reached or a query fails
     */
    @Test
    @DisplayName("TC-MY-23 reporter details are encrypted in the database "
            + "and readable through the repository")
    void reporterDetailsAreEncrypted() throws Exception {
        MySqlReportRepository encrypted = new MySqlReportRepository(database,
                new FieldCipher(FieldCipher.generateKey()));
        DisasterReport report = newReport();
        report.setReporterName("Priya Shah");
        report.setReporterContact("0400 111 222");

        encrypted.save(report);
        String[] raw = database.withConnection(connection -> {
            try (var statement = connection.prepareStatement(
                    "SELECT reporter_name, reporter_contact FROM reports "
                            + "WHERE report_id = ?")) {
                statement.setString(1, report.getReportId());
                try (var rs = statement.executeQuery()) {
                    rs.next();
                    return new String[] {rs.getString(1), rs.getString(2)};
                }
            }
        });

        assertFalse(raw[0].contains("Priya"), raw[0]);
        assertFalse(raw[1].contains("0400"), raw[1]);
        DisasterReport loaded =
                encrypted.findById(report.getReportId()).orElseThrow();
        assertEquals("Priya Shah", loaded.getReporterName());
        assertEquals("0400 111 222", loaded.getReporterContact());
    }

    /**
     * Checks that an incident keeps its link to its source report, its
     * priority and its updated status when saved and read back.
     */
    @Test
    @DisplayName("TC-MY-14 an incident keeps its source report links")
    void incidentKeepsSourceReports() {
        DisasterReport report = reports.save(newReport());
        Incident incident = newIncident();
        incident.addSourceReportId(report.getReportId());

        incidents.save(incident);
        incident.setStatus(IncidentStatus.MONITORING);
        incidents.save(incident);
        Incident loaded =
                incidents.findById(incident.getIncidentId()).orElseThrow();

        assertEquals(List.of(report.getReportId()),
                loaded.getSourceReportIds());
        assertEquals(Priority.P1, loaded.getPriority());
        assertEquals(IncidentStatus.MONITORING, loaded.getStatus());
        assertEquals(1,
                incidents.findByStatus(IncidentStatus.MONITORING).size());
    }

    /**
     * Checks that an incident linked to a report that does not exist is
     * refused and the incident row is rolled back.
     */
    @Test
    @DisplayName("TC-MY-15 a link to a report that does not exist is "
            + "refused and nothing is saved")
    void linkToMissingReportRollsBack() {
        Incident incident = newIncident();
        incident.addSourceReportId("RPT-9999");

        assertThrows(IllegalStateException.class,
                () -> incidents.save(incident));
        assertTrue(incidents.findAll().isEmpty(),
                "the incident row must be rolled back with the link");
    }

    /**
     * Checks that the newest of two saved assessment versions is returned as
     * the latest for an incident.
     */
    @Test
    @DisplayName("TC-MY-16 the latest assessment version is returned")
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
                .findLatestForIncident(incident.getIncidentId())
                .orElseThrow();

        assertEquals(2, latest.getVersion());
        assertEquals(100, latest.getCalculatedScore());
        assertEquals(2, assessments
                .findByIncidentId(incident.getIncidentId()).size());
    }

    /**
     * Checks that a department assignment is read back with its status and
     * resource allocation, and is listed against its incident.
     */
    @Test
    @DisplayName("TC-MY-17 an assignment round-trips with its allocations")
    void assignmentRoundTripWithAllocations() {
        Incident incident = incidents.save(newIncident());
        DepartmentAssignment assignment = new DepartmentAssignment(null,
                incident.getIncidentId(), Department.FIRE_AND_EMERGENCY,
                "Suppress fire", "Immediate");
        assignment.setCreatedAt(LocalDateTime.of(2026, 10, 12, 9, 0));
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
        assertEquals(1, incidents.findById(incident.getIncidentId())
                .orElseThrow().getAssignments().size());
    }

    /**
     * Checks that field updates for an incident are returned in the order they
     * were submitted, not the order they were saved.
     */
    @Test
    @DisplayName("TC-MY-18 the timeline is returned in submission order")
    void timelineIsOrdered() {
        Incident incident = incidents.save(newIncident());
        fieldUpdates.save(update(incident, "Fire contained",
                LocalDateTime.of(2026, 9, 18, 12, 0)));
        fieldUpdates.save(update(incident, "Crews dispatched",
                LocalDateTime.of(2026, 9, 18, 11, 0)));

        List<FieldUpdate> timeline =
                fieldUpdates.findByIncidentId(incident.getIncidentId());

        assertEquals(2, timeline.size());
        assertEquals("Crews dispatched", timeline.get(0).getProgressNote());
        assertEquals("Fire contained", timeline.get(1).getProgressNote());
    }

    /**
     * Checks that audit events are listed newest first and can be found by the
     * entity they concern.
     */
    @Test
    @DisplayName("TC-MY-19 audit events can be read back by entity")
    void auditEventsAreQueryableByEntity() {
        audit.save(new AuditEvent("coordinator", "CREATE_INCIDENT",
                "Incident", "INC-0001", "From report RPT-0001",
                LocalDateTime.of(2026, 10, 12, 9, 0)));
        audit.save(new AuditEvent("liaison", "RESPOND_ASSIGNMENT",
                "Assignment", "ASG-0001", "ACCEPTED",
                LocalDateTime.of(2026, 10, 12, 9, 5)));

        assertEquals(2, audit.findAll().size());
        assertEquals("AUD-00002", audit.findAll().get(0).getEventId(),
                "newest first");
        assertEquals("coordinator",
                audit.findByEntity("Incident", "INC-0001").get(0).getActor());
    }

    /**
     * Checks that an audit chain no one has changed verifies as intact and
     * counts every record.
     */
    @Test
    @DisplayName("TC-MY-24 an untouched audit chain verifies")
    void untouchedChainVerifies() {
        saveAuditTrail(3);

        ChainVerification result = audit.verifyChain();

        assertTrue(result.isIntact(), result.toString());
        assertEquals(3, result.getChecked());
    }

    /**
     * Checks that editing an audit row directly in the database breaks the
     * chain at that row.
     *
     * @throws Exception if the database cannot be reached or a query fails
     */
    @Test
    @DisplayName("TC-MY-25 an edited audit row breaks the chain at that row")
    void editedRowBreaksTheChain() throws Exception {
        saveAuditTrail(3);
        execute("UPDATE audit_events SET detail = 'REJECTED' "
                + "WHERE event_id = 'AUD-00002'");

        ChainVerification result = audit.verifyChain();

        assertFalse(result.isIntact());
        assertEquals("AUD-00002", result.getFirstBrokenEventId());
        assertEquals("its content was changed", result.getReason());
    }

    /**
     * Checks that deleting an audit row breaks the chain at the row that
     * follows it.
     *
     * @throws Exception if the database cannot be reached or a query fails
     */
    @Test
    @DisplayName("TC-MY-26 a deleted audit row breaks the chain at the next "
            + "row")
    void deletedRowBreaksTheChain() throws Exception {
        saveAuditTrail(3);
        execute("DELETE FROM audit_events WHERE event_id = 'AUD-00002'");

        ChainVerification result = audit.verifyChain();

        assertFalse(result.isIntact());
        assertEquals("AUD-00003", result.getFirstBrokenEventId());
    }

    /**
     * Checks that back-dating an audit row's time stamp breaks the chain at
     * that row.
     *
     * @throws Exception if the database cannot be reached or a query fails
     */
    @Test
    @DisplayName("TC-MY-27 a back-dated time stamp breaks the chain")
    void changedTimeStampBreaksTheChain() throws Exception {
        saveAuditTrail(2);
        execute("UPDATE audit_events SET occurred_at = '2026-01-01 00:00:00'"
                + " WHERE event_id = 'AUD-00001'");

        assertEquals("AUD-00001",
                audit.verifyChain().getFirstBrokenEventId());
    }

    /**
     * Checks that audit events saved by ten threads at once still form a
     * single intact chain.
     *
     * @throws Exception if a save fails or a thread is interrupted
     */
    @Test
    @DisplayName("TC-MY-28 audit rows written by ten clients at once still "
            + "form one chain")
    void concurrentAuditWritesStayChained() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<AuditEvent>> tasks = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                String detail = "client " + i;
                tasks.add(() -> audit.save(new AuditEvent("coordinator",
                        "LOGIN_SUCCESS", "User", "USR-001", detail,
                        LocalDateTime.now())));
            }
            for (Future<AuditEvent> future : pool.invokeAll(tasks)) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }

        ChainVerification result = audit.verifyChain();
        assertTrue(result.isIntact(), result.toString());
        assertEquals(10, result.getChecked());
    }

    /**
     * Checks that warning versions are stored, the newest version is returned
     * with its level and evacuation centre, and the latest version of each
     * warning is listed.
     */
    @Test
    @DisplayName("TC-MY-29 warning versions are stored and the newest is "
            + "returned")
    void warningVersionsRoundTrip() {
        MySqlWarningRepository warnings = new MySqlWarningRepository(database);
        Incident incident = incidents.save(newIncident());
        Warning first = newWarning(incident);
        warnings.saveVersion(first);
        Warning second = first.copy();
        second.setVersion(2);
        second.setLevel(WarningLevel.EMERGENCY_WARNING);
        second.setEvacuationCentre(
                new EvacuationCentre("Laverton Community Hall", 250));
        warnings.saveVersion(second);
        warnings.saveVersion(newWarning(incident));

        Warning latest = warnings.findLatest("WRN-0001").orElseThrow();

        assertEquals("WRN-0001", first.getWarningId());
        assertEquals(2, latest.getVersion());
        assertEquals(WarningLevel.EMERGENCY_WARNING, latest.getLevel());
        assertEquals(250, latest.getEvacuationCentre().getCapacity());
        assertEquals(2, warnings.findHistory("WRN-0001").size());
        assertEquals(List.of("WRN-0001", "WRN-0002"),
                warnings.findLatestVersions().stream()
                        .map(Warning::getWarningId).toList());
    }

    /**
     * Checks that saving the same version of a warning twice is refused.
     */
    @Test
    @DisplayName("TC-MY-30 the same warning version cannot be saved twice")
    void duplicateWarningVersionRefused() {
        MySqlWarningRepository warnings = new MySqlWarningRepository(database);
        Warning first = warnings.saveVersion(
                newWarning(incidents.save(newIncident())));
        Warning clash = first.copy();

        assertThrows(IllegalStateException.class,
                () -> warnings.saveVersion(clash));
    }

    /**
     * Checks that a department can acknowledge a warning only once and the
     * acknowledgement is stored.
     */
    @Test
    @DisplayName("TC-MY-31 a department acknowledges a warning only once")
    void acknowledgementsAreUniquePerDepartment() {
        MySqlWarningRepository warnings = new MySqlWarningRepository(database);
        Warning warning = warnings.saveVersion(
                newWarning(incidents.save(newIncident())));
        WarningAcknowledgement ack = new WarningAcknowledgement(
                warning.getWarningId(), Department.SCHOOLS, "liaison",
                LocalDateTime.of(2026, 10, 13, 10, 0));

        warnings.saveAcknowledgement(ack);

        assertThrows(IllegalStateException.class,
                () -> warnings.saveAcknowledgement(ack));
        assertEquals(Department.SCHOOLS, warnings.findAcknowledgements(
                warning.getWarningId()).get(0).getDepartment());
    }

    /**
     * Checks that records imported from DRS-Initial keep their codes and that
     * importing them a second time adds nothing.
     */
    @Test
    @DisplayName("TC-MY-32 A2 records keep their codes, and a second import "
            + "adds nothing")
    void importIsIdempotent() {
        MySqlImportStore store = new MySqlImportStore(database,
                new FieldCipher(FieldCipher.generateKey()));
        DisasterReport report = newReport();
        report.setReportId("RPT-0007");
        Incident incident = newIncident();
        incident.setIncidentId("INC-0003");
        incident.addSourceReportId("RPT-0007");
        Assessment assessment = new Assessment("INC-0003", 5, 4, 4, 4);
        assessment.setAssessmentId("ASM-0002");
        assessment.setVersion(1);
        DepartmentAssignment assignment = new DepartmentAssignment("ASG-0004",
                "INC-0003", Department.FIRE_AND_EMERGENCY, "Suppress fire",
                "Immediate");
        assignment.setStatus(AssignmentStatus.ACCEPTED);
        LocalDateTime from = LocalDateTime.of(2026, 9, 18, 9, 0);
        assignment.addAllocation(new ResourceAllocation("ALC-0009",
                "ASG-0004", "Pumper 12", Department.FIRE_AND_EMERGENCY, 1,
                from, from.plusHours(4)));
        FieldUpdate update = update(incident, "Crews on scene",
                LocalDateTime.of(2026, 9, 18, 11, 0));
        update.setUpdateId("UPD-0005");

        for (int run = 1; run <= 2; run++) {
            boolean first = run == 1;
            assertEquals(first, store.importReport(report), "report " + run);
            assertEquals(first, store.importIncident(incident));
            assertEquals(first, store.importAssessment(assessment));
            assertEquals(first, store.importAssignment(assignment));
            assertEquals(first, store.importFieldUpdate(update));
        }

        assertEquals(1, reports.findAll().size());
        assertEquals(List.of("RPT-0007"), incidents.findById("INC-0003")
                .orElseThrow().getSourceReportIds());
        assertEquals(1, assignments.findById("ASG-0004").orElseThrow()
                .getAllocations().size());
        assertEquals(1, fieldUpdates.findByIncidentId("INC-0003").size());
    }

    /**
     * Checks that an imported account whose user name already exists is
     * skipped, and a new one is given the next free code.
     */
    @Test
    @DisplayName("TC-MY-33 an existing user name is not imported again")
    void importedUsersMatchByUsername() {
        MySqlImportStore store = new MySqlImportStore(database,
                new FieldCipher(FieldCipher.generateKey()));
        User live = new User(null, "coordinator", "Alex", UserRole.COORDINATOR);
        live.setPasswordSalt("salt");
        live.setPasswordHash("hash");
        users.save(live);
        User a2Coordinator = new User("USR-001", "coordinator", "Old",
                UserRole.COORDINATOR);
        a2Coordinator.setPasswordSalt("old");
        a2Coordinator.setPasswordHash("old");
        User a2Liaison = new User("USR-001", "liaison2", "Kim",
                UserRole.LIAISON);
        a2Liaison.setPasswordSalt("s");
        a2Liaison.setPasswordHash("h");

        assertFalse(store.importUser(a2Coordinator));
        assertTrue(store.importUser(a2Liaison));

        assertEquals("Alex",
                users.findByUsername("coordinator").orElseThrow()
                        .getFullName());
        assertEquals("USR-002",
                users.findByUsername("liaison2").orElseThrow().getUserId());
    }

    /**
     * Checks that an imported audit event is added once, keeps its DRS-Initial
     * code in its detail, hides the reporter's name and joins the chain.
     *
     * @throws Exception if the database cannot be reached or a query fails
     */
    @Test
    @DisplayName("TC-MY-34 imported audit events join the chain, keep their "
            + "A2 code and never name a reporter")
    void importedAuditEvents() throws Exception {
        MySqlImportStore store = new MySqlImportStore(database,
                new FieldCipher(FieldCipher.generateKey()));
        AuditEvent submitted = new AuditEvent("Priya Shah", "SUBMIT_REPORT",
                "DisasterReport", "RPT-0001", "Fire at Laverton",
                LocalDateTime.of(2026, 9, 18, 9, 14));
        submitted.setEventId("AUD-00012");

        assertTrue(store.importAuditEvent(submitted));
        assertFalse(store.importAuditEvent(submitted));

        List<AuditEvent> stored = audit.findAll();
        assertEquals(1, stored.size());
        assertEquals("public", stored.get(0).getActor());
        assertEquals("[A2 AUD-00012] Fire at Laverton",
                stored.get(0).getDetail());
        assertTrue(audit.verifyChain().isIntact());
    }

    /**
     * Checks that reports saved by ten threads at once are all stored with
     * different codes.
     *
     * @throws Exception if a save fails or a thread is interrupted
     */
    @Test
    @DisplayName("TC-MY-20 ten clients saving at once all get different "
            + "codes")
    void concurrentSavesGetUniqueCodes() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(10);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                tasks.add(() -> reports.save(newReport()).getReportId());
            }
            Set<String> codes = new HashSet<>();
            for (Future<String> future : pool.invokeAll(tasks)) {
                codes.add(future.get());
            }
            assertEquals(10, codes.size());
            assertEquals(10, reports.findAll().size());
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Checks that a committed report can still be read after opening a new
     * connection pool to the database.
     */
    @Test
    @DisplayName("TC-MY-21 committed data survives closing and reopening")
    void dataSurvivesReopening() {
        reports.save(newReport());

        try (MySqlDatabase reopened =
                new MySqlDatabase(MySqlTestDatabase.settings())) {
            MySqlReportRepository afterRestart =
                    new MySqlReportRepository(reopened);
            assertFalse(afterRestart.findAll().isEmpty(),
                    "NFR-03 requires the last committed data to be "
                            + "available after an interruption");
        }
    }

    /**
     * Checks that every table in the schema exists in the drs_test database.
     *
     * @throws Exception if the database cannot be reached or a query fails
     */
    @Test
    @DisplayName("TC-MY-22 the server creates every table on start-up")
    void everyTableExists() throws Exception {
        int found = database.withConnection(connection -> {
            int count = 0;
            for (String table : MySqlSchema.TABLES) {
                try (var rs = connection.getMetaData().getTables(
                        "drs_test", null, table, null)) {
                    if (rs.next()) {
                        count++;
                    }
                }
            }
            return count;
        });

        assertEquals(MySqlSchema.TABLES.size(), found);
        assertNotNull(database.toString());
    }

    /**
     * Builds an active Watch and Act warning for the given incident, version
     * 1, issued by the coordinator.
     *
     * @param incident the incident the warning is for
     * @return the warning, not yet saved
     */
    private Warning newWarning(Incident incident) {
        Warning warning = new Warning(incident.getIncidentId(),
                WarningLevel.WATCH_AND_ACT, "Laverton, Altona North",
                "Smoke from a warehouse fire", LocalDateTime.of(2026, 10,
                        13, 18, 0));
        warning.setVersion(1);
        warning.setStatus(WarningStatus.ACTIVE);
        warning.setIssuedBy("coordinator");
        warning.setIssuedAt(LocalDateTime.of(2026, 10, 13, 9, 0));
        return warning;
    }

    /**
     * Saves the given number of report-decision audit events, one minute
     * apart.
     *
     * @param count how many audit events to save
     */
    private void saveAuditTrail(int count) {
        for (int i = 1; i <= count; i++) {
            audit.save(new AuditEvent("coordinator", "DECIDE_REPORT",
                    "DisasterReport", "RPT-000" + i, "VALIDATED",
                    LocalDateTime.of(2026, 10, 13, 9, i)));
        }
    }

    /**
     * Runs an SQL update directly on the database, bypassing the repositories.
     *
     * @param sql the SQL statement to run
     * @throws Exception if the database cannot be reached or the statement
     *         fails
     */
    private void execute(String sql) throws Exception {
        database.withConnection(connection -> {
            try (Statement statement = connection.createStatement()) {
                return statement.executeUpdate(sql);
            }
        });
    }

    /**
     * Builds a submitted fire report at Laverton with valid values.
     *
     * @return the report, not yet saved
     */
    private DisasterReport newReport() {
        DisasterReport report = new DisasterReport();
        report.setDisasterType(DisasterType.FIRE);
        report.setLocation(
                new Location("2 Dock Road", "Laverton", -37.8636, 144.7700));
        report.setObservedAt(LocalDateTime.of(2026, 10, 12, 8, 50));
        report.setDescription("Flames visible");
        report.setStatus(ReportStatus.SUBMITTED);
        return report;
    }

    /**
     * Builds a P1 warehouse fire incident at Laverton with no code yet.
     *
     * @return the incident, not yet saved
     */
    private Incident newIncident() {
        Incident incident = new Incident(null, "Warehouse fire - Laverton",
                DisasterType.FIRE,
                new Location("2 Dock Road", "Laverton", -37.8636, 144.7700),
                LocalDateTime.of(2026, 10, 12, 9, 0));
        incident.setPriority(Priority.P1);
        return incident;
    }

    /**
     * Builds a responder's field update for the given incident.
     *
     * @param incident the incident the update is for
     * @param note the progress note
     * @param at when the update was submitted
     * @return the field update, not yet saved
     */
    private FieldUpdate update(Incident incident, String note,
            LocalDateTime at) {
        FieldUpdate fieldUpdate = new FieldUpdate();
        fieldUpdate.setIncidentId(incident.getIncidentId());
        fieldUpdate.setSubmittedBy("responder");
        fieldUpdate.setSubmittedAt(at);
        fieldUpdate.setProgressNote(note);
        return fieldUpdate;
    }

    /**
     * Describes this test class and the fixture it uses.
     *
     * @return the class name and the repositories it tests
     */
    @Override
    public String toString() {
        return "MySqlRepositoryIntegrationTest[database=" + database
                + ", users=" + this.users + ", reports=" + this.reports
                + ", incidents=" + this.incidents + ", audit=" + this.audit
                + "]";
    }
}
