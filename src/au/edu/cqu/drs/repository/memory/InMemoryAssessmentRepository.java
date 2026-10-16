package au.edu.cqu.drs.repository.memory;

import au.edu.cqu.drs.model.Assessment;
import au.edu.cqu.drs.repository.AssessmentRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** In-memory test double for versioned assessments. */
public class InMemoryAssessmentRepository implements AssessmentRepository {

    private final List<Assessment> assessments = new ArrayList<>();
    private int sequence = 0;

    /**
     * Stores an assessment, replacing any stored one with the same
     * identifier. A new assessment is given the next ASM- identifier.
     *
     * @param assessment the assessment to store
     * @return the same assessment, with its identifier set
     */
    @Override
    public Assessment save(Assessment assessment) {
        if (assessment.getAssessmentId() == null) {
            this.sequence++;
            assessment.setAssessmentId(String.format("ASM-%04d",
                    this.sequence));
        }
        this.assessments.removeIf(a -> a.getAssessmentId()
                .equals(assessment.getAssessmentId()));
        this.assessments.add(assessment);
        return assessment;
    }

    /**
     * Finds the assessments recorded for an incident, in the order they
     * were stored.
     *
     * @param incidentId the incident identifier
     * @return the assessments for the incident
     */
    @Override
    public List<Assessment> findByIncidentId(String incidentId) {
        return this.assessments.stream()
                .filter(a -> incidentId.equals(a.getIncidentId()))
                .toList();
    }

    /**
     * Finds the assessment with the highest version number for an
     * incident.
     *
     * @param incidentId the incident identifier
     * @return the latest assessment, or empty if there is none
     */
    @Override
    public Optional<Assessment> findLatestForIncident(String incidentId) {
        return findByIncidentId(incidentId).stream()
                .max(Comparator.comparingInt(Assessment::getVersion));
    }

    /**
     * Describes the repository.
     *
     * @return how many assessments it holds
     */
    @Override
    public String toString() {
        return "InMemoryAssessmentRepository: " + this.assessments.size()
                + " assessment(s)";
    }
}
