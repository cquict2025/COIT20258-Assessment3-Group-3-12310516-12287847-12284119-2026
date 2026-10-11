package au.edu.cqu.drs.repository;

import au.edu.cqu.drs.model.Assessment;

import java.util.List;
import java.util.Optional;

/** Data-access contract for versioned incident assessments (FR-04, FR-05). */
public interface AssessmentRepository {

    Assessment save(Assessment assessment);

    List<Assessment> findByIncidentId(String incidentId);

    Optional<Assessment> findLatestForIncident(String incidentId);
}
