package au.edu.cqu.drs;

import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.repository.AssessmentRepository;
import au.edu.cqu.drs.repository.AssignmentRepository;
import au.edu.cqu.drs.repository.AuditRepository;
import au.edu.cqu.drs.repository.FieldUpdateRepository;
import au.edu.cqu.drs.repository.IncidentRepository;
import au.edu.cqu.drs.repository.PartnerGateway;
import au.edu.cqu.drs.repository.ReportRepository;
import au.edu.cqu.drs.repository.UserRepository;
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
import au.edu.cqu.drs.service.DuplicateDetectionService;
import au.edu.cqu.drs.service.EscalationService;
import au.edu.cqu.drs.service.IncidentService;
import au.edu.cqu.drs.service.ReportService;

/**
 * Wires the concrete SQLite adapters to the application services and holds the
 * signed-in user for the session.
 *
 * <p>All wiring happens in one place so that the services themselves depend
 * only
 * on the repository interfaces. That is what allows the JUnit suite to build
 * the
 * same services on in-memory doubles without starting JavaFX or touching a
 * database, as Assessment 1 (UR-09) required.</p>
 */
public class AppContext {

    private final Database database;
    private final AuditService auditService;
    private final AuthenticationService authenticationService;
    private final ReportService reportService;
    private final IncidentService incidentService;
    private final AssessmentService assessmentService;
    private final CoordinationService coordinationService;
    private final EscalationService escalationService;
    private final DuplicateDetectionService duplicateDetectionService;
    private final SimulatedPartnerGateway partnerGateway;

    private User currentUser;

    public AppContext(String databaseFile) {
        this.database = new Database(databaseFile);

        UserRepository userRepository = new SqliteUserRepository(this.database);
        ReportRepository reportRepository =
                new SqliteReportRepository(this.database);
        IncidentRepository incidentRepository =
                new SqliteIncidentRepository(this.database);
        AssessmentRepository assessmentRepository =
                new SqliteAssessmentRepository(this.database);
        AssignmentRepository assignmentRepository =
                new SqliteAssignmentRepository(this.database);
        FieldUpdateRepository fieldUpdateRepository =
                new SqliteFieldUpdateRepository(this.database);
        AuditRepository auditRepository = new
                SqliteAuditRepository(this.database);
        this.partnerGateway = new SimulatedPartnerGateway();

        this.auditService = new AuditService(auditRepository);
        this.authenticationService = new AuthenticationService(userRepository,
                this.auditService);
        this.reportService = new ReportService(reportRepository,
                                               this.authenticationService,
                                               this.auditService);
        this.incidentService = new IncidentService(incidentRepository,
                                                   fieldUpdateRepository, 
                this.authenticationService, this.auditService);
        this.assessmentService = new AssessmentService(assessmentRepository,
                                                       incidentRepository, 
                this.authenticationService, this.auditService);
        this.coordinationService = new CoordinationService(assignmentRepository,
                incidentRepository, 
                this.partnerGateway, this.authenticationService,
                        this.auditService);
        this.escalationService = new EscalationService(this.incidentService);
        this.duplicateDetectionService =
                new DuplicateDetectionService(reportRepository);

        new DemoDataSeeder(this.authenticationService,
                userRepository).seedIfEmpty();
    }

    public AuditService getAuditService() {
        return this.auditService;
    }

    public AuthenticationService getAuthenticationService() {
        return this.authenticationService;
    }

    public ReportService getReportService() {
        return this.reportService;
    }

    public IncidentService getIncidentService() {
        return this.incidentService;
    }

    public AssessmentService getAssessmentService() {
        return this.assessmentService;
    }

    public CoordinationService getCoordinationService() {
        return this.coordinationService;
    }

    public EscalationService getEscalationService() {
        return this.escalationService;
    }

    public DuplicateDetectionService getDuplicateDetectionService() {
        return this.duplicateDetectionService;
    }

    /**
     * Exposed so that the coordination screen can demonstrate a partner outage.
     */
    public SimulatedPartnerGateway getPartnerGateway() {
        return this.partnerGateway;
    }

    public User getCurrentUser() {
        return this.currentUser;
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }

    public void shutdown() {
        this.database.close();
    }

    @Override
    public String toString() {
        return "AppContext | signed in as "
                + (this.currentUser == null ? "nobody" :
                        this.currentUser.getUsername());
    }
}
