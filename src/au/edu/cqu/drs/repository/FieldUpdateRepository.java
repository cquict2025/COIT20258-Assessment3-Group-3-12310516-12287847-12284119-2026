package au.edu.cqu.drs.repository;

import au.edu.cqu.drs.model.FieldUpdate;

import java.util.List;

/** Data-access contract for the incident timeline (FR-10). */
public interface FieldUpdateRepository {

    FieldUpdate save(FieldUpdate fieldUpdate);

    List<FieldUpdate> findByIncidentId(String incidentId);
}
