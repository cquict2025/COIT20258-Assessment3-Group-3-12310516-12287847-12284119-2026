package au.edu.cqu.drs.repository.mysql;

import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.model.ReportStatus;
import au.edu.cqu.drs.repository.ReportRepository;
import au.edu.cqu.drs.repository.mysql.MySqlSupport.CodeKey;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** MySQL adapter for {@link ReportRepository}. */
public class MySqlReportRepository implements ReportRepository {

    private static final List<String> COLUMNS = List.of(
            "disaster_type", "other_type_description", "address_line",
            "suburb", "latitude", "longitude", "observed_at", "submitted_at",
            "description", "immediate_risks", "reporter_name",
            "reporter_contact", "status", "decision_reason",
            "linked_incident_id");

    private final MySqlDatabase database;

    /**
     * Creates the repository.
     *
     * @param database the pooled MySQL database
     */
    public MySqlReportRepository(MySqlDatabase database) {
        this.database = database;
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
     * Describes the repository.
     *
     * @return the table it uses
     */
    @Override
    public String toString() {
        return "MySqlReportRepository: reports";
    }

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
        statement.setString(i++, report.getReporterName());
        statement.setString(i++, report.getReporterContact());
        statement.setString(i++, MySqlSupport.fromEnum(report.getStatus()));
        statement.setString(i++, report.getDecisionReason());
        statement.setString(i, report.getLinkedIncidentId());
    }

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
        report.setReporterName(rs.getString("reporter_name"));
        report.setReporterContact(rs.getString("reporter_contact"));
        report.setStatus(MySqlSupport.toEnum(ReportStatus.class,
                rs.getString("status")));
        report.setDecisionReason(rs.getString("decision_reason"));
        report.setLinkedIncidentId(rs.getString("linked_incident_id"));
        return report;
    }
}
