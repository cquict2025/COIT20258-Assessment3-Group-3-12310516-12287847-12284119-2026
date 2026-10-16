package au.edu.cqu.drs.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * An immutable record of one significant action (FR-12). Assessment 1 committed
 * to writing the audit event in the same unit of work as the operational change
 * so that the timeline can never claim an action that was not committed.
 */
public class AuditEvent implements Serializable {

    /** Version of the serialised form sent between client and server. */
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private String eventId;
    private String actor;
    private String action;
    private String entityType;
    private String entityId;
    private String detail;
    private LocalDateTime occurredAt;

    /**
     * Creates an empty audit event.
     */
    public AuditEvent() {
    }

    /**
     * Creates an audit event.
     *
     * @param actor      the actor
     * @param action     the action
     * @param entityType the entity type
     * @param entityId   the entity id
     * @param detail     the detail
     * @param occurredAt the occurred at
     */
    public AuditEvent(String actor, String action, String entityType,
                      String entityId, 
                      String detail, LocalDateTime occurredAt) {
        this.actor = actor;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.detail = detail;
        this.occurredAt = occurredAt;
    }

    /**
     * Returns the event id.
     *
     * @return the event id
     */
    public String getEventId() {
        return this.eventId;
    }

    /**
     * Sets the event id.
     *
     * @param eventId the event id
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * Returns the actor.
     *
     * @return the actor
     */
    public String getActor() {
        return this.actor;
    }

    /**
     * Sets the actor.
     *
     * @param actor the actor
     */
    public void setActor(String actor) {
        this.actor = actor;
    }

    /**
     * Returns the action.
     *
     * @return the action
     */
    public String getAction() {
        return this.action;
    }

    /**
     * Sets the action.
     *
     * @param action the action
     */
    public void setAction(String action) {
        this.action = action;
    }

    /**
     * Returns the entity type.
     *
     * @return the entity type
     */
    public String getEntityType() {
        return this.entityType;
    }

    /**
     * Sets the entity type.
     *
     * @param entityType the entity type
     */
    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    /**
     * Returns the entity id.
     *
     * @return the entity id
     */
    public String getEntityId() {
        return this.entityId;
    }

    /**
     * Sets the entity id.
     *
     * @param entityId the entity id
     */
    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    /**
     * Returns the detail.
     *
     * @return the detail
     */
    public String getDetail() {
        return this.detail;
    }

    /**
     * Sets the detail.
     *
     * @param detail the detail
     */
    public void setDetail(String detail) {
        this.detail = detail;
    }

    /**
     * Returns the occurred at.
     *
     * @return the occurred at
     */
    public LocalDateTime getOccurredAt() {
        return this.occurredAt;
    }

    /**
     * Sets the occurred at.
     *
     * @param occurredAt the occurred at
     */
    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    /**
     * Describes the event as a log line: when it happened, who acted,
     * what was done and to which entity.
     *
     * @return a one-line summary of this audit event
     */
    @Override
    public String toString() {
        return String.format("[%s] %s performed %s on %s %s | %s",
                this.occurredAt == null ? "unknown time" : this.occurredAt
                        .format(TIMESTAMP_FORMAT),
                this.actor,
                this.action,
                this.entityType,
                this.entityId,
                this.detail == null ? "" : this.detail);
    }
}
