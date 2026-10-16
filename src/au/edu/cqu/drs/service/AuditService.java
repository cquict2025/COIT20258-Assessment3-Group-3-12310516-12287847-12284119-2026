package au.edu.cqu.drs.service;

import au.edu.cqu.drs.model.AuditEvent;
import au.edu.cqu.drs.repository.AuditRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Writes the immutable action history required by FR-12. Every other service
 * calls this in the same unit of work as the operational change it records.
 */
public class AuditService {

    private final AuditRepository auditRepository;

    /**
     * Creates an audit service.
     *
     * @param auditRepository the audit repository
     */
    public AuditService(AuditRepository auditRepository) {
        this.auditRepository = auditRepository;
    }

    /**
     * Records one auditable action.
     *
     * @param actor      username responsible for the action
     * @param action     short action name, for example VALIDATE_REPORT
     * @param entityType the kind of entity affected
     * @param entityId   the identifier of the entity affected
     * @param detail     human-readable detail, excluding credentials
     * @return the saved audit event
     */
    public AuditEvent record(String actor, String action, String entityType,
                             String entityId, String detail) {
        AuditEvent event = new AuditEvent(actor, action, entityType, entityId,
                detail, LocalDateTime.now());
        return this.auditRepository.save(event);
    }

    /**
     * Returns every recorded audit event.
     *
     * @return all audit events
     */
    public List<AuditEvent> findAll() {
        return this.auditRepository.findAll();
    }

    /**
     * Returns the audit events recorded against one entity.
     *
     * @param entityType the kind of entity, for example Incident
     * @param entityId   the identifier of the entity
     * @return the audit events for that entity
     */
    public List<AuditEvent> findForEntity(String entityType, String entityId) {
        return this.auditRepository.findByEntity(entityType, entityId);
    }

    /**
     * Describes what this service does.
     *
     * @return a short description of this object
     */
    @Override
    public String toString() {
        return "AuditService: records and reads the audit log";
    }
}
