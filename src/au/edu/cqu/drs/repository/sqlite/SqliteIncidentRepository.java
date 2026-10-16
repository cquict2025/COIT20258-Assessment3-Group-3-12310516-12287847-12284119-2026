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

    /**
     * Creates a SQLite incident repository.
     *
     * @param database the database
     */
    public SqliteIncidentRepository(Database database) {
        this.database = database;
    }

    /**
     * Inserts an incident, or updates the stored row with the same
     * identifier, then records its links to source reports. A new incident
     * is given the next INC- identifier.
     *
     * @param incident the incident to save
     * @return the same incident, with its identifier set
     * @throws IllegalStateException if the rows cannot be written
     */
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

    /**
     * Finds an incident by its identifier, with its source reports and
     * assignments.
     *
     * @param incidentId the incident identifier
     * @return the incident, or empty if there is none
     * @throws IllegalStateException if the rows cannot be read
     */
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

    /**
     * Lists every incident, ordered by identifier.
     *
     * @return all incidents
     */
    @Override
    public List<Incident> findAll() {
        return query("SELECT * FROM incidents ORDER BY incident_id", null);
    }

    /**
     * Finds the incidents that have a given status, ordered by identifier.
     *
     * @param status the status to match
     * @return the incidents with that status
     */
    @Override
    public List<Incident> findByStatus(IncidentStatus status) {
        return query("SELECT * FROM incidents WHERE status = ? ORDER BY "
                + "incident_id",
                     
                     
                     
                     status.name());
    }

    /**
     * Records the links between an incident and its source reports, as one
     * batch. Links that already exist are ignored.
     *
     * @param connection the live connection
     * @param incident the incident whose links are saved
     * @throws SQLException if the links cannot be written
     */
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

    /**
     * Runs a query against the incidents table and maps every row.
     *
     * @param sql the query to run
     * @param parameter the single query parameter, or null if there is none
     * @return the incidents found
     * @throws IllegalStateException if the rows cannot be read
     */
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

    /**
     * Builds an incident from the current row of a result set and loads
     * its source reports and assignments.
     *
     * @param rs the result set, positioned on the row to read
     * @return the incident read from the row
     * @throws SQLException if a column or linked row cannot be read
     */
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

    /**
     * Adds the identifiers of the reports linked to an incident.
     *
     * @param incident the incident to fill in
     * @throws SQLException if the links cannot be read
     */
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
     *
     * @param incident the incident to fill in
     * @throws SQLException if the assignments cannot be read
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

    /**
     * Describes the repository.
     *
     * @return the database it reads and writes
     */
    @Override
    public String toString() {
        return "SqliteIncidentRepository: " + this.database;
    }
}
