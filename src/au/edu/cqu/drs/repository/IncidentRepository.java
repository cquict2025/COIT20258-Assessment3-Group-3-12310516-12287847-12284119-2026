package au.edu.cqu.drs.repository;

import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.IncidentStatus;

import java.util.List;
import java.util.Optional;

/** Data-access contract for incidents (FR-06, FR-11). */
public interface IncidentRepository {

    Incident save(Incident incident);

    Optional<Incident> findById(String incidentId);

    List<Incident> findAll();

    List<Incident> findByStatus(IncidentStatus status);
}
