package au.edu.cqu.drs.repository.sqlite;

import au.edu.cqu.drs.model.AuditEvent;
import au.edu.cqu.drs.repository.AuditRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * SQLite adapter for {@link AuditRepository}. Audit rows are never updated or
 * deleted.
 */
public class SqliteAuditRepository implements AuditRepository {

    private final Database database;

    /**
     * Creates a SQLite audit repository.
     *
     * @param database the database
     */
    public SqliteAuditRepository(Database database) {
        this.database = database;
    }

    /**
     * Inserts a new audit event. A new event is given the next AUD-
     * identifier.
     *
     * @param event the event to record
     * @return the same event, with its identifier set
     * @throws IllegalStateException if the row cannot be written
     */
    @Override
    public AuditEvent save(AuditEvent event) {
        Connection connection = this.database.getConnection();
        if (event.getEventId() == null) {
            event.setEventId(SqlSupport
                    .nextId(connection, "audit_events", "event_id", "AUD", 5));
        }
        String sql = """
                INSERT INTO audit_events (
                    event_id, actor, action, entity_type, entity_id, detail,
                    occurred_at)
                VALUES (?,?,?,?,?,?,?)""";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, event.getEventId());
            statement.setString(2, event.getActor());
            statement.setString(3, event.getAction());
            statement.setString(4, event.getEntityType());
            statement.setString(5, event.getEntityId());
            statement.setString(6, event.getDetail());
            statement.setString(7, SqlSupport.toText(event.getOccurredAt()));
            statement.executeUpdate();
            return event;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not write the audit event.",
                                            e);
        }
    }

    /**
     * Lists every audit event, newest first.
     *
     * @return all audit events
     */
    @Override
    public List<AuditEvent> findAll() {
        return query("SELECT * FROM audit_events ORDER BY occurred_at DESC",
                     null, null);
    }

    /**
     * Finds the audit events recorded against one entity, newest first.
     *
     * @param entityType the kind of entity, such as an incident
     * @param entityId the entity identifier
     * @return the events for that entity
     */
    @Override
    public List<AuditEvent> findByEntity(String entityType, String entityId) {
        return query("SELECT * FROM audit_events WHERE entity_type = ? AND "
                + "entity_id = ? "
                + "ORDER BY occurred_at DESC", entityType, entityId);
    }

    /**
     * Runs a query against the audit log and maps every row to an event.
     *
     * @param sql the query to run
     * @param first the first parameter, or null if the query has none
     * @param second the second parameter, used only when first is set
     * @return the events found
     * @throws IllegalStateException if the rows cannot be read
     */
    private List<AuditEvent> query(String sql, String first, String second) {
        List<AuditEvent> events = new ArrayList<>();
        try (PreparedStatement statement = this.database.getConnection()
                .prepareStatement(sql)) {
            if (first != null) {
                statement.setString(1, first);
                statement.setString(2, second);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    AuditEvent event = new AuditEvent();
                    event.setEventId(rs.getString("event_id"));
                    event.setActor(rs.getString("actor"));
                    event.setAction(rs.getString("action"));
                    event.setEntityType(rs.getString("entity_type"));
                    event.setEntityId(rs.getString("entity_id"));
                    event.setDetail(rs.getString("detail"));
                    event.setOccurredAt(SqlSupport
                            .toDateTime(rs.getString("occurred_at")));
                    events.add(event);
                }
            }
            return events;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the audit log.", e);
        }
    }

    /**
     * Describes the repository.
     *
     * @return the database it reads and writes
     */
    @Override
    public String toString() {
        return "SqliteAuditRepository: " + this.database;
    }
}
