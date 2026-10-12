package au.edu.cqu.drs.server;

import au.edu.cqu.drs.DemoDataSeeder;
import au.edu.cqu.drs.repository.AssessmentRepository;
import au.edu.cqu.drs.repository.AssignmentRepository;
import au.edu.cqu.drs.repository.AuditRepository;
import au.edu.cqu.drs.repository.FieldUpdateRepository;
import au.edu.cqu.drs.repository.IncidentRepository;
import au.edu.cqu.drs.repository.ReportRepository;
import au.edu.cqu.drs.repository.UserRepository;
import au.edu.cqu.drs.repository.memory.InMemoryAssessmentRepository;
import au.edu.cqu.drs.repository.memory.InMemoryAssignmentRepository;
import au.edu.cqu.drs.repository.memory.InMemoryAuditRepository;
import au.edu.cqu.drs.repository.memory.InMemoryFieldUpdateRepository;
import au.edu.cqu.drs.repository.memory.InMemoryIncidentRepository;
import au.edu.cqu.drs.repository.memory.InMemoryReportRepository;
import au.edu.cqu.drs.repository.memory.InMemoryUserRepository;
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

import java.time.Clock;

/**
 * Wires the repositories chosen in the {@link ServerConfig} to the existing
 * services, exactly as {@code AppContext} does for the single-user client.
 *
 * <p>The services are unchanged: they depend only on the repository
 * interfaces, so the server can run them on SQLite, in memory, or on MySQL
 * once those adapters are merged. Modules that take a {@code ServerContext}
 * in their constructor receive this object.</p>
 */
public class ServerContext implements AutoCloseable {

    private final ServerConfig config;
    private final Clock clock;
    private final Database database;
    private final UserRepository userRepository;
    private final SimulatedPartnerGateway partnerGateway;
    private final AuditService auditService;
    private final AuthenticationService authenticationService;
    private final ReportService reportService;
    private final IncidentService incidentService;
    private final AssessmentService assessmentService;
    private final CoordinationService coordinationService;
    private final EscalationService escalationService;
    private final DuplicateDetectionService duplicateDetectionService;

    /**
     * Builds the services on the configured storage and creates the
     * demonstration accounts if there are no users yet.
     *
     * @param config the server settings
     * @param clock  the clock used for escalation checks
     * @throws IllegalStateException if the storage is not available
     */
    public ServerContext(ServerConfig config, Clock clock) {
        this.config = config;
        this.clock = clock;
        this.partnerGateway = new SimulatedPartnerGateway();
        ReportRepository reports;
        IncidentRepository incidents;
        AssessmentRepository assessments;
        AssignmentRepository assignments;
        FieldUpdateRepository fieldUpdates;
        AuditRepository audit;
        switch (config.getBackend()) {
            case SQLITE:
                this.database = new Database(config.getDatabaseFile());
                this.userRepository = new SqliteUserRepository(this.database);
                reports = new SqliteReportRepository(this.database);
                incidents = new SqliteIncidentRepository(this.database);
                assessments = new SqliteAssessmentRepository(this.database);
                assignments = new SqliteAssignmentRepository(this.database);
                fieldUpdates = new SqliteFieldUpdateRepository(this.database);
                audit = new SqliteAuditRepository(this.database);
                break;
            case MEMORY:
                this.database = null;
                this.userRepository = new InMemoryUserRepository();
                reports = new InMemoryReportRepository();
                incidents = new InMemoryIncidentRepository();
                assessments = new InMemoryAssessmentRepository();
                assignments = new InMemoryAssignmentRepository();
                fieldUpdates = new InMemoryFieldUpdateRepository();
                audit = new InMemoryAuditRepository();
                break;
            default:
                // TODO(W6): switch when Milan's MySQL adapters are merged.
                throw new IllegalStateException("The MySQL storage is not "
                        + "available yet. Set repository=sqlite or memory.");
        }
        this.auditService = new AuditService(audit);
        this.authenticationService =
                new AuthenticationService(this.userRepository,
                        this.auditService);
        this.reportService = new ReportService(reports,
                this.authenticationService, this.auditService);
        this.incidentService = new IncidentService(incidents, fieldUpdates,
                this.authenticationService, this.auditService);
        this.assessmentService = new AssessmentService(assessments,
                incidents, this.authenticationService, this.auditService);
        this.coordinationService = new CoordinationService(assignments,
                incidents, this.partnerGateway, this.authenticationService,
                this.auditService);
        this.escalationService = new EscalationService(this.incidentService);
        this.duplicateDetectionService =
                new DuplicateDetectionService(reports);
        new DemoDataSeeder(this.authenticationService, this.userRepository)
                .seedIfEmpty();
    }

    /**
     * Returns the server settings.
     *
     * @return the settings
     */
    public ServerConfig getConfig() {
        return this.config;
    }

    /**
     * Returns the server clock; time-based rules use it, never the client's.
     *
     * @return the clock
     */
    public Clock getClock() {
        return this.clock;
    }

    /**
     * Returns the user accounts store.
     *
     * @return the user repository
     */
    public UserRepository getUserRepository() {
        return this.userRepository;
    }

    /**
     * Returns the simulated partner interfaces.
     *
     * @return the partner gateway
     */
    public SimulatedPartnerGateway getPartnerGateway() {
        return this.partnerGateway;
    }

    /**
     * Returns the audit service.
     *
     * @return the service
     */
    public AuditService getAuditService() {
        return this.auditService;
    }

    /**
     * Returns the authentication service.
     *
     * @return the service
     */
    public AuthenticationService getAuthenticationService() {
        return this.authenticationService;
    }

    /**
     * Returns the report service.
     *
     * @return the service
     */
    public ReportService getReportService() {
        return this.reportService;
    }

    /**
     * Returns the incident service.
     *
     * @return the service
     */
    public IncidentService getIncidentService() {
        return this.incidentService;
    }

    /**
     * Returns the assessment service.
     *
     * @return the service
     */
    public AssessmentService getAssessmentService() {
        return this.assessmentService;
    }

    /**
     * Returns the coordination service.
     *
     * @return the service
     */
    public CoordinationService getCoordinationService() {
        return this.coordinationService;
    }

    /**
     * Returns the escalation service.
     *
     * @return the service
     */
    public EscalationService getEscalationService() {
        return this.escalationService;
    }

    /**
     * Returns the duplicate detection service.
     *
     * @return the service
     */
    public DuplicateDetectionService getDuplicateDetectionService() {
        return this.duplicateDetectionService;
    }

    /**
     * Closes the database, if there is one.
     */
    @Override
    public void close() {
        if (this.database != null) {
            this.database.close();
        }
    }

    /**
     * Describes the context.
     *
     * @return the storage in use
     */
    @Override
    public String toString() {
        return "ServerContext: " + this.config.getBackend() + " storage";
    }
}
