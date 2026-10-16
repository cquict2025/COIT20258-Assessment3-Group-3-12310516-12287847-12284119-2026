package au.edu.cqu.drs.repository.memory;

import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.IncidentStatus;
import au.edu.cqu.drs.repository.IncidentRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory test double for incidents. */
public class InMemoryIncidentRepository implements IncidentRepository {

    private final Map<String, Incident> incidentsById = new LinkedHashMap<>();
    private int sequence = 0;

    /**
     * Stores an incident, replacing any stored one with the same
     * identifier. A new incident is given the next INC- identifier.
     *
     * @param incident the incident to store
     * @return the same incident, with its identifier set
     */
    @Override
    public Incident save(Incident incident) {
        if (incident.getIncidentId() == null) {
            this.sequence++;
            incident.setIncidentId(String.format("INC-%04d", this.sequence));
        }
        this.incidentsById.put(incident.getIncidentId(), incident);
        return incident;
    }

    /**
     * Finds an incident by its identifier.
     *
     * @param incidentId the incident identifier
     * @return the incident, or empty if there is none
     */
    @Override
    public Optional<Incident> findById(String incidentId) {
        return Optional.ofNullable(this.incidentsById.get(incidentId));
    }

    /**
     * Lists every stored incident.
     *
     * @return a new list of all incidents, in the order stored
     */
    @Override
    public List<Incident> findAll() {
        return new ArrayList<>(this.incidentsById.values());
    }

    /**
     * Finds the incidents that have a given status.
     *
     * @param status the status to match
     * @return the incidents with that status
     */
    @Override
    public List<Incident> findByStatus(IncidentStatus status) {
        return this.incidentsById.values().stream()
                .filter(i -> i.getStatus() == status)
                .toList();
    }

    /**
     * Describes the repository.
     *
     * @return how many incidents it holds
     */
    @Override
    public String toString() {
        return "InMemoryIncidentRepository: " + this.incidentsById.size()
                + " incident(s)";
    }
}
