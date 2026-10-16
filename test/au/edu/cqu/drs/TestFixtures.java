package au.edu.cqu.drs;

import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.repository.memory.InMemoryAssessmentRepository;
import au.edu.cqu.drs.repository.memory.InMemoryAssignmentRepository;
import au.edu.cqu.drs.repository.memory.InMemoryAuditRepository;
import au.edu.cqu.drs.repository.memory.InMemoryFieldUpdateRepository;
import au.edu.cqu.drs.repository.memory.InMemoryIncidentRepository;
import au.edu.cqu.drs.repository.memory.InMemoryReportRepository;
import au.edu.cqu.drs.repository.memory.InMemoryUserRepository;
import au.edu.cqu.drs.repository.memory.SimulatedPartnerGateway;
import au.edu.cqu.drs.service.AssessmentService;
import au.edu.cqu.drs.service.AuditService;
import au.edu.cqu.drs.service.AuthenticationService;
import au.edu.cqu.drs.service.CoordinationService;
import au.edu.cqu.drs.service.DuplicateDetectionService;
import au.edu.cqu.drs.service.EscalationService;
import au.edu.cqu.drs.service.IncidentService;
import au.edu.cqu.drs.service.ReportService;

import java.time.LocalDateTime;

/**
 * Shared test fixture. Every service is built on in-memory repositories, so the
 * whole suite runs without JavaFX and without a database file. That is only
 * possible because the services depend on repository interfaces, which is the
 * design decision Assessment 1 recorded as UR-09.
 */
public class TestFixtures {

    /** Password used by every account created for testing. */
    public static final String TEST_PASSWORD = "Password1";

    public final InMemoryUserRepository userRepository =
            new InMemoryUserRepository();
    public final InMemoryReportRepository reportRepository =
            new InMemoryReportRepository();
    public final InMemoryIncidentRepository incidentRepository =
            new InMemoryIncidentRepository();
    public final InMemoryAssessmentRepository assessmentRepository =
            new InMemoryAssessmentRepository();
    public final InMemoryAssignmentRepository assignmentRepository =
            new InMemoryAssignmentRepository();
    public final InMemoryFieldUpdateRepository fieldUpdateRepository =
            new InMemoryFieldUpdateRepository();
    public final InMemoryAuditRepository auditRepository =
            new InMemoryAuditRepository();
    public final SimulatedPartnerGateway partnerGateway =
            new SimulatedPartnerGateway();

    public final AuditService auditService = new AuditService(auditRepository);
    public final AuthenticationService authenticationService =
            new AuthenticationService(userRepository, auditService);
    public final ReportService reportService =
            new ReportService(reportRepository, authenticationService,
                              auditService);
    public final IncidentService incidentService = new IncidentService(
            incidentRepository, fieldUpdateRepository, authenticationService,
                    auditService);
    public final AssessmentService assessmentService = new AssessmentService(
            assessmentRepository, incidentRepository, authenticationService,
                    auditService);
    public final CoordinationService coordinationService =
            new CoordinationService(
            assignmentRepository, incidentRepository, partnerGateway,
            authenticationService, auditService);
    public final EscalationService escalationService =
            new EscalationService(incidentService);
    public final DuplicateDetectionService duplicateDetectionService =
            new DuplicateDetectionService(reportRepository);

    /**
     * Creates an active account with the standard test password.
     *
     * @param username the account name
     * @param role     the role to grant
     * @return the saved user
     */
    public User createUser(String username, UserRole role) {
        return authenticationService
                .register(username, username + " (test)", role, TEST_PASSWORD)
                .getValue();
    }

    /**
     * Creates a valid report that the report service will accept.
     *
     * @param type       the disaster type
     * @param suburb     the suburb it was observed in
     * @param latitude   the latitude
     * @param longitude  the longitude
     * @param observedAt when it was observed
     * @return the report, not yet saved
     */
    public DisasterReport validReport(DisasterType type, String suburb,
                                      double latitude, double longitude,
                                      LocalDateTime observedAt) {
        DisasterReport report = new DisasterReport();
        report.setDisasterType(type);
        report.setLocation(new Location("1 Test Street", suburb, latitude,
                longitude));
        report.setObservedAt(observedAt);
        report.setDescription("Smoke and flames visible from the street.");
        return report;
    }

    /**
     * Describes this fixture.
     *
     * @return a short description of this object
     */
    @Override
    public String toString() {
        return "TestFixtures: services built on in-memory repositories for "
                + "tests";
    }
}
