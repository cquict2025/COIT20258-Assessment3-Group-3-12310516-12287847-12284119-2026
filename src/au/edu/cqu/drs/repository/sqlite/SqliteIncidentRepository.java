package au.edu.cqu.drs.repository.sqlite;

import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.IncidentStatus;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.repository.IncidentRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * SQLite adapter for {@link IncidentRepository}, including the source-report
 * links.
 */
public class SqliteIncidentRepository implements IncidentRepository {

    private final Database database;

    public SqliteIncidentRepository(Database database) {
        this.database = database;
    }

    @Override
    public Incident save(Incident incident) {
        Connection connection = this.database.getConnection();
        if (incident.getIncidentId() == null) {
            incident.setIncidentId(
                    SqlSupport.nextId(connection, "incidents", "incident_id",
                                      "INC", 4));
        }
        String sql = """
                INSERT INTO incidents (
                    incident_id, title, disaster_type, address_line, suburb,
                    latitude, longitude, status, priority, created_at,
                    first_response_at, closed_at, closure_outcome,
                    residual_risks, version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(incident_id) DO UPDATE SET
                    title = excluded.title,
                    disaster_type = excluded.disaster_type,
                    address_line = excluded.address_line,
                    suburb = excluded.suburb,
                    latitude = excluded.latitude,
                    longitude = excluded.longitude,
                    status = excluded.status,
                    priority = excluded.priority,
                    created_at = excluded.created_at,
                    first_response_at = excluded.first_response_at,
                    closed_at = excluded.closed_at,
                    closure_outcome = excluded.closure_outcome,
                    residual_risks = excluded.residual_risks,
                    version = excluded.version""";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Location location = incident.getLocation() == null
                    ? new Location() : incident.getLocation();
            statement.setString(1, incident.getIncidentId());
            statement.setString(2, incident.getTitle());
            statement.setString(3,
                                SqlSupport.fromEnum(incident
                                        .getDisasterType()));
            statement.setString(4, location.getAddressLine());
            statement.setString(5, location.getSuburb());
            statement.setDouble(6, location.getLatitude());
            statement.setDouble(7, location.getLongitude());
            statement.setString(8, SqlSupport.fromEnum(incident.getStatus()));
            statement.setString(9, SqlSupport.fromEnum(incident.getPriority()));
            statement.setString(10, SqlSupport.toText(incident.getCreatedAt()));
            statement.setString(11,
                                SqlSupport.toText(incident
                                        .getFirstResponseAt()));
            statement.setString(12, SqlSupport.toText(incident.getClosedAt()));
            statement.setString(13, incident.getClosureOutcome());
            statement.setString(14, incident.getResidualRisks());
            statement.setInt(15, incident.getVersion());
            statement.executeUpdate();
            saveSourceReports(connection, incident);
            return incident;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save the incident.", e);
        }
    }

    @Override
    public Optional<Incident> findById(String incidentId) {
        try (PreparedStatement statement = this.database.getConnection()
                .prepareStatement("SELECT * FROM incidents WHERE incident_id = "
                        + "?")) {
            statement.setString(1, incidentId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the incident.", e);
        }
    }

    @Override
    public List<Incident> findAll() {
        return query("SELECT * FROM incidents ORDER BY incident_id", null);
    }

    @Override
    public List<Incident> findByStatus(IncidentStatus status) {
        return query("SELECT * FROM incidents WHERE status = ? ORDER BY "
                + "incident_id",
                     
                     
                     
                     status.name());
    }

    private void saveSourceReports(Connection connection,
                                   Incident incident) throws SQLException {
        String sql = "INSERT OR IGNORE INTO incident_reports (incident_id, "
                + "report_id) VALUES (?,?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (String reportId : incident.getSourceReportIds()) {
                statement.setString(1, incident.getIncidentId());
                statement.setString(2, reportId);
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private List<Incident> query(String sql, String parameter) {
        List<Incident> incidents = new ArrayList<>();
        try (PreparedStatement statement = this.database.getConnection()
                .prepareStatement(sql)) {
            if (parameter != null) {
                statement.setString(1, parameter);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    incidents.add(map(rs));
                }
            }
            return incidents;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read incidents.", e);
        }
    }

    private Incident map(ResultSet rs) throws SQLException {
        Incident incident = new Incident();
        incident.setIncidentId(rs.getString("incident_id"));
        incident.setTitle(rs.getString("title"));
        incident.setDisasterType(SqlSupport
                .toEnum(DisasterType.class, rs.getString("disaster_type")));
        incident.setLocation(new Location(rs.getString("address_line"),
                rs.getString("suburb"),
                rs.getDouble("latitude"), rs.getDouble("longitude")));
        incident.setStatus(SqlSupport
                .toEnum(IncidentStatus.class, rs.getString("status")));
        incident.setPriority(SqlSupport
                .toEnum(Priority.class, rs.getString("priority")));
        incident.setCreatedAt(SqlSupport
                .toDateTime(rs.getString("created_at")));
        incident.setFirstResponseAt(SqlSupport
                .toDateTime(rs.getString("first_response_at")));
        incident.setClosedAt(SqlSupport.toDateTime(rs.getString("closed_at")));
        incident.setClosureOutcome(rs.getString("closure_outcome"));
        incident.setResidualRisks(rs.getString("residual_risks"));
        incident.setVersion(rs.getInt("version"));
        loadSourceReports(incident);
        loadAssignments(incident);
        return incident;
    }

    private void loadSourceReports(Incident incident) throws SQLException {
        try (PreparedStatement statement = this.database.getConnection()
                .prepareStatement(
                "SELECT report_id FROM incident_reports WHERE incident_id = ?"))
                        {
            statement.setString(1, incident.getIncidentId());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    incident.addSourceReportId(rs.getString("report_id"));
                }
            }
        }
    }

    /**
     * Loads assignment status only, which is all the incident needs in order to
     * answer {@code hasUnresolvedAssignments()} before closure (UC-13).
     */
    private void loadAssignments(Incident incident) throws SQLException {
        try (PreparedStatement statement = this.database.getConnection()
                .prepareStatement(
                "SELECT assignment_id, department, requested_action, urgency, "
                        + "status "
                        + "FROM assignments WHERE incident_id = ?")) {
            statement.setString(1, incident.getIncidentId());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    au.edu.cqu.drs.model.DepartmentAssignment assignment =
                            new au.edu.cqu.drs.model.DepartmentAssignment();
                    assignment.setAssignmentId(rs.getString("assignment_id"));
                    assignment.setIncidentId(incident.getIncidentId());
                    assignment.setDepartment(SqlSupport.toEnum(
                            au.edu.cqu.drs.model.Department.class, rs
                                    .getString("department")));
                    assignment.setRequestedAction(rs
                            .getString("requested_action"));
                    assignment.setUrgency(rs.getString("urgency"));
                    assignment.setStatus(SqlSupport.toEnum(
                            au.edu.cqu.drs.model.AssignmentStatus.class, rs
                                    .getString("status")));
                    incident.addAssignment(assignment);
                }
            }
        }
    }
}
