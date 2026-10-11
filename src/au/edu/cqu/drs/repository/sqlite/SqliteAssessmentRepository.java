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

    public SqliteAssessmentRepository(Database database) {
        this.database = database;
    }

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

    @Override
    public Optional<Assessment> findLatestForIncident(String incidentId) {
        return findByIncidentId(incidentId).stream()
                .max(Comparator.comparingInt(Assessment::getVersion));
    }

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
}
