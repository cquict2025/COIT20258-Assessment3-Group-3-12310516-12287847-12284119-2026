package au.edu.cqu.drs.repository.mysql;

import au.edu.cqu.drs.model.AuditEvent;
import au.edu.cqu.drs.repository.AuditRepository;
import au.edu.cqu.drs.repository.mysql.MySqlSupport.CodeKey;
import au.edu.cqu.drs.security.AuditChain;
import au.edu.cqu.drs.security.ChainVerification;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * MySQL adapter for {@link AuditRepository}. Audit rows are only ever
 * inserted: apart from setting a new row's own hash, this class has no
 * statement that updates or deletes one.
 *
 * <p>Each new row is linked into a hash chain ({@link AuditChain}):
 * {@code previous_hash} holds the hash of the row before and
 * {@code entry_hash} the hash of this row, and {@link #verifyChain()}
 * reports the first row that was changed or removed afterwards. Rows are
 * added one at a time so that two clients cannot both link to the same
 * previous row.</p>
 */
public class MySqlAuditRepository implements AuditRepository {

    private static final List<String> COLUMNS = List.of(
            "actor", "action", "entity_type", "entity_id", "detail",
            "occurred_at", "previous_hash");

    private static final String SET_ENTRY_HASH =
            "UPDATE audit_events SET entry_hash = ? WHERE event_id = ?";

    /** One chain per database, so one writer at a time in this server. */
    private static final Object CHAIN_LOCK = new Object();

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
        event.setOccurredAt(AuditChain.stamp(event.getOccurredAt()));
        synchronized (CHAIN_LOCK) {
            try {
                this.database.inTransaction(connection -> {
                    String previous = lastHash(connection);
                    String code = MySqlSupport.insert(connection,
                            CodeKey.AUDIT_EVENT, COLUMNS, event.getEventId(),
                            (statement, first) ->
                                    bind(statement, first, event, previous));
                    event.setEventId(code);
                    try (PreparedStatement statement =
                            connection.prepareStatement(SET_ENTRY_HASH)) {
                        statement.setString(1,
                                AuditChain.hash(previous, event));
                        statement.setString(2, code);
                        statement.executeUpdate();
                    }
                    return code;
                });
                return event;
            } catch (SQLException e) {
                throw new IllegalStateException("Could not write the audit "
                        + "event.", e);
            }
        }
    }

    /**
     * Recomputes every hash in the chain, oldest first, and reports the
     * first row whose content no longer matches its hash or whose link to
     * the row before is wrong. Rows saved before the chain was introduced,
     * which have no hash, are counted but cannot be checked.
     *
     * @return the result
     */
    public ChainVerification verifyChain() {
        try {
            return this.database.withConnection(connection -> {
                int checked = 0;
                int unchained = 0;
                String expectedPrevious = AuditChain.GENESIS;
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT * FROM audit_events ORDER BY id");
                     ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        AuditEvent event = map(rs);
                        String previous = rs.getString("previous_hash");
                        String stored = rs.getString("entry_hash");
                        if (stored == null) {
                            if (checked > 0) {
                                return ChainVerification.broken(checked,
                                        unchained, event.getEventId(),
                                        "its hash has been removed");
                            }
                            unchained++;
                            continue;
                        }
                        if (!expectedPrevious.equals(previous)) {
                            return ChainVerification.broken(checked,
                                    unchained, event.getEventId(),
                                    "the record before it was changed or "
                                            + "removed");
                        }
                        if (!AuditChain.hash(previous, event)
                                .equals(stored)) {
                            return ChainVerification.broken(checked,
                                    unchained, event.getEventId(),
                                    "its content was changed");
                        }
                        expectedPrevious = stored;
                        checked++;
                    }
                }
                return ChainVerification.intact(checked, unchained);
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the audit log.",
                    e);
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
        return "MySqlAuditRepository: audit_events, hash-chained";
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

    /**
     * Returns the hash of the newest row, locking it until the transaction
     * ends, or the genesis value if no row has a hash yet.
     */
    private String lastHash(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT entry_hash FROM audit_events ORDER BY id DESC "
                        + "LIMIT 1 FOR UPDATE");
             ResultSet rs = statement.executeQuery()) {
            String hash = rs.next() ? rs.getString(1) : null;
            return hash == null ? AuditChain.GENESIS : hash;
        }
    }

    private void bind(PreparedStatement statement, int first,
            AuditEvent event, String previousHash) throws SQLException {
        int i = first;
        statement.setString(i++, event.getActor());
        statement.setString(i++, event.getAction());
        statement.setString(i++, event.getEntityType());
        statement.setString(i++, event.getEntityId());
        statement.setString(i++, event.getDetail());
        MySqlSupport.setDateTime(statement, i++, event.getOccurredAt());
        statement.setString(i, previousHash);
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
