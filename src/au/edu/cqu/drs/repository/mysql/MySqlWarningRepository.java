package au.edu.cqu.drs.repository.mysql;

import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.warning.EvacuationCentre;
import au.edu.cqu.drs.model.warning.Warning;
import au.edu.cqu.drs.model.warning.WarningAcknowledgement;
import au.edu.cqu.drs.model.warning.WarningLevel;
import au.edu.cqu.drs.model.warning.WarningStatus;
import au.edu.cqu.drs.repository.WarningRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MySQL adapter for {@link WarningRepository}. Each version of a warning is
 * its own row in {@code warnings}; the pair (warning_id, version) is unique,
 * so two coordinators cannot both save version 3 of the same warning.
 */
public class MySqlWarningRepository implements WarningRepository {

    private static final String INSERT_VERSION = """
            INSERT INTO warnings (
                warning_id, version, incident_id, level, affected_area,
                message, recommended_action, evacuation_centre,
                evacuation_capacity, status, issued_by, issued_at, expires_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""";

    private static final String LATEST_VERSIONS = """
            SELECT w.* FROM warnings w
            JOIN (SELECT warning_id, MAX(version) AS newest
                  FROM warnings GROUP BY warning_id) n
              ON w.warning_id = n.warning_id AND w.version = n.newest
            """;

    /** MySQL's error number for a duplicate unique key. */
    private static final int DUPLICATE_KEY = 1062;

    /** New codes are taken one at a time so two cannot be the same. */
    private static final Object CODE_LOCK = new Object();

    private final MySqlDatabase database;

    /**
     * Creates the repository.
     *
     * @param database the pooled MySQL database
     */
    public MySqlWarningRepository(MySqlDatabase database) {
        this.database = database;
    }

    /**
     * Stores a new version, giving a new warning the next code.
     *
     * @param warning the version to store
     * @return the same warning, with its code set
     */
    @Override
    public Warning saveVersion(Warning warning) {
        synchronized (CODE_LOCK) {
            try {
                String code = this.database.inTransaction(connection -> {
                    String id = warning.getWarningId() != null
                            ? warning.getWarningId() : nextCode(connection);
                    try (PreparedStatement statement =
                            connection.prepareStatement(INSERT_VERSION)) {
                        bind(statement, id, warning);
                        statement.executeUpdate();
                    }
                    return id;
                });
                warning.setWarningId(code);
                return warning;
            } catch (SQLException e) {
                if (e.getErrorCode() == DUPLICATE_KEY) {
                    throw new IllegalStateException(warning.getWarningId()
                            + " version " + warning.getVersion()
                            + " is already stored.", e);
                }
                throw new IllegalStateException("Could not save the "
                        + "warning.", e);
            }
        }
    }

    /**
     * Finds the newest version of a warning.
     *
     * @param warningId the code
     * @return the newest version, if any
     */
    @Override
    public Optional<Warning> findLatest(String warningId) {
        List<Warning> found = query("SELECT * FROM warnings "
                + "WHERE warning_id = ? ORDER BY version DESC LIMIT 1",
                warningId);
        return found.isEmpty() ? Optional.empty()
                : Optional.of(found.get(0));
    }

    /**
     * Lists the newest version of every warning.
     *
     * @return one row per warning, oldest warning first
     */
    @Override
    public List<Warning> findLatestVersions() {
        return query(LATEST_VERSIONS + "ORDER BY w.warning_id", null);
    }

    /**
     * Lists every version of a warning.
     *
     * @param warningId the code
     * @return the versions, oldest first
     */
    @Override
    public List<Warning> findHistory(String warningId) {
        return query("SELECT * FROM warnings WHERE warning_id = ? "
                + "ORDER BY version", warningId);
    }

    /**
     * Records an acknowledgement.
     *
     * @param acknowledgement the acknowledgement
     * @return the same acknowledgement
     */
    @Override
    public WarningAcknowledgement saveAcknowledgement(
            WarningAcknowledgement acknowledgement) {
        try {
            this.database.withConnection(connection -> {
                try (PreparedStatement statement = connection
                        .prepareStatement("INSERT INTO "
                                + "warning_acknowledgements (warning_id, "
                                + "department, acknowledged_by, "
                                + "acknowledged_at) VALUES (?,?,?,?)")) {
                    statement.setString(1, acknowledgement.getWarningId());
                    statement.setString(2, MySqlSupport.fromEnum(
                            acknowledgement.getDepartment()));
                    statement.setString(3,
                            acknowledgement.getAcknowledgedBy());
                    MySqlSupport.setDateTime(statement, 4,
                            acknowledgement.getAcknowledgedAt());
                    return statement.executeUpdate();
                }
            });
            return acknowledgement;
        } catch (SQLException e) {
            if (e.getErrorCode() == DUPLICATE_KEY) {
                throw new IllegalStateException(acknowledgement
                        .getDepartment() + " has already acknowledged "
                        + acknowledgement.getWarningId() + ".", e);
            }
            throw new IllegalStateException("Could not save the "
                    + "acknowledgement.", e);
        }
    }

    /**
     * Lists the acknowledgements of a warning.
     *
     * @param warningId the code
     * @return the acknowledgements, earliest first
     */
    @Override
    public List<WarningAcknowledgement> findAcknowledgements(
            String warningId) {
        try {
            return this.database.withConnection(connection ->
                    acknowledgements(connection, warningId));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read "
                    + "acknowledgements.", e);
        }
    }

    /**
     * Describes the repository.
     *
     * @return the tables it uses
     */
    @Override
    public String toString() {
        return "MySqlWarningRepository: warnings, warning_acknowledgements";
    }

    /**
     * Finds the next code, locking the rows read until the transaction
     * ends.
     */
    private String nextCode(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COALESCE(MAX(CAST(SUBSTRING(warning_id, 5) "
                        + "AS UNSIGNED)), 0) FROM warnings FOR UPDATE");
             ResultSet rs = statement.executeQuery()) {
            rs.next();
            return String.format("WRN-%04d", rs.getLong(1) + 1);
        }
    }

    private List<Warning> query(String sql, String parameter) {
        try {
            return this.database.withConnection(connection -> {
                List<Warning> warnings = new ArrayList<>();
                try (PreparedStatement statement =
                        connection.prepareStatement(sql)) {
                    if (parameter != null) {
                        statement.setString(1, parameter);
                    }
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            warnings.add(map(rs));
                        }
                    }
                }
                return warnings;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read warnings.", e);
        }
    }

    private List<WarningAcknowledgement> acknowledgements(
            Connection connection, String warningId) throws SQLException {
        List<WarningAcknowledgement> found = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM warning_acknowledgements WHERE warning_id = ?"
                        + " ORDER BY acknowledged_at, id")) {
            statement.setString(1, warningId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    found.add(new WarningAcknowledgement(
                            rs.getString("warning_id"),
                            MySqlSupport.toEnum(Department.class,
                                    rs.getString("department")),
                            rs.getString("acknowledged_by"),
                            MySqlSupport.getDateTime(rs,
                                    "acknowledged_at")));
                }
            }
        }
        return found;
    }

    private void bind(PreparedStatement statement, String code,
            Warning warning) throws SQLException {
        EvacuationCentre centre = warning.getEvacuationCentre();
        statement.setString(1, code);
        statement.setInt(2, warning.getVersion());
        statement.setString(3, warning.getIncidentId());
        statement.setString(4, MySqlSupport.fromEnum(warning.getLevel()));
        statement.setString(5, warning.getAffectedArea());
        statement.setString(6, warning.getMessage());
        statement.setString(7, warning.getRecommendedAction());
        if (centre == null) {
            statement.setNull(8, Types.VARCHAR);
            statement.setNull(9, Types.INTEGER);
        } else {
            statement.setString(8, centre.getName());
            statement.setInt(9, centre.getCapacity());
        }
        statement.setString(10, MySqlSupport.fromEnum(warning.getStatus()));
        statement.setString(11, warning.getIssuedBy());
        MySqlSupport.setDateTime(statement, 12, warning.getIssuedAt());
        MySqlSupport.setDateTime(statement, 13, warning.getExpiresAt());
    }

    private Warning map(ResultSet rs) throws SQLException {
        Warning warning = new Warning();
        warning.setWarningId(rs.getString("warning_id"));
        warning.setVersion(rs.getInt("version"));
        warning.setIncidentId(rs.getString("incident_id"));
        warning.setLevel(MySqlSupport.toEnum(WarningLevel.class,
                rs.getString("level")));
        warning.setAffectedArea(rs.getString("affected_area"));
        warning.setMessage(rs.getString("message"));
        warning.setRecommendedAction(rs.getString("recommended_action"));
        String centre = rs.getString("evacuation_centre");
        if (centre != null) {
            warning.setEvacuationCentre(new EvacuationCentre(centre,
                    rs.getInt("evacuation_capacity")));
        }
        warning.setStatus(MySqlSupport.toEnum(WarningStatus.class,
                rs.getString("status")));
        warning.setIssuedBy(rs.getString("issued_by"));
        warning.setIssuedAt(MySqlSupport.getDateTime(rs, "issued_at"));
        warning.setExpiresAt(MySqlSupport.getDateTime(rs, "expires_at"));
        return warning;
    }
}
