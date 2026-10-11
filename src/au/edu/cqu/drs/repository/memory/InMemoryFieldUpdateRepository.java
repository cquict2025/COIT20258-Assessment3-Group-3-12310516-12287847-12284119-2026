package au.edu.cqu.drs.repository.memory;

import au.edu.cqu.drs.model.FieldUpdate;
import au.edu.cqu.drs.repository.FieldUpdateRepository;

import java.util.ArrayList;
import java.util.List;

/** In-memory test double for the incident timeline. */
public class InMemoryFieldUpdateRepository implements FieldUpdateRepository {

    private final List<FieldUpdate> updates = new ArrayList<>();
    private int sequence = 0;

    @Override
    public FieldUpdate save(FieldUpdate fieldUpdate) {
        if (fieldUpdate.getUpdateId() == null) {
            this.sequence++;
            fieldUpdate.setUpdateId(String.format("UPD-%04d", this.sequence));
        }
        this.updates.add(fieldUpdate);
        return fieldUpdate;
    }

    @Override
    public List<FieldUpdate> findByIncidentId(String incidentId) {
        return this.updates.stream()
                .filter(u -> incidentId.equals(u.getIncidentId()))
                .toList();
    }
}
