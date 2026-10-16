package au.edu.cqu.drs.repository.mysql;

import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.model.ReportStatus;
import au.edu.cqu.drs.repository.ReportRepository;
import au.edu.cqu.drs.repository.mysql.MySqlSupport.CodeKey;
import au.edu.cqu.drs.security.FieldCipher;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MySQL adapter for {@link ReportRepository}.
 *
 * <p>When it is given a {@link FieldCipher}, the reporter's name and contact
 * are encrypted before they are written and decrypted when they are read, so
 * the database never holds them in plaintext (spec 2.5).</p>
 */
public class MySqlReportRepository implements ReportRepository {

    private static final List<String> COLUMNS = List.of(
            "disaster_type", "other_type_description", "address_line",
            "suburb", "latitude", "longitude", "observed_at", "submitted_at",
            "description", "immediate_risks", "reporter_name",
            "reporter_contact", "status", "decision_reason",
            "linked_incident_id");

    private final MySqlDatabase database;
    private final FieldCipher cipher;

    /**
     * Creates the repository without encryption of personal details.
     *
     * @param database the pooled MySQL database
     */
    public MySqlReportRepository(MySqlDatabase database) {
        this(database, null);
    }

    /**
     * Creates the repository.
     *
     * @param database the pooled MySQL database
     * @param cipher   encrypts the reporter's name and contact, or null to
     *                 store them as they are
     */
    public MySqlReportRepository(MySqlDatabase database,
            FieldCipher cipher) {
        this.database = database;
        this.cipher = cipher;
    }

    /**
     * Inserts a new report, giving it a code, or updates an existing one.
     *
     * @param report the report
     * @return the same report, with its code set
     */
    @Override
    public DisasterReport save(DisasterReport report) {
        try {
            String code = this.database.inTransaction(connection ->
                    MySqlSupport.save(connection, CodeKey.REPORT, COLUMNS,
                            report.getReportId(),
                            (statement, first) ->
                                    bind(statement, first, report)));
            report.setReportId(code);
            return report;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save the disaster "
                    + "report.", e);
        }
    }

    /**
     * Finds a report by code.
     *
     * @param reportId the code, for example RPT-0001
     * @return the report, if there is one
     */
    @Override
    public Optional<DisasterReport> findById(String reportId) {
        List<DisasterReport> found =
                query("SELECT * FROM reports WHERE report_id = ?", reportId);
        return found.isEmpty() ? Optional.empty()
                : Optional.of(found.get(0));
    }

    /**
     * Lists every report.
     *
     * @return the reports in code order
     */
    @Override
    public List<DisasterReport> findAll() {
        return query("SELECT * FROM reports ORDER BY id", null);
    }

    /**
     * Lists the reports in one state.
     *
     * @param status the state
     * @return the matching reports in code order
     */
    @Override
    public List<DisasterReport> findByStatus(ReportStatus status) {
        return query("SELECT * FROM reports WHERE status = ? ORDER BY id",
                status.name());
    }

    /**
     * Runs a report query and maps every row it returns to a report.
     *
     * @param sql       the SELECT statement, with at most one parameter
     * @param parameter the value for the parameter, or null when the query
     *                  has none
     * @return the reports found, in the order the query gives
     */
    private List<DisasterReport> query(String sql, String parameter) {
        try {
            return this.database.withConnection(connection -> {
                List<DisasterReport> reports = new ArrayList<>();
                try (PreparedStatement statement =
                        connection.prepareStatement(sql)) {
                    if (parameter != null) {
                        statement.setString(1, parameter);
                    }
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            reports.add(map(rs));
                        }
                    }
                }
                return reports;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read disaster "
                    + "reports.", e);
        }
    }

    /**
     * Sets the report's column values on a statement, in the order of
     * {@code COLUMNS}, encrypting the reporter's name and contact when a
     * cipher is set. A missing location is stored as an empty one.
     *
     * @param statement the INSERT or UPDATE statement to fill
     * @param first     the position of the first column's parameter
     * @param report    the report whose values are bound
     * @throws SQLException if a parameter cannot be set
     */
    private void bind(PreparedStatement statement, int first,
            DisasterReport report) throws SQLException {
        Location location = report.getLocation() == null ? new Location()
                : report.getLocation();
        int i = first;
        statement.setString(i++,
                MySqlSupport.fromEnum(report.getDisasterType()));
        statement.setString(i++, report.getOtherTypeDescription());
        statement.setString(i++, location.getAddressLine());
        statement.setString(i++, location.getSuburb());
        statement.setDouble(i++, location.getLatitude());
        statement.setDouble(i++, location.getLongitude());
        MySqlSupport.setDateTime(statement, i++, report.getObservedAt());
        MySqlSupport.setDateTime(statement, i++, report.getSubmittedAt());
        statement.setString(i++, report.getDescription());
        statement.setString(i++, report.getImmediateRisks());
        statement.setString(i++,
                seal(report.getReporterName(), "reporter_name"));
        statement.setString(i++,
                seal(report.getReporterContact(), "reporter_contact"));
        statement.setString(i++, MySqlSupport.fromEnum(report.getStatus()));
        statement.setString(i++, report.getDecisionReason());
        statement.setString(i, report.getLinkedIncidentId());
    }

    /**
     * Builds a report from the current row of a result set, decrypting the
     * reporter's name and contact when a cipher is set.
     *
     * @param rs the result set, positioned on a row
     * @return the report read from the row
     * @throws SQLException if a column cannot be read
     */
    private DisasterReport map(ResultSet rs) throws SQLException {
        DisasterReport report = new DisasterReport();
        report.setReportId(rs.getString("report_id"));
        report.setDisasterType(MySqlSupport.toEnum(DisasterType.class,
                rs.getString("disaster_type")));
        report.setOtherTypeDescription(rs.getString("other_type_description"));
        report.setLocation(new Location(rs.getString("address_line"),
                rs.getString("suburb"), rs.getDouble("latitude"),
                rs.getDouble("longitude")));
        report.setObservedAt(MySqlSupport.getDateTime(rs, "observed_at"));
        report.setSubmittedAt(MySqlSupport.getDateTime(rs, "submitted_at"));
        report.setDescription(rs.getString("description"));
        report.setImmediateRisks(rs.getString("immediate_risks"));
        report.setReporterName(open(rs, "reporter_name"));
        report.setReporterContact(open(rs, "reporter_contact"));
        report.setStatus(MySqlSupport.toEnum(ReportStatus.class,
                rs.getString("status")));
        report.setDecisionReason(rs.getString("decision_reason"));
        report.setLinkedIncidentId(rs.getString("linked_incident_id"));
        return report;
    }

    /**
     * Encrypts a personal detail for its column, or returns it unchanged
     * when there is no cipher.
     *
     * @param value  the plain value, may be null
     * @param column the column it will be stored in
     * @return the value to store
     */
    private String seal(String value, String column) {
        return this.cipher == null ? value
                : this.cipher.encrypt(value, column);
    }

    /**
     * Reads a personal detail from the current row and decrypts it, or
     * returns it unchanged when there is no cipher.
     *
     * @param rs     the result set, positioned on a row
     * @param column the column to read
     * @return the plain value, or null if the column is null
     * @throws SQLException if the column cannot be read
     */
    private String open(ResultSet rs, String column) throws SQLException {
        String stored = rs.getString(column);
        return this.cipher == null ? stored
                : this.cipher.decrypt(stored, column);
    }

    /**
     * Describes the repository.
     *
     * @return the table it uses
     */
    @Override
    public String toString() {
        return "MySqlReportRepository: reports, personal details "
                + (this.cipher == null ? "not encrypted" : "encrypted");
    }
}
