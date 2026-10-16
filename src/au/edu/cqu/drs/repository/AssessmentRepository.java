package au.edu.cqu.drs.repository;

import au.edu.cqu.drs.model.Assessment;

import java.util.List;
import java.util.Optional;

/** Data-access contract for versioned incident assessments (FR-04, FR-05). */
public interface AssessmentRepository {

    /**
     * Saves an assessment version, giving it an identifier if it has none
     * yet.
     *
     * @param assessment the assessment to save
     * @return the saved assessment, with its identifier set
     */
    Assessment save(Assessment assessment);

    /**
     * Finds every assessment version recorded for an incident.
     *
     * @param incidentId the incident identifier
     * @return the assessments for the incident
     */
    List<Assessment> findByIncidentId(String incidentId);

    /**
     * Finds the assessment with the highest version number for an incident.
     *
     * @param incidentId the incident identifier
     * @return the latest assessment, or empty if there is none
     */
    Optional<Assessment> findLatestForIncident(String incidentId);
}
