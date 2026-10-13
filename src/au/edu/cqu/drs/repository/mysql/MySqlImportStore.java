package au.edu.cqu.drs.repository.mysql;

import au.edu.cqu.drs.model.Assessment;
import au.edu.cqu.drs.model.AuditEvent;
import au.edu.cqu.drs.model.DepartmentAssignment;
import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.FieldUpdate;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.ResourceAllocation;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.repository.mysql.MySqlSupport.CodeKey;
import au.edu.cqu.drs.security.FieldCipher;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Set;

/**
 * Adds records from DRS-Initial (Assessment 2) to the MySQL database, for
 * the server's import commands (spec 2.1).
 *
 * <p>Every record keeps its original code, for example INC-0003, so links
 * between records still hold. A record whose code is already stored is
 * skipped rather than overwritten, which makes the import safe to run
 * twice and means it never replaces data entered on the new system. Run it
 * on a new database, before the system is used, so the codes cannot
 * clash.</p>
 *
 * <p>Two kinds of record need special handling:</p>
 * <ul>
 *   <li>a user whose user name already exists is skipped, because the
 *       server creates the demonstration accounts itself;</li>
 *   <li>an audit event gets a new code, because the server has already
 *       written its own events; the original code is kept at the start of
 *       its detail, for example "[A2 AUD-00012]", and that is how a second
 *       run recognises it. A public report's actor is recorded as
 *       "public", never the reporter's name.</li>
 * </ul>
 */
public class MySqlImportStore {

    /** Start of the detail of every imported audit event. */
    public static final String AUDIT_PREFIX = "[A2 ";

    /** Actions a member of the public can take, so the actor may be a
     *  reporter's name rather than a user name. */
    private static final Set<String> PUBLIC_ACTIONS = Set.of(
            "SUBMIT_REPORT");

    private final MySqlDatabase database;
    private final MySqlUserRepository users;
    private final MySqlReportRepository reports;
    private final MySqlIncidentRepository incidents;
    private final MySqlAssessmentRepository assessments;
    private final MySqlAssignmentRepository assignments;
    private final MySqlFieldUpdateRepository fieldUpdates;
    private final MySqlAuditRepository audit;

    /**
     * Creates the store.
     *
     * @param database the pooled MySQL database
     * @param cipher   encrypts reporter details, as the live server does
     */
    public MySqlImportStore(MySqlDatabase database, FieldCipher cipher) {
        this.database = database;
        this.users = new MySqlUserRepository(database);
        this.reports = new MySqlReportRepository(database, cipher);
        this.incidents = new MySqlIncidentRepository(database);
        this.assessments = new MySqlAssessmentRepository(database);
        this.assignments = new MySqlAssignmentRepository(database);
        this.fieldUpdates = new MySqlFieldUpdateRepository(database);
        this.audit = new MySqlAuditRepository(database);
    }

    /**
     * Adds a staff account unless its user name is already taken.
     *
     * @param user the account, with its salt and hash
     * @return true if it was added, false if it was already there
     */
    public boolean importUser(User user) {
        if (this.users.findByUsername(user.getUsername()).isPresent()) {
            return false;
        }
        if (user.getUserId() != null
                && exists(CodeKey.USER, user.getUserId())) {
            // The code belongs to another account here; take a new one.
            user.setUserId(null);
        }
        this.users.save(user);
        return true;
    }

    /**
     * Adds a report unless its code is already stored. The reporter's name
     * and contact are encrypted on the way in.
     *
     * @param report the report
     * @return true if it was added
     */
    public boolean importReport(DisasterReport report) {
        if (exists(CodeKey.REPORT, report.getReportId())) {
            return false;
        }
        this.reports.save(report);
        return true;
    }

    /**
     * Adds an incident and its links to its reports, unless its code is
     * already stored. Import the reports first.
     *
     * @param incident the incident
     * @return true if it was added
     */
    public boolean importIncident(Incident incident) {
        if (exists(CodeKey.INCIDENT, incident.getIncidentId())) {
            return false;
        }
        this.incidents.save(incident);
        return true;
    }

    /**
     * Adds an assessment unless its code is already stored.
     *
     * @param assessment the assessment
     * @return true if it was added
     */
    public boolean importAssessment(Assessment assessment) {
        if (exists(CodeKey.ASSESSMENT, assessment.getAssessmentId())) {
            return false;
        }
        this.assessments.save(assessment);
        return true;
    }

    /**
     * Adds an assignment and its resource allocations, unless the
     * assignment's code is already stored.
     *
     * @param assignment the assignment, with its allocations
     * @return true if it was added
     */
    public boolean importAssignment(DepartmentAssignment assignment) {
        if (exists(CodeKey.ASSIGNMENT, assignment.getAssignmentId())) {
            return false;
        }
        this.assignments.save(assignment);
        for (ResourceAllocation allocation : assignment.getAllocations()) {
            if (!exists(CodeKey.ALLOCATION, allocation.getAllocationId())) {
                allocation.setAssignmentId(assignment.getAssignmentId());
                this.assignments.saveAllocation(allocation);
            }
        }
        return true;
    }

    /**
     * Adds a field update unless its code is already stored.
     *
     * @param fieldUpdate the update
     * @return true if it was added
     */
    public boolean importFieldUpdate(FieldUpdate fieldUpdate) {
        if (exists(CodeKey.FIELD_UPDATE, fieldUpdate.getUpdateId())) {
            return false;
        }
        this.fieldUpdates.save(fieldUpdate);
        return true;
    }

    /**
     * Appends an Assessment 2 audit event to the hash chain under a new
     * code, unless it was imported before.
     *
     * @param event the event, with its original code
     * @return true if it was added
     */
    public boolean importAuditEvent(AuditEvent event) {
        String tag = AUDIT_PREFIX + event.getEventId() + "]";
        if (auditTagExists(tag)) {
            return false;
        }
        AuditEvent copy = new AuditEvent(publicActor(event),
                event.getAction(), event.getEntityType(), event.getEntityId(),
                tag + (event.getDetail() == null ? ""
                        : " " + event.getDetail()),
                event.getOccurredAt());
        this.audit.save(copy);
        return true;
    }

    /**
     * Describes the store.
     *
     * @return the database it writes to
     */
    @Override
    public String toString() {
        return "MySqlImportStore: " + this.database;
    }

    /**
     * DRS-Initial recorded a public report's actor as the reporter's name;
     * that name is personal data, so it becomes "public" here.
     */
    private String publicActor(AuditEvent event) {
        if (PUBLIC_ACTIONS.contains(event.getAction())
                && (event.getActor() == null || this.users
                        .findByUsername(event.getActor()).isEmpty())) {
            return "public";
        }
        return event.getActor();
    }

    private boolean exists(CodeKey key, String code) {
        if (code == null) {
            return false;
        }
        // The table and column come from CodeKey constants, never input.
        String sql = "SELECT 1 FROM " + key.table() + " WHERE "
                + key.column() + " = ?";
        return query(sql, code);
    }

    private boolean auditTagExists(String tag) {
        return query("SELECT 1 FROM audit_events WHERE detail = ? "
                + "OR detail LIKE CONCAT(?, ' %')", tag, tag);
    }

    private boolean query(String sql, String... parameters) {
        try {
            return this.database.withConnection(connection -> {
                try (PreparedStatement statement =
                        connection.prepareStatement(sql)) {
                    for (int i = 0; i < parameters.length; i++) {
                        statement.setString(i + 1, parameters[i]);
                    }
                    try (ResultSet rs = statement.executeQuery()) {
                        return rs.next();
                    }
                }
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Could not check the database "
                    + "before importing.", e);
        }
    }
}
