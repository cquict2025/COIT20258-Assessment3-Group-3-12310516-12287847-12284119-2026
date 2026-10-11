package au.edu.cqu.drs.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * An immutable record of one significant action (FR-12). Assessment 1 committed
 * to writing the audit event in the same unit of work as the operational change
 * so that the timeline can never claim an action that was not committed.
 */
public class AuditEvent {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private String eventId;
    private String actor;
    private String action;
    private String entityType;
    private String entityId;
    private String detail;
    private LocalDateTime occurredAt;

    public AuditEvent() {
    }

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

    public String getEventId() {
        return this.eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getActor() {
        return this.actor;
    }

    public void setActor(String actor) {
        this.actor = actor;
    }

    public String getAction() {
        return this.action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityType() {
        return this.entityType;
    }

    public void setEntityType(String entityType) {
        this.entityType = entityType;
    }

    public String getEntityId() {
        return this.entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getDetail() {
        return this.detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public LocalDateTime getOccurredAt() {
        return this.occurredAt;
    }

    public void setOccurredAt(LocalDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

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
