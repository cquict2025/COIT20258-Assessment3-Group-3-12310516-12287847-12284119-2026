package au.edu.cqu.drs.repository.mysql;

import au.edu.cqu.drs.model.Assessment;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.repository.AssessmentRepository;
import au.edu.cqu.drs.repository.mysql.MySqlSupport.CodeKey;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** MySQL adapter for {@link AssessmentRepository}. */
public class MySqlAssessmentRepository implements AssessmentRepository {

    private static final List<String> COLUMNS = List.of(
            "incident_id", "threat_to_life", "scale", "infrastructure_impact",
            "source_confidence", "calculated_score", "recommended_priority",
            "confirmed_priority", "override_reason", "assessed_by",
            "assessed_at", "version");

    private final MySqlDatabase database;

    /**
     * Creates the repository.
     *
     * @param database the pooled MySQL database
     */
    public MySqlAssessmentRepository(MySqlDatabase database) {
        this.database = database;
    }

    /**
     * Inserts a new assessment version, giving it a code, or updates an
     * existing one.
     *
     * @param assessment the assessment
     * @return the same assessment, with its code set
     */
    @Override
    public Assessment save(Assessment assessment) {
        try {
            String code = this.database.inTransaction(connection ->
                    MySqlSupport.save(connection, CodeKey.ASSESSMENT,
                            COLUMNS, assessment.getAssessmentId(),
                            (statement, first) ->
                                    bind(statement, first, assessment)));
            assessment.setAssessmentId(code);
            return assessment;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save the assessment.",
                    e);
        }
    }

    /**
     * Lists every assessment version of an incident.
     *
     * @param incidentId the incident code
     * @return the versions, oldest first
     */
    @Override
    public List<Assessment> findByIncidentId(String incidentId) {
        return query("SELECT * FROM assessments WHERE incident_id = ? "
                + "ORDER BY version, id", incidentId);
    }

    /**
     * Finds the newest assessment version of an incident.
     *
     * @param incidentId the incident code
     * @return the newest version, if there is one
     */
    @Override
    public Optional<Assessment> findLatestForIncident(String incidentId) {
        List<Assessment> found = query("SELECT * FROM assessments "
                + "WHERE incident_id = ? ORDER BY version DESC, id DESC "
                + "LIMIT 1", incidentId);
        return found.isEmpty() ? Optional.empty()
                : Optional.of(found.get(0));
    }

    /**
     * Describes the repository.
     *
     * @return the table it uses
     */
    @Override
    public String toString() {
        return "MySqlAssessmentRepository: assessments";
    }

    private List<Assessment> query(String sql, String incidentId) {
        try {
            return this.database.withConnection(connection -> {
                List<Assessment> assessments = new ArrayList<>();
                try (PreparedStatement statement =
                        connection.prepareStatement(sql)) {
                    statement.setString(1, incidentId);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            assessments.add(map(rs));
                        }
                    }
                }
                return assessments;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read assessments.",
                    e);
        }
    }

    private void bind(PreparedStatement statement, int first,
            Assessment assessment) throws SQLException {
        int i = first;
        statement.setString(i++, assessment.getIncidentId());
        statement.setInt(i++, assessment.getThreatToLife());
        statement.setInt(i++, assessment.getScale());
        statement.setInt(i++, assessment.getInfrastructureImpact());
        statement.setInt(i++, assessment.getSourceConfidence());
        statement.setInt(i++, assessment.getCalculatedScore());
        statement.setString(i++,
                MySqlSupport.fromEnum(assessment.getRecommendedPriority()));
        statement.setString(i++,
                MySqlSupport.fromEnum(assessment.getConfirmedPriority()));
        statement.setString(i++, assessment.getOverrideReason());
        statement.setString(i++, assessment.getAssessedBy());
        MySqlSupport.setDateTime(statement, i++, assessment.getAssessedAt());
        statement.setInt(i, assessment.getVersion());
    }

    private Assessment map(ResultSet rs) throws SQLException {
        Assessment assessment = new Assessment();
        assessment.setAssessmentId(rs.getString("assessment_id"));
        assessment.setIncidentId(rs.getString("incident_id"));
        assessment.setThreatToLife(rs.getInt("threat_to_life"));
        assessment.setScale(rs.getInt("scale"));
        assessment.setInfrastructureImpact(
                rs.getInt("infrastructure_impact"));
        assessment.setSourceConfidence(rs.getInt("source_confidence"));
        assessment.setCalculatedScore(rs.getInt("calculated_score"));
        assessment.setRecommendedPriority(MySqlSupport.toEnum(Priority.class,
                rs.getString("recommended_priority")));
        assessment.setConfirmedPriority(MySqlSupport.toEnum(Priority.class,
                rs.getString("confirmed_priority")));
        assessment.setOverrideReason(rs.getString("override_reason"));
        assessment.setAssessedBy(rs.getString("assessed_by"));
        assessment.setAssessedAt(MySqlSupport.getDateTime(rs, "assessed_at"));
        assessment.setVersion(rs.getInt("version"));
        return assessment;
    }
}
