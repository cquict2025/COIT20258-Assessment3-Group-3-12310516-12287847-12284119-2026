package au.edu.cqu.drs.service;

import au.edu.cqu.drs.model.Assessment;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.repository.AssessmentRepository;
import au.edu.cqu.drs.repository.IncidentRepository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Explainable prioritisation (UC-05, FR-04, FR-05).
 *
 * <p>Assessment 1 promised an explainable P1-P4 recommendation from bounded
 * factors but did not define the factor scales or the thresholds. The revised
 * specification defines them here so that the behaviour is testable:</p>
 *
 * <ul>
 *   <li>each factor is scored 0-5;</li>
 *   <li>severity = threat to life x 4 + scale x 3 + infrastructure impact x 2,
 *       giving a maximum of 45, normalised to a 0-100 score;</li>
 * <li>source confidence does not raise the score. Low confidence evidence must
 * not make an incident look more serious than it is, so a confidence below
 * the clarification floor sends the coordinator back for clarification.</li>
 * </ul>
 */
public class AssessmentService {

    /** Weight applied to the threat-to-life factor. */
    public static final int WEIGHT_THREAT_TO_LIFE = 4;
    /** Weight applied to the scale factor. */
    public static final int WEIGHT_SCALE = 3;
    /** Weight applied to the infrastructure impact factor. */
    public static final int WEIGHT_INFRASTRUCTURE = 2;
    /** Maximum achievable weighted total before normalisation. */
    public static final int MAX_WEIGHTED_TOTAL =
            Assessment.MAX_FACTOR * (WEIGHT_THREAT_TO_LIFE + WEIGHT_SCALE
                    + WEIGHT_INFRASTRUCTURE);

    /** Lowest normalised score that recommends P1. */
    public static final int THRESHOLD_P1 = 75;
    /** Lowest normalised score that recommends P2. */
    public static final int THRESHOLD_P2 = 50;
    /** Lowest normalised score that recommends P3. */
    public static final int THRESHOLD_P3 = 25;
    /** Confidence below this value requires clarification before scoring. */
    public static final int MIN_CONFIDENCE_TO_SCORE = 2;

    private final AssessmentRepository assessmentRepository;
    private final IncidentRepository incidentRepository;
    private final AuthenticationService authenticationService;
    private final AuditService auditService;

    public AssessmentService(AssessmentRepository assessmentRepository,
                             IncidentRepository incidentRepository,
                             AuthenticationService authenticationService,
                             AuditService auditService) {
        this.assessmentRepository = assessmentRepository;
        this.incidentRepository = incidentRepository;
        this.authenticationService = authenticationService;
        this.auditService = auditService;
    }

    /**
     * Normalises the three severity factors to a 0-100 score.
     *
     * @param threatToLife         0-5
     * @param scale                0-5
     * @param infrastructureImpact 0-5
     * @return the normalised score, rounded to the nearest whole number
     */
    public int calculateScore(int threatToLife, int scale,
                              int infrastructureImpact) {
        int weighted = threatToLife * WEIGHT_THREAT_TO_LIFE
                + scale * WEIGHT_SCALE
                + infrastructureImpact * WEIGHT_INFRASTRUCTURE;
        return (int) Math.round(weighted * 100.0 / MAX_WEIGHTED_TOTAL);
    }

    /**
     * Maps a normalised score to the recommended priority.
     *
     * @param score normalised score between 0 and 100
     * @return the recommended priority
     */
    public Priority recommendPriority(int score) {
        if (score >= THRESHOLD_P1) {
            return Priority.P1;
        }
        if (score >= THRESHOLD_P2) {
            return Priority.P2;
        }
        if (score >= THRESHOLD_P3) {
            return Priority.P3;
        }
        return Priority.P4;
    }

    /**
     * Scores an incident and stores the recommendation without confirming it.
     *
     * @param coordinator the signed-in coordinator
     * @param assessment  the factor values captured on the assessment screen
     * @return the scored assessment awaiting confirmation, or a typed failure
     */
    public ServiceResult<Assessment> score(User coordinator,
                                           Assessment assessment) {
        if (!this.authenticationService
                .isAuthorised(coordinator, UserRole.COORDINATOR)) {
            return ServiceResult.failure(ErrorCode.PERMISSION_DENIED,
                    "Only a coordinator can assess an incident.");
        }
        if (assessment == null) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                                         "No assessment was supplied.");
        }

        ServiceResult<Assessment> factorCheck = validateFactors(assessment);
        if (factorCheck.isFailure()) {
            return factorCheck;
        }

        Optional<Incident> incident =
                this.incidentRepository.findById(assessment.getIncidentId());
        if (incident.isEmpty()) {
            return ServiceResult.failure(ErrorCode.NOT_FOUND,
                                         "That incident no longer exists.");
        }
        if (assessment.getSourceConfidence() < MIN_CONFIDENCE_TO_SCORE) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Source confidence is too low to score. Request "
                            + "clarification first.",
                    "sourceConfidence");
        }

        int score = calculateScore(assessment.getThreatToLife(),
                                   assessment.getScale(), 
                assessment.getInfrastructureImpact());
        assessment.setCalculatedScore(score);
        assessment.setRecommendedPriority(recommendPriority(score));
        assessment.setAssessedBy(coordinator.getUsername());
        assessment.setAssessedAt(LocalDateTime.now());
        assessment.setVersion(this.assessmentRepository
                .findLatestForIncident(assessment.getIncidentId())
                .map(a -> a.getVersion() + 1).orElse(1));

        return
                ServiceResult.success(this.assessmentRepository
                        .save(assessment));
    }

    /**
     * Confirms a priority for an incident. The coordinator may accept the
     * recommendation or override it, but an override requires a reason (FR-05).
     *
     * @param coordinator      the signed-in coordinator
     * @param assessment       a previously scored assessment
     * @param confirmedPriority the priority the coordinator is confirming
     * @param overrideReason   the justification, required only for an override
     * @return the confirmed assessment, or a typed failure
     */
    public ServiceResult<Assessment> confirmPriority(User coordinator,
            Assessment assessment, 
                                                     Priority confirmedPriority,
                                                     String overrideReason) {
        if (!this.authenticationService
                .isAuthorised(coordinator, UserRole.COORDINATOR)) {
            return ServiceResult.failure(ErrorCode.PERMISSION_DENIED,
                    "Only a coordinator can confirm a priority.");
        }
        if (assessment == null || assessment.getRecommendedPriority() == null) {
            return ServiceResult.failure(ErrorCode.CONFLICT,
                    "Score the incident before confirming a priority.");
        }
        if (confirmedPriority == null) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Choose the priority you are confirming.",
                            "confirmedPriority");
        }

        boolean overriding =
                confirmedPriority != assessment.getRecommendedPriority();
        if (overriding
                && (overrideReason == null || overrideReason.isBlank())) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Give a reason when overriding the recommended priority.",
                            "overrideReason");
        }

        assessment.setConfirmedPriority(confirmedPriority);
        assessment.setOverrideReason(overriding ? overrideReason : null);
        Assessment saved = this.assessmentRepository.save(assessment);

        Optional<Incident> found =
                this.incidentRepository.findById(assessment.getIncidentId());
        if (found.isPresent()) {
            Incident incident = found.get();
            incident.setPriority(confirmedPriority);
            incident.incrementVersion();
            this.incidentRepository.save(incident);
        }

        this.auditService.record(coordinator.getUsername(), "CONFIRM_PRIORITY",
                            "Incident", 
                assessment.getIncidentId(),
                String.format("score=%d recommended=%s confirmed=%s%s",
                        saved.getCalculatedScore(),
                        saved.getRecommendedPriority().name(),
                        confirmedPriority.name(),
                        overriding ? " override: " + overrideReason : ""));
        return ServiceResult.success(saved);
    }

    public Optional<Assessment> findLatestForIncident(String incidentId) {
        return this.assessmentRepository.findLatestForIncident(incidentId);
    }

    private ServiceResult<Assessment> validateFactors(Assessment assessment) {
        if (isOutOfRange(assessment.getThreatToLife())) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    factorMessage("Threat to life"), "threatToLife");
        }
        if (isOutOfRange(assessment.getScale())) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    factorMessage("Scale"), "scale");
        }
        if (isOutOfRange(assessment.getInfrastructureImpact())) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    factorMessage("Infrastructure impact"),
                            "infrastructureImpact");
        }
        if (isOutOfRange(assessment.getSourceConfidence())) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    factorMessage("Source confidence"), "sourceConfidence");
        }
        return ServiceResult.success(assessment);
    }

    private boolean isOutOfRange(int factor) {
        return factor < Assessment.MIN_FACTOR || factor > Assessment.MAX_FACTOR;
    }

    private String factorMessage(String factorName) {
        return String.format("%s must be between %d and %d.",
                factorName, Assessment.MIN_FACTOR, Assessment.MAX_FACTOR);
    }
}
