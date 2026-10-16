package au.edu.cqu.drs.repository;

import au.edu.cqu.drs.model.AuditEvent;

import java.util.List;

/** Data-access contract for the immutable audit log (FR-12). */
public interface AuditRepository {

    /**
     * Appends an event to the audit log, giving it an identifier if it has
     * none yet.
     *
     * @param event the event to record
     * @return the recorded event, with its identifier set
     */
    AuditEvent save(AuditEvent event);

    /**
     * Lists every event in the audit log.
     *
     * @return all audit events
     */
    List<AuditEvent> findAll();

    /**
     * Finds the audit events recorded against one entity.
     *
     * @param entityType the kind of entity, such as an incident
     * @param entityId the entity identifier
     * @return the events for that entity
     */
    List<AuditEvent> findByEntity(String entityType, String entityId);
}
