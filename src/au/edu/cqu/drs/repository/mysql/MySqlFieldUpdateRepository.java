package au.edu.cqu.drs.repository.mysql;

import au.edu.cqu.drs.model.FieldUpdate;
import au.edu.cqu.drs.repository.FieldUpdateRepository;
import au.edu.cqu.drs.repository.mysql.MySqlSupport.CodeKey;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL adapter for {@link FieldUpdateRepository}. Field updates form the
 * incident timeline and are only ever added, never changed.
 */
public class MySqlFieldUpdateRepository implements FieldUpdateRepository {

    private static final List<String> COLUMNS = List.of(
            "incident_id", "submitted_by", "submitted_at", "progress_note",
            "observed_impact", "resource_needs", "next_review_at",
            "incident_version_seen");

    private final MySqlDatabase database;

    /**
     * Creates the repository.
     *
     * @param database the pooled MySQL database
     */
    public MySqlFieldUpdateRepository(MySqlDatabase database) {
        this.database = database;
    }

    /**
     * Adds a field update to the timeline, giving it a code.
     *
     * @param fieldUpdate the update
     * @return the same update, with its code set
     */
    @Override
    public FieldUpdate save(FieldUpdate fieldUpdate) {
        try {
            String code = this.database.inTransaction(connection ->
                    MySqlSupport.insert(connection, CodeKey.FIELD_UPDATE,
                            COLUMNS, fieldUpdate.getUpdateId(),
                            (statement, first) ->
                                    bind(statement, first, fieldUpdate)));
            fieldUpdate.setUpdateId(code);
            return fieldUpdate;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save the field "
                    + "update.", e);
        }
    }

    /**
     * Returns an incident's timeline.
     *
     * @param incidentId the incident code
     * @return the updates, oldest first
     */
    @Override
    public List<FieldUpdate> findByIncidentId(String incidentId) {
        try {
            return this.database.withConnection(connection -> {
                List<FieldUpdate> updates = new ArrayList<>();
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM field_updates WHERE incident_id = ? "
                                + "ORDER BY submitted_at, id")) {
                    statement.setString(1, incidentId);
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            updates.add(map(rs));
                        }
                    }
                }
                return updates;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the incident "
                    + "timeline.", e);
        }
    }

    /**
     * Sets the field update's column values on a statement, in the order
     * of {@code COLUMNS}.
     *
     * @param statement   the INSERT statement to fill
     * @param first       the position of the first column's parameter
     * @param fieldUpdate the update whose values are bound
     * @throws SQLException if a parameter cannot be set
     */
    private void bind(PreparedStatement statement, int first,
            FieldUpdate fieldUpdate) throws SQLException {
        int i = first;
        statement.setString(i++, fieldUpdate.getIncidentId());
        statement.setString(i++, fieldUpdate.getSubmittedBy());
        MySqlSupport.setDateTime(statement, i++,
                fieldUpdate.getSubmittedAt());
        statement.setString(i++, fieldUpdate.getProgressNote());
        statement.setString(i++, fieldUpdate.getObservedImpact());
        statement.setString(i++, fieldUpdate.getResourceNeeds());
        MySqlSupport.setDateTime(statement, i++,
                fieldUpdate.getNextReviewAt());
        statement.setInt(i, fieldUpdate.getIncidentVersionSeen());
    }

    /**
     * Builds a field update from the current row of a result set.
     *
     * @param rs the result set, positioned on a row
     * @return the field update read from the row
     * @throws SQLException if a column cannot be read
     */
    private FieldUpdate map(ResultSet rs) throws SQLException {
        FieldUpdate update = new FieldUpdate();
        update.setUpdateId(rs.getString("update_id"));
        update.setIncidentId(rs.getString("incident_id"));
        update.setSubmittedBy(rs.getString("submitted_by"));
        update.setSubmittedAt(MySqlSupport.getDateTime(rs, "submitted_at"));
        update.setProgressNote(rs.getString("progress_note"));
        update.setObservedImpact(rs.getString("observed_impact"));
        update.setResourceNeeds(rs.getString("resource_needs"));
        update.setNextReviewAt(MySqlSupport.getDateTime(rs,
                "next_review_at"));
        update.setIncidentVersionSeen(rs.getInt("incident_version_seen"));
        return update;
    }

    /**
     * Describes the repository.
     *
     * @return the table it uses
     */
    @Override
    public String toString() {
        return "MySqlFieldUpdateRepository: field_updates";
    }
}
