package au.edu.cqu.drs.repository;

import au.edu.cqu.drs.model.FieldUpdate;

import java.util.List;

/** Data-access contract for the incident timeline (FR-10). */
public interface FieldUpdateRepository {

    /**
     * Saves a field update, giving it an identifier if it has none yet.
     *
     * @param fieldUpdate the update to save
     * @return the saved update, with its identifier set
     */
    FieldUpdate save(FieldUpdate fieldUpdate);

    /**
     * Finds the field updates posted against an incident.
     *
     * @param incidentId the incident identifier
     * @return the updates for the incident
     */
    List<FieldUpdate> findByIncidentId(String incidentId);
}
