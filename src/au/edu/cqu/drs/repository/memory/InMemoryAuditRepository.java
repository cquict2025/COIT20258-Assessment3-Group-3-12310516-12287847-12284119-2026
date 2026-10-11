package au.edu.cqu.drs.repository.memory;

import au.edu.cqu.drs.model.AuditEvent;
import au.edu.cqu.drs.repository.AuditRepository;

import java.util.ArrayList;
import java.util.List;

/** In-memory test double for the audit log. */
public class InMemoryAuditRepository implements AuditRepository {

    private final List<AuditEvent> events = new ArrayList<>();
    private int sequence = 0;

    @Override
    public AuditEvent save(AuditEvent event) {
        if (event.getEventId() == null) {
            this.sequence++;
            event.setEventId(String.format("AUD-%05d", this.sequence));
        }
        this.events.add(event);
        return event;
    }

    @Override
    public List<AuditEvent> findAll() {
        return new ArrayList<>(this.events);
    }

    @Override
    public List<AuditEvent> findByEntity(String entityType, String entityId) {
        return this.events.stream()
                .filter(e -> entityType.equals(e.getEntityType())
                        && entityId.equals(e.getEntityId()))
                .toList();
    }
}
