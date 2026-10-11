package au.edu.cqu.drs.repository.sqlite;

import au.edu.cqu.drs.model.FieldUpdate;
import au.edu.cqu.drs.repository.FieldUpdateRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** SQLite adapter for {@link FieldUpdateRepository}. */
public class SqliteFieldUpdateRepository implements FieldUpdateRepository {

    private final Database database;

    public SqliteFieldUpdateRepository(Database database) {
        this.database = database;
    }

    @Override
    public FieldUpdate save(FieldUpdate fieldUpdate) {
        Connection connection = this.database.getConnection();
        if (fieldUpdate.getUpdateId() == null) {
            fieldUpdate.setUpdateId(
                    SqlSupport.nextId(connection, "field_updates", "update_id",
                                      "UPD", 4));
        }
        String sql = """
                INSERT INTO field_updates (
                    update_id, incident_id, submitted_by, submitted_at,
                    progress_note, observed_impact, resource_needs,
                    next_review_at, incident_version_seen)
                VALUES (?,?,?,?,?,?,?,?,?)""";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, fieldUpdate.getUpdateId());
            statement.setString(2, fieldUpdate.getIncidentId());
            statement.setString(3, fieldUpdate.getSubmittedBy());
            statement.setString(4,
                                SqlSupport.toText(fieldUpdate
                                        .getSubmittedAt()));
            statement.setString(5, fieldUpdate.getProgressNote());
            statement.setString(6, fieldUpdate.getObservedImpact());
            statement.setString(7, fieldUpdate.getResourceNeeds());
            statement.setString(8,
                                SqlSupport.toText(fieldUpdate
                                        .getNextReviewAt()));
            statement.setInt(9, fieldUpdate.getIncidentVersionSeen());
            statement.executeUpdate();
            return fieldUpdate;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save the field update.",
                                            e);
        }
    }

    @Override
    public List<FieldUpdate> findByIncidentId(String incidentId) {
        List<FieldUpdate> updates = new ArrayList<>();
        try (PreparedStatement statement = this.database.getConnection()
                .prepareStatement(
                "SELECT * FROM field_updates WHERE incident_id = ? ORDER BY "
                        + "submitted_at")) {
            statement.setString(1, incidentId);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    FieldUpdate update = new FieldUpdate();
                    update.setUpdateId(rs.getString("update_id"));
                    update.setIncidentId(rs.getString("incident_id"));
                    update.setSubmittedBy(rs.getString("submitted_by"));
                    update.setSubmittedAt(SqlSupport
                            .toDateTime(rs.getString("submitted_at")));
                    update.setProgressNote(rs.getString("progress_note"));
                    update.setObservedImpact(rs.getString("observed_impact"));
                    update.setResourceNeeds(rs.getString("resource_needs"));
                    update.setNextReviewAt(SqlSupport
                            .toDateTime(rs.getString("next_review_at")));
                    update.setIncidentVersionSeen(rs
                            .getInt("incident_version_seen"));
                    updates.add(update);
                }
            }
            return updates;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the incident "
                    + "timeline.",
                                            
                                            
                                            
                                            e);
        }
    }
}
