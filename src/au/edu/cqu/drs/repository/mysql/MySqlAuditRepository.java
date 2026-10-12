package au.edu.cqu.drs.repository.mysql;

import au.edu.cqu.drs.model.AuditEvent;
import au.edu.cqu.drs.repository.AuditRepository;
import au.edu.cqu.drs.repository.mysql.MySqlSupport.CodeKey;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL adapter for {@link AuditRepository}. Audit rows are only ever
 * inserted: this class has no statement that updates or deletes one.
 */
public class MySqlAuditRepository implements AuditRepository {

    private static final List<String> COLUMNS = List.of(
            "actor", "action", "entity_type", "entity_id", "detail",
            "occurred_at");

    private final MySqlDatabase database;

    /**
     * Creates the repository.
     *
     * @param database the pooled MySQL database
     */
    public MySqlAuditRepository(MySqlDatabase database) {
        this.database = database;
    }

    /**
     * Appends an event to the audit log, giving it a code.
     *
     * @param event the event
     * @return the same event, with its code set
     */
    @Override
    public AuditEvent save(AuditEvent event) {
        try {
            String code = this.database.inTransaction(connection ->
                    MySqlSupport.insert(connection, CodeKey.AUDIT_EVENT,
                            COLUMNS, event.getEventId(),
                            (statement, first) ->
                                    bind(statement, first, event)));
            event.setEventId(code);
            return event;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not write the audit "
                    + "event.", e);
        }
    }

    /**
     * Lists every event.
     *
     * @return the events, newest first
     */
    @Override
    public List<AuditEvent> findAll() {
        return query("SELECT * FROM audit_events "
                + "ORDER BY occurred_at DESC, id DESC", null, null);
    }

    /**
     * Lists the events about one entity.
     *
     * @param entityType the entity type, for example Incident
     * @param entityId   the entity code
     * @return the events, newest first
     */
    @Override
    public List<AuditEvent> findByEntity(String entityType, String entityId) {
        return query("SELECT * FROM audit_events "
                + "WHERE entity_type = ? AND entity_id = ? "
                + "ORDER BY occurred_at DESC, id DESC", entityType, entityId);
    }

    /**
     * Describes the repository.
     *
     * @return the table it uses
     */
    @Override
    public String toString() {
        return "MySqlAuditRepository: audit_events";
    }

    private List<AuditEvent> query(String sql, String entityType,
            String entityId) {
        try {
            return this.database.withConnection(connection -> {
                List<AuditEvent> events = new ArrayList<>();
                try (PreparedStatement statement =
                        connection.prepareStatement(sql)) {
                    if (entityType != null) {
                        statement.setString(1, entityType);
                        statement.setString(2, entityId);
                    }
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            events.add(map(rs));
                        }
                    }
                }
                return events;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the audit log.",
                    e);
        }
    }

    private void bind(PreparedStatement statement, int first,
            AuditEvent event) throws SQLException {
        int i = first;
        statement.setString(i++, event.getActor());
        statement.setString(i++, event.getAction());
        statement.setString(i++, event.getEntityType());
        statement.setString(i++, event.getEntityId());
        statement.setString(i++, event.getDetail());
        MySqlSupport.setDateTime(statement, i, event.getOccurredAt());
    }

    private AuditEvent map(ResultSet rs) throws SQLException {
        AuditEvent event = new AuditEvent();
        event.setEventId(rs.getString("event_id"));
        event.setActor(rs.getString("actor"));
        event.setAction(rs.getString("action"));
        event.setEntityType(rs.getString("entity_type"));
        event.setEntityId(rs.getString("entity_id"));
        event.setDetail(rs.getString("detail"));
        event.setOccurredAt(MySqlSupport.getDateTime(rs, "occurred_at"));
        return event;
    }
}
