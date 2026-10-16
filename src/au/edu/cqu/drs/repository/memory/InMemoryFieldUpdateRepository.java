package au.edu.cqu.drs.repository.memory;

import au.edu.cqu.drs.model.FieldUpdate;
import au.edu.cqu.drs.repository.FieldUpdateRepository;

import java.util.ArrayList;
import java.util.List;

/** In-memory test double for the incident timeline. */
public class InMemoryFieldUpdateRepository implements FieldUpdateRepository {

    private final List<FieldUpdate> updates = new ArrayList<>();
    private int sequence = 0;

    /**
     * Appends a field update. A new update is given the next UPD-
     * identifier.
     *
     * @param fieldUpdate the update to store
     * @return the same update, with its identifier set
     */
    @Override
    public FieldUpdate save(FieldUpdate fieldUpdate) {
        if (fieldUpdate.getUpdateId() == null) {
            this.sequence++;
            fieldUpdate.setUpdateId(String.format("UPD-%04d", this.sequence));
        }
        this.updates.add(fieldUpdate);
        return fieldUpdate;
    }

    /**
     * Finds the field updates posted against an incident.
     *
     * @param incidentId the incident identifier
     * @return the updates for the incident, oldest first
     */
    @Override
    public List<FieldUpdate> findByIncidentId(String incidentId) {
        return this.updates.stream()
                .filter(u -> incidentId.equals(u.getIncidentId()))
                .toList();
    }

    /**
     * Describes the repository.
     *
     * @return how many field updates it holds
     */
    @Override
    public String toString() {
        return "InMemoryFieldUpdateRepository: " + this.updates.size()
                + " update(s)";
    }
}
