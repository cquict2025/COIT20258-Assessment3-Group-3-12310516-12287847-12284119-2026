package au.edu.cqu.drs.repository.mysql;

import au.edu.cqu.drs.model.AssignmentStatus;
import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.DepartmentAssignment;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.IncidentStatus;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.repository.IncidentRepository;
import au.edu.cqu.drs.repository.mysql.MySqlSupport.CodeKey;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MySQL adapter for {@link IncidentRepository}, including the links to the
 * source reports. An incident and its links are saved in one transaction.
 */
public class MySqlIncidentRepository implements IncidentRepository {

    private static final List<String> COLUMNS = List.of(
            "title", "disaster_type", "address_line", "suburb", "latitude",
            "longitude", "status", "priority", "created_at",
            "first_response_at", "closed_at", "closure_outcome",
            "residual_risks", "version");

    private static final String LINK_REPORT = """
            INSERT INTO incident_reports (incident_id, report_id)
            VALUES (?,?) AS new
            ON DUPLICATE KEY UPDATE report_id = new.report_id""";

    private final MySqlDatabase database;

    /**
     * Creates the repository.
     *
     * @param database the pooled MySQL database
     */
    public MySqlIncidentRepository(MySqlDatabase database) {
        this.database = database;
    }

    /**
     * Inserts a new incident, giving it a code, or updates an existing one,
     * together with its source report links.
     *
     * @param incident the incident
     * @return the same incident, with its code set
     */
    @Override
    public Incident save(Incident incident) {
        try {
            String code = this.database.inTransaction(connection -> {
                String saved = MySqlSupport.save(connection,
                        CodeKey.INCIDENT, COLUMNS, incident.getIncidentId(),
                        (statement, first) ->
                                bind(statement, first, incident));
                linkReports(connection, saved, incident.getSourceReportIds());
                return saved;
            });
            incident.setIncidentId(code);
            return incident;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save the incident.",
                    e);
        }
    }

    /**
     * Finds an incident by code.
     *
     * @param incidentId the code, for example INC-0001
     * @return the incident, if there is one
     */
    @Override
    public Optional<Incident> findById(String incidentId) {
        List<Incident> found = query(
                "SELECT * FROM incidents WHERE incident_id = ?", incidentId);
        return found.isEmpty() ? Optional.empty()
                : Optional.of(found.get(0));
    }

    /**
     * Lists every incident.
     *
     * @return the incidents in code order
     */
    @Override
    public List<Incident> findAll() {
        return query("SELECT * FROM incidents ORDER BY id", null);
    }

    /**
     * Lists the incidents in one state.
     *
     * @param status the state
     * @return the matching incidents in code order
     */
    @Override
    public List<Incident> findByStatus(IncidentStatus status) {
        return query("SELECT * FROM incidents WHERE status = ? ORDER BY id",
                status.name());
    }

    /**
     * Describes the repository.
     *
     * @return the tables it uses
     */
    @Override
    public String toString() {
        return "MySqlIncidentRepository: incidents, incident_reports";
    }

    private void linkReports(Connection connection, String incidentId,
            List<String> reportIds) throws SQLException {
        if (reportIds.isEmpty()) {
            return;
        }
        try (PreparedStatement statement =
                connection.prepareStatement(LINK_REPORT)) {
            for (String reportId : reportIds) {
                statement.setString(1, incidentId);
                statement.setString(2, reportId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private List<Incident> query(String sql, String parameter) {
        try {
            return this.database.withConnection(connection -> {
                List<Incident> incidents = new ArrayList<>();
                try (PreparedStatement statement =
                        connection.prepareStatement(sql)) {
                    if (parameter != null) {
                        statement.setString(1, parameter);
                    }
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            incidents.add(map(rs));
                        }
                    }
                }
                for (Incident incident : incidents) {
                    loadSourceReports(connection, incident);
                    loadAssignments(connection, incident);
                }
                return incidents;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read incidents.", e);
        }
    }

    private void bind(PreparedStatement statement, int first,
            Incident incident) throws SQLException {
        Location location = incident.getLocation() == null ? new Location()
                : incident.getLocation();
        int i = first;
        statement.setString(i++, incident.getTitle());
        statement.setString(i++,
                MySqlSupport.fromEnum(incident.getDisasterType()));
        statement.setString(i++, location.getAddressLine());
        statement.setString(i++, location.getSuburb());
        statement.setDouble(i++, location.getLatitude());
        statement.setDouble(i++, location.getLongitude());
        statement.setString(i++, MySqlSupport.fromEnum(incident.getStatus()));
        statement.setString(i++,
                MySqlSupport.fromEnum(incident.getPriority()));
        MySqlSupport.setDateTime(statement, i++, incident.getCreatedAt());
        MySqlSupport.setDateTime(statement, i++,
                incident.getFirstResponseAt());
        MySqlSupport.setDateTime(statement, i++, incident.getClosedAt());
        statement.setString(i++, incident.getClosureOutcome());
        statement.setString(i++, incident.getResidualRisks());
        statement.setInt(i, incident.getVersion());
    }

    private Incident map(ResultSet rs) throws SQLException {
        Incident incident = new Incident();
        incident.setIncidentId(rs.getString("incident_id"));
        incident.setTitle(rs.getString("title"));
        incident.setDisasterType(MySqlSupport.toEnum(DisasterType.class,
                rs.getString("disaster_type")));
        incident.setLocation(new Location(rs.getString("address_line"),
                rs.getString("suburb"), rs.getDouble("latitude"),
                rs.getDouble("longitude")));
        incident.setStatus(MySqlSupport.toEnum(IncidentStatus.class,
                rs.getString("status")));
        incident.setPriority(MySqlSupport.toEnum(Priority.class,
                rs.getString("priority")));
        incident.setCreatedAt(MySqlSupport.getDateTime(rs, "created_at"));
        incident.setFirstResponseAt(MySqlSupport.getDateTime(rs,
                "first_response_at"));
        incident.setClosedAt(MySqlSupport.getDateTime(rs, "closed_at"));
        incident.setClosureOutcome(rs.getString("closure_outcome"));
        incident.setResidualRisks(rs.getString("residual_risks"));
        incident.setVersion(rs.getInt("version"));
        return incident;
    }

    private void loadSourceReports(Connection connection, Incident incident)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT report_id FROM incident_reports WHERE incident_id = ?"
                        + " ORDER BY id")) {
            statement.setString(1, incident.getIncidentId());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    incident.addSourceReportId(rs.getString("report_id"));
                }
            }
        }
    }

    /**
     * Loads assignment status only, which is all the incident needs to
     * answer {@code hasUnresolvedAssignments()} before closure (UC-13).
     */
    private void loadAssignments(Connection connection, Incident incident)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT assignment_id, department, requested_action, "
                        + "urgency, status FROM assignments "
                        + "WHERE incident_id = ? ORDER BY id")) {
            statement.setString(1, incident.getIncidentId());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    DepartmentAssignment assignment =
                            new DepartmentAssignment();
                    assignment.setAssignmentId(rs.getString("assignment_id"));
                    assignment.setIncidentId(incident.getIncidentId());
                    assignment.setDepartment(MySqlSupport.toEnum(
                            Department.class, rs.getString("department")));
                    assignment.setRequestedAction(
                            rs.getString("requested_action"));
                    assignment.setUrgency(rs.getString("urgency"));
                    assignment.setStatus(MySqlSupport.toEnum(
                            AssignmentStatus.class, rs.getString("status")));
                    incident.addAssignment(assignment);
                }
            }
        }
    }
}
