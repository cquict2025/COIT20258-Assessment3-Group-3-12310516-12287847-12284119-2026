package au.edu.cqu.drs.repository;

import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.IncidentStatus;

import java.util.List;
import java.util.Optional;

/** Data-access contract for incidents (FR-06, FR-11). */
public interface IncidentRepository {

    /**
     * Saves an incident, giving it an identifier if it has none yet.
     *
     * @param incident the incident to save
     * @return the saved incident, with its identifier set
     */
    Incident save(Incident incident);

    /**
     * Finds an incident by its identifier.
     *
     * @param incidentId the incident identifier
     * @return the incident, or empty if there is none
     */
    Optional<Incident> findById(String incidentId);

    /**
     * Lists every stored incident.
     *
     * @return all incidents
     */
    List<Incident> findAll();

    /**
     * Finds the incidents that have a given status.
     *
     * @param status the status to match
     * @return the incidents with that status
     */
    List<Incident> findByStatus(IncidentStatus status);
}
