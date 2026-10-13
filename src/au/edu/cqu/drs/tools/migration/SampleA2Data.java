package au.edu.cqu.drs.tools.migration;

import au.edu.cqu.drs.DemoDataSeeder;
import au.edu.cqu.drs.model.Assessment;
import au.edu.cqu.drs.model.AssignmentStatus;
import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.DepartmentAssignment;
import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.FieldUpdate;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.model.ReportStatus;
import au.edu.cqu.drs.model.ResourceAllocation;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.repository.memory.SimulatedPartnerGateway;
import au.edu.cqu.drs.repository.sqlite.Database;
import au.edu.cqu.drs.repository.sqlite.SqliteAssessmentRepository;
import au.edu.cqu.drs.repository.sqlite.SqliteAssignmentRepository;
import au.edu.cqu.drs.repository.sqlite.SqliteAuditRepository;
import au.edu.cqu.drs.repository.sqlite.SqliteFieldUpdateRepository;
import au.edu.cqu.drs.repository.sqlite.SqliteIncidentRepository;
import au.edu.cqu.drs.repository.sqlite.SqliteReportRepository;
import au.edu.cqu.drs.repository.sqlite.SqliteUserRepository;
import au.edu.cqu.drs.service.AssessmentService;
import au.edu.cqu.drs.service.AuditService;
import au.edu.cqu.drs.service.AuthenticationService;
import au.edu.cqu.drs.service.CoordinationService;
import au.edu.cqu.drs.service.IncidentService;
import au.edu.cqu.drs.service.ReportService;
import au.edu.cqu.drs.service.ServiceResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * Writes a DRS-Initial (Assessment 2) SQLite database of sample data, by
 * running DRS-Initial's own services on its own SQLite adapters. The
 * result looks exactly like a database the A2 prototype produced: public
 * reports, triage decisions, incidents with assessments, a departmental
 * assignment with a resource, field updates, and the audit trail.
 *
 * <p>Usage: {@code SampleA2Data <new-file.db>}. It refuses to touch a file
 * that already exists.</p>
 */
public final class SampleA2Data {

    private final AuthenticationService authentication;
    private final ReportService reports;
    private final IncidentService incidents;
    private final AssessmentService assessments;
    private final CoordinationService coordination;
    private final SqliteUserRepository users;

    private SampleA2Data(Database database) {
        this.users = new SqliteUserRepository(database);
        SqliteIncidentRepository incidentStore =
                new SqliteIncidentRepository(database);
        AuditService audit =
                new AuditService(new SqliteAuditRepository(database));
        this.authentication = new AuthenticationService(this.users, audit);
        this.reports = new ReportService(new SqliteReportRepository(database),
                this.authentication, audit);
        this.incidents = new IncidentService(incidentStore,
                new SqliteFieldUpdateRepository(database),
                this.authentication, audit);
        this.assessments = new AssessmentService(
                new SqliteAssessmentRepository(database), incidentStore,
                this.authentication, audit);
        this.coordination = new CoordinationService(
                new SqliteAssignmentRepository(database), incidentStore,
                new SimulatedPartnerGateway(), this.authentication, audit);
    }

    /**
     * Writes the sample database.
     *
     * @param file a file that does not exist yet
     * @throws IllegalArgumentException if the file already exists
     */
    public static void write(Path file) {
        if (Files.exists(file)) {
            throw new IllegalArgumentException(file + " already exists; "
                    + "choose a new file name.");
        }
        Database database = new Database(file.toString());
        try {
            new SampleA2Data(database).fill();
        } finally {
            database.close();
        }
    }

    /**
     * Writes the sample database named on the command line.
     *
     * @param args the new file's path
     */
    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: SampleA2Data <new-file.db>");
            System.exit(2);
        }
        write(Path.of(args[0]));
        System.out.println("Wrote sample Assessment 2 data to " + args[0]);
    }

    /**
     * Describes the generator.
     *
     * @return what it writes
     */
    @Override
    public String toString() {
        return "SampleA2Data: DRS-Initial SQLite sample";
    }

    private void fill() {
        new DemoDataSeeder(this.authentication, this.users).seedIfEmpty();
        User coordinator = user("coordinator");
        User liaison = user("liaison");
        User responder = user("responder");
        LocalDateTime now = LocalDateTime.now();

        DisasterReport fire = submit(DisasterType.FIRE, "2 Dock Road",
                "Laverton", -37.8636, 144.7700, now.minusHours(6),
                "Thick black smoke and flames from a warehouse roof.",
                "Priya Shah", "0400 111 222");
        DisasterReport flood = submit(DisasterType.FLOOD, "15 River Street",
                "Maribyrnong", -37.7740, 144.8890, now.minusHours(5),
                "Water over the road near the bridge.",
                "Tom Nguyen", "tom.nguyen@example.com");
        DisasterReport storm = submit(DisasterType.STORM, "8 Pier Street",
                "Altona", -37.8690, 144.8300, now.minusHours(4),
                "Large tree down across both lanes.", null, null);
        DisasterReport duplicate = submit(DisasterType.FIRE, "4 Dock Road",
                "Laverton", -37.8640, 144.7705, now.minusHours(6),
                "Smoke near the warehouses.", null, null);

        for (DisasterReport report : new DisasterReport[] {fire, flood,
            storm}) {
            check(this.reports.decideReport(coordinator,
                    report.getReportId(), ReportStatus.VALIDATED, null));
        }
        check(this.reports.decideReport(coordinator,
                duplicate.getReportId(), ReportStatus.REJECTED,
                "Duplicate of the Dock Road warehouse fire."));

        Incident fireIncident = createIncident(coordinator, fire);
        Incident floodIncident = createIncident(coordinator, flood);
        Incident stormIncident = createIncident(coordinator, storm);
        assess(coordinator, fireIncident, 5, 4, 4, 4, Priority.P1);
        assess(coordinator, floodIncident, 3, 3, 3, 4, Priority.P2);
        assess(coordinator, stormIncident, 1, 1, 2, 5, null);

        DepartmentAssignment assignment = check(this.coordination
                .createAssignment(coordinator, fireIncident.getIncidentId(),
                        Department.FIRE_AND_EMERGENCY,
                        "Suppress the fire and secure the gas cylinders",
                        "Immediate"));
        check(this.coordination.respondToAssignment(liaison,
                assignment.getAssignmentId(), AssignmentStatus.ACCEPTED,
                null, now.plusMinutes(12)));
        check(this.coordination.allocateResource(liaison,
                new ResourceAllocation(null, assignment.getAssignmentId(),
                        "Pumper 12", Department.FIRE_AND_EMERGENCY, 1,
                        now.minusMinutes(30), now.plusHours(4))));

        fieldUpdate(responder, fireIncident, now.minusHours(5),
                "Crews on scene, fire contained to the north wing.");
        fieldUpdate(responder, fireIncident, now.minusHours(3),
                "Fire under control; monitoring hot spots.");
        fieldUpdate(responder, floodIncident, now.minusHours(4),
                "River Street closed between Hall and Pier.");
    }

    private User user(String username) {
        return this.users.findByUsername(username).orElseThrow();
    }

    private DisasterReport submit(DisasterType type, String address,
            String suburb, double latitude, double longitude,
            LocalDateTime observed, String description, String name,
            String contact) {
        DisasterReport report = new DisasterReport(null, type,
                new Location(address, suburb, latitude, longitude), observed,
                description);
        report.setReporterName(name);
        report.setReporterContact(contact);
        return check(this.reports.submitReport(report));
    }

    private Incident createIncident(User coordinator,
            DisasterReport report) {
        return check(this.incidents.createFromReport(coordinator,
                this.reports.findById(report.getReportId()).orElseThrow()));
    }

    private void assess(User coordinator, Incident incident, int life,
            int scale, int infrastructure, int confidence,
            Priority override) {
        Assessment scored = check(this.assessments.score(coordinator,
                new Assessment(incident.getIncidentId(), life, scale,
                        infrastructure, confidence)));
        Priority confirmed = override == null
                ? scored.getRecommendedPriority() : override;
        check(this.assessments.confirmPriority(coordinator, scored,
                confirmed, confirmed == scored.getRecommendedPriority()
                        ? null : "Coordinator judgement on site risk"));
    }

    private void fieldUpdate(User responder, Incident incident,
            LocalDateTime at, String note) {
        FieldUpdate update = new FieldUpdate(null, incident.getIncidentId(),
                responder.getUsername(), at, note);
        check(this.incidents.addFieldUpdate(responder, update));
    }

    private static <T> T check(ServiceResult<T> result) {
        if (result.isFailure()) {
            throw new IllegalStateException("Sample data step failed: "
                    + result.getMessage());
        }
        return result.getValue();
    }
}
