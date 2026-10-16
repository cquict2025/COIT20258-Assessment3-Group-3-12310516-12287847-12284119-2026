package au.edu.cqu.drs.repository.sqlite;

import au.edu.cqu.drs.model.Assessment;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.repository.AssessmentRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** SQLite adapter for {@link AssessmentRepository}. */
public class SqliteAssessmentRepository implements AssessmentRepository {

    private final Database database;

    /**
     * Creates a SQLite assessment repository.
     *
     * @param database the database
     */
    public SqliteAssessmentRepository(Database database) {
        this.database = database;
    }

    /**
     * Inserts an assessment, or updates the stored row with the same
     * identifier. A new assessment is given the next ASM- identifier.
     *
     * @param assessment the assessment to save
     * @return the same assessment, with its identifier set
     * @throws IllegalStateException if the row cannot be written
     */
    @Override
    public Assessment save(Assessment assessment) {
        Connection connection = this.database.getConnection();
        if (assessment.getAssessmentId() == null) {
            assessment.setAssessmentId(
                    SqlSupport.nextId(connection, "assessments",
                                      "assessment_id", "ASM", 4));
        }
        String sql = """
                INSERT INTO assessments (
                    assessment_id, incident_id, threat_to_life, scale,
                    infrastructure_impact, source_confidence, calculated_score,
                    recommended_priority, confirmed_priority, override_reason,
                    assessed_by, assessed_at, version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(assessment_id) DO UPDATE SET
                    threat_to_life = excluded.threat_to_life,
                    scale = excluded.scale,
                    infrastructure_impact = excluded.infrastructure_impact,
                    source_confidence = excluded.source_confidence,
                    calculated_score = excluded.calculated_score,
                    recommended_priority = excluded.recommended_priority,
                    confirmed_priority = excluded.confirmed_priority,
                    override_reason = excluded.override_reason,
                    assessed_by = excluded.assessed_by,
                    assessed_at = excluded.assessed_at,
                    version = excluded.version""";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, assessment.getAssessmentId());
            statement.setString(2, assessment.getIncidentId());
            statement.setInt(3, assessment.getThreatToLife());
            statement.setInt(4, assessment.getScale());
            statement.setInt(5, assessment.getInfrastructureImpact());
            statement.setInt(6, assessment.getSourceConfidence());
            statement.setInt(7, assessment.getCalculatedScore());
            statement.setString(8,
                                SqlSupport.fromEnum(assessment
                                        .getRecommendedPriority()));
            statement.setString(9,
                                SqlSupport.fromEnum(assessment
                                        .getConfirmedPriority()));
            statement.setString(10, assessment.getOverrideReason());
            statement.setString(11, assessment.getAssessedBy());
            statement.setString(12,
                                SqlSupport.toText(assessment.getAssessedAt()));
            statement.setInt(13, assessment.getVersion());
            statement.executeUpdate();
            return assessment;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save the assessment.",
                                            e);
        }
    }

    /**
     * Finds the assessments recorded for an incident, lowest version first.
     *
     * @param incidentId the incident identifier
     * @return the assessments for the incident
     * @throws IllegalStateException if the rows cannot be read
     */
    @Override
    public List<Assessment> findByIncidentId(String incidentId) {
        List<Assessment> assessments = new ArrayList<>();
        try (PreparedStatement statement = this.database.getConnection()
                .prepareStatement(
                "SELECT * FROM assessments WHERE incident_id = ? ORDER BY "
                        + "version")) {
            statement.setString(1, incidentId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    assessments.add(map(rs));
                }
            }
            return assessments;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read assessments.", e);
        }
    }

    /**
     * Finds the assessment with the highest version number for an
     * incident.
     *
     * @param incidentId the incident identifier
     * @return the latest assessment, or empty if there is none
     */
    @Override
    public Optional<Assessment> findLatestForIncident(String incidentId) {
        return findByIncidentId(incidentId).stream()
                .max(Comparator.comparingInt(Assessment::getVersion));
    }

    /**
     * Builds an assessment from the current row of a result set.
     *
     * @param rs the result set, positioned on the row to read
     * @return the assessment read from the row
     * @throws SQLException if a column cannot be read
     */
    private Assessment map(ResultSet rs) throws SQLException {
        Assessment assessment = new Assessment();
        assessment.setAssessmentId(rs.getString("assessment_id"));
        assessment.setIncidentId(rs.getString("incident_id"));
        assessment.setThreatToLife(rs.getInt("threat_to_life"));
        assessment.setScale(rs.getInt("scale"));
        assessment.setInfrastructureImpact(rs.getInt("infrastructure_impact"));
        assessment.setSourceConfidence(rs.getInt("source_confidence"));
        assessment.setCalculatedScore(rs.getInt("calculated_score"));
        assessment.setRecommendedPriority(
                SqlSupport.toEnum(Priority.class,
                                  rs.getString("recommended_priority")));
        assessment.setConfirmedPriority(
                SqlSupport.toEnum(Priority.class,
                                  rs.getString("confirmed_priority")));
        assessment.setOverrideReason(rs.getString("override_reason"));
        assessment.setAssessedBy(rs.getString("assessed_by"));
        assessment.setAssessedAt(SqlSupport
                .toDateTime(rs.getString("assessed_at")));
        assessment.setVersion(rs.getInt("version"));
        return assessment;
    }

    /**
     * Describes the repository.
     *
     * @return the database it reads and writes
     */
    @Override
    public String toString() {
        return "SqliteAssessmentRepository: " + this.database;
    }
}
