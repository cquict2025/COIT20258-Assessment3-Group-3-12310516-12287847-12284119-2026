package au.edu.cqu.drs.server.event;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One change in the shared state, pushed by the server to every client that
 * has subscribed to live events.
 *
 * <p>An event names what changed (the entity type and ID) and says what
 * happened in a short summary for the activity feed. It never carries the
 * entity itself: a client that needs the new state asks the server for it,
 * so the permission checks on reads still apply and no personal details
 * travel with the event. Events are immutable.</p>
 */
public final class ServerEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Time format used in the activity feed. */
    private static final DateTimeFormatter FEED_TIME =
            DateTimeFormatter.ofPattern("HH:mm:ss");

    private final EventType type;
    private final String entityType;
    private final String entityId;
    private final String summary;
    private final LocalDateTime time;
    private final int clientCount;

    /**
     * Creates an event.
     *
     * @param type        the kind of event
     * @param entityType  what changed, such as Report or Incident, or null
     * @param entityId    the reference of what changed, or null
     * @param summary     one line describing what happened
     * @param time        when it happened, by the server clock
     * @param clientCount the number of clients receiving events; only
     *                    meaningful for {@link EventType#CLIENTS}
     */
    public ServerEvent(EventType type, String entityType, String entityId,
                       String summary, LocalDateTime time, int clientCount) {
        if (type == null || time == null) {
            throw new IllegalArgumentException("An event needs a type and "
                    + "a time.");
        }
        this.type = type;
        this.entityType = entityType;
        this.entityId = entityId;
        this.summary = summary;
        this.time = time;
        this.clientCount = clientCount;
    }

    /**
     * Creates an event about one entity.
     *
     * @param type       the kind of event
     * @param entityType what changed, such as Report or Incident
     * @param entityId   the reference of what changed, or null
     * @param summary    one line describing what happened
     * @param time       when it happened, by the server clock
     * @return the event
     */
    public static ServerEvent about(EventType type, String entityType,
                                    String entityId, String summary,
                                    LocalDateTime time) {
        return new ServerEvent(type, entityType, entityId, summary, time, 0);
    }

    /**
     * Creates an event giving the number of clients receiving events.
     *
     * @param count the number of clients
     * @param time  when the number changed
     * @return the event
     */
    public static ServerEvent clients(int count, LocalDateTime time) {
        return new ServerEvent(EventType.CLIENTS, null, null,
                count + " client(s) receiving live updates", time, count);
    }

    /**
     * Creates a heartbeat, sent on a quiet channel to show it still works.
     *
     * @param time the server time
     * @return the event
     */
    public static ServerEvent heartbeat(LocalDateTime time) {
        return new ServerEvent(EventType.HEARTBEAT, null, null, null, time,
                0);
    }

    /**
     * Returns the kind of event.
     *
     * @return the type
     */
    public EventType getType() {
        return this.type;
    }

    /**
     * Returns what changed.
     *
     * @return the entity type, such as Incident, or null
     */
    public String getEntityType() {
        return this.entityType;
    }

    /**
     * Returns the reference of what changed.
     *
     * @return the entity ID, or null
     */
    public String getEntityId() {
        return this.entityId;
    }

    /**
     * Returns the one-line description of what happened.
     *
     * @return the summary
     */
    public String getSummary() {
        return this.summary;
    }

    /**
     * Returns when the event happened, by the server clock.
     *
     * @return the time
     */
    public LocalDateTime getTime() {
        return this.time;
    }

    /**
     * Returns the number of clients receiving events.
     *
     * @return the count; only meaningful for {@link EventType#CLIENTS}
     */
    public int getClientCount() {
        return this.clientCount;
    }

    /**
     * Formats the event as a line for the activity feed.
     *
     * @return the time, type and summary
     */
    public String toFeedLine() {
        return FEED_TIME.format(this.time) + "  " + this.type + "  "
                + this.summary;
    }

    /**
     * Describes the event.
     *
     * @return the type, entity and summary
     */
    @Override
    public String toString() {
        String entity = "";
        if (this.entityType != null) {
            entity = " " + this.entityType;
        }
        if (this.entityId != null) {
            entity = entity + " " + this.entityId;
        }
        return "ServerEvent " + this.type.name() + entity + " at "
                + this.time + ": " + this.summary;
    }
}
