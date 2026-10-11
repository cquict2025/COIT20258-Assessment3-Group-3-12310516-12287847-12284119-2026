package au.edu.cqu.drs.repository.sqlite;

import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.model.ReportStatus;
import au.edu.cqu.drs.repository.ReportRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQLite adapter for {@link ReportRepository}. */
public class SqliteReportRepository implements ReportRepository {

    private final Database database;

    public SqliteReportRepository(Database database) {
        this.database = database;
    }

    @Override
    public DisasterReport save(DisasterReport report) {
        Connection connection = this.database.getConnection();
        if (report.getReportId() == null) {
            report.setReportId(SqlSupport
                    .nextId(connection, "reports", "report_id", "RPT", 4));
        }
        String sql = """
                INSERT INTO reports (
                    report_id, disaster_type, other_type_description,
                    address_line, suburb, latitude, longitude, observed_at,
                    submitted_at, description, immediate_risks, reporter_name,
                    reporter_contact, status, decision_reason,
                    linked_incident_id)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(report_id) DO UPDATE SET
                    disaster_type = excluded.disaster_type,
                    other_type_description = excluded.other_type_description,
                    address_line = excluded.address_line,
                    suburb = excluded.suburb,
                    latitude = excluded.latitude,
                    longitude = excluded.longitude,
                    observed_at = excluded.observed_at,
                    submitted_at = excluded.submitted_at,
                    description = excluded.description,
                    immediate_risks = excluded.immediate_risks,
                    reporter_name = excluded.reporter_name,
                    reporter_contact = excluded.reporter_contact,
                    status = excluded.status,
                    decision_reason = excluded.decision_reason,
                    linked_incident_id = excluded.linked_incident_id""";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            Location location = report.getLocation() == null ? new Location()
                    : report.getLocation();
            statement.setString(1, report.getReportId());
            statement.setString(2,
                                SqlSupport.fromEnum(report.getDisasterType()));
            statement.setString(3, report.getOtherTypeDescription());
            statement.setString(4, location.getAddressLine());
            statement.setString(5, location.getSuburb());
            statement.setDouble(6, location.getLatitude());
            statement.setDouble(7, location.getLongitude());
            statement.setString(8, SqlSupport.toText(report.getObservedAt()));
            statement.setString(9, SqlSupport.toText(report.getSubmittedAt()));
            statement.setString(10, report.getDescription());
            statement.setString(11, report.getImmediateRisks());
            statement.setString(12, report.getReporterName());
            statement.setString(13, report.getReporterContact());
            statement.setString(14, SqlSupport.fromEnum(report.getStatus()));
            statement.setString(15, report.getDecisionReason());
            statement.setString(16, report.getLinkedIncidentId());
            statement.executeUpdate();
            return report;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save the disaster "
                    + "report.",
                                            
                                            
                                            
                                            e);
        }
    }

    @Override
    public Optional<DisasterReport> findById(String reportId) {
        try (PreparedStatement statement = this.database.getConnection()
                .prepareStatement("SELECT * FROM reports WHERE report_id = ?"))
                        {
            statement.setString(1, reportId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the disaster "
                    + "report.",
                                            
                                            
                                            
                                            e);
        }
    }

    @Override
    public List<DisasterReport> findAll() {
        return query("SELECT * FROM reports ORDER BY report_id", null);
    }

    @Override
    public List<DisasterReport> findByStatus(ReportStatus status) {
        return
                query("SELECT * FROM reports WHERE status = ? ORDER BY "
                        + "report_id",
                     
                     
                     
                     
                     
                     
                     
                     
                     
                     
                     
                     
                     
                     
                     
                     status.name());
    }

    private List<DisasterReport> query(String sql, String parameter) {
        List<DisasterReport> reports = new ArrayList<>();
        try (PreparedStatement statement = this.database.getConnection()
                .prepareStatement(sql)) {
            if (parameter != null) {
                statement.setString(1, parameter);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    reports.add(map(rs));
                }
            }
            return reports;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read disaster reports.",
                                            e);
        }
    }

    private DisasterReport map(ResultSet rs) throws SQLException {
        DisasterReport report = new DisasterReport();
        report.setReportId(rs.getString("report_id"));
        report.setDisasterType(SqlSupport
                .toEnum(DisasterType.class, rs.getString("disaster_type")));
        report.setOtherTypeDescription(rs.getString("other_type_description"));
        report.setLocation(new Location(rs.getString("address_line"),
                rs.getString("suburb"),
                rs.getDouble("latitude"), rs.getDouble("longitude")));
        report.setObservedAt(SqlSupport
                .toDateTime(rs.getString("observed_at")));
        report.setSubmittedAt(SqlSupport
                .toDateTime(rs.getString("submitted_at")));
        report.setDescription(rs.getString("description"));
        report.setImmediateRisks(rs.getString("immediate_risks"));
        report.setReporterName(rs.getString("reporter_name"));
        report.setReporterContact(rs.getString("reporter_contact"));
        report.setStatus(SqlSupport
                .toEnum(ReportStatus.class, rs.getString("status")));
        report.setDecisionReason(rs.getString("decision_reason"));
        report.setLinkedIncidentId(rs.getString("linked_incident_id"));
        return report;
    }
}
