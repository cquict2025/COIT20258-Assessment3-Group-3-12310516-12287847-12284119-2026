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

    @Override
    public List<Assessment> findByIncidentId(String incidentId) {
        return this.assessments.stream()
                .filter(a -> incidentId.equals(a.getIncidentId()))
                .toList();
    }

    @Override
    public Optional<Assessment> findLatestForIncident(String incidentId) {
        return findByIncidentId(incidentId).stream()
                .max(Comparator.comparingInt(Assessment::getVersion));
    }
}
