package au.edu.cqu.drs.repository;

import au.edu.cqu.drs.model.AuditEvent;

import java.util.List;

/** Data-access contract for the immutable audit log (FR-12). */
public interface AuditRepository {

    AuditEvent save(AuditEvent event);

    List<AuditEvent> findAll();

    List<AuditEvent> findByEntity(String entityType, String entityId);
}
