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

    @Override
    public Incident save(Incident incident) {
        if (incident.getIncidentId() == null) {
            this.sequence++;
            incident.setIncidentId(String.format("INC-%04d", this.sequence));
        }
        this.incidentsById.put(incident.getIncidentId(), incident);
        return incident;
    }

    @Override
    public Optional<Incident> findById(String incidentId) {
        return Optional.ofNullable(this.incidentsById.get(incidentId));
    }

    @Override
    public List<Incident> findAll() {
        return new ArrayList<>(this.incidentsById.values());
    }

    @Override
    public List<Incident> findByStatus(IncidentStatus status) {
        return this.incidentsById.values().stream()
                .filter(i -> i.getStatus() == status)
                .toList();
    }
}
