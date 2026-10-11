package au.edu.cqu.drs.model;

import java.time.LocalDateTime;

/**
 * A scored, versioned judgement about one incident (UC-05, FR-04, FR-05).
 * The four factors are recorded alongside the calculated score so that the
 * recommendation remains explainable, which was the central design commitment
 * of Assessment 1: the system recommends, a coordinator decides.
 */
public class Assessment {

    /** Lowest value accepted for any assessment factor. */
    public static final int MIN_FACTOR = 0;
    /** Highest value accepted for any assessment factor. */
    public static final int MAX_FACTOR = 5;

    private String assessmentId;
    private String incidentId;
    private int threatToLife;
    private int scale;
    private int infrastructureImpact;
    private int sourceConfidence;
    private int calculatedScore;
    private Priority recommendedPriority;
    private Priority confirmedPriority;
    private String overrideReason;
    private String assessedBy;
    private LocalDateTime assessedAt;
    private int version;

    public Assessment() {
        this.version = 1;
    }

    public Assessment(String incidentId, int threatToLife, int scale,
                      int infrastructureImpact, int sourceConfidence) {
        this();
        this.incidentId = incidentId;
        this.threatToLife = threatToLife;
        this.scale = scale;
        this.infrastructureImpact = infrastructureImpact;
        this.sourceConfidence = sourceConfidence;
    }

    public String getAssessmentId() {
        return this.assessmentId;
    }

    public void setAssessmentId(String assessmentId) {
        this.assessmentId = assessmentId;
    }

    public String getIncidentId() {
        return this.incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public int getThreatToLife() {
        return this.threatToLife;
    }

    public void setThreatToLife(int threatToLife) {
        this.threatToLife = threatToLife;
    }

    public int getScale() {
        return this.scale;
    }

    public void setScale(int scale) {
        this.scale = scale;
    }

    public int getInfrastructureImpact() {
        return this.infrastructureImpact;
    }

    public void setInfrastructureImpact(int infrastructureImpact) {
        this.infrastructureImpact = infrastructureImpact;
    }

    public int getSourceConfidence() {
        return this.sourceConfidence;
    }

    public void setSourceConfidence(int sourceConfidence) {
        this.sourceConfidence = sourceConfidence;
    }

    public int getCalculatedScore() {
        return this.calculatedScore;
    }

    public void setCalculatedScore(int calculatedScore) {
        this.calculatedScore = calculatedScore;
    }

    public Priority getRecommendedPriority() {
        return this.recommendedPriority;
    }

    public void setRecommendedPriority(Priority recommendedPriority) {
        this.recommendedPriority = recommendedPriority;
    }

    public Priority getConfirmedPriority() {
        return this.confirmedPriority;
    }

    public void setConfirmedPriority(Priority confirmedPriority) {
        this.confirmedPriority = confirmedPriority;
    }

    public String getOverrideReason() {
        return this.overrideReason;
    }

    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }

    public String getAssessedBy() {
        return this.assessedBy;
    }

    public void setAssessedBy(String assessedBy) {
        this.assessedBy = assessedBy;
    }

    public LocalDateTime getAssessedAt() {
        return this.assessedAt;
    }

    public void setAssessedAt(LocalDateTime assessedAt) {
        this.assessedAt = assessedAt;
    }

    public int getVersion() {
        return this.version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * True when the coordinator confirmed a priority other than the
     * recommendation.
     */
    public boolean isOverridden() {
        return this.confirmedPriority != null
                && this.confirmedPriority != this.recommendedPriority;
    }

    @Override
    public String toString() {
        return String.format(
                "Assessment %s v%d | incident %s | life=%d scale=%d infra=%d "
                        + "confidence=%d "
                + "| score=%d | recommended %s | confirmed %s%s",
                this.assessmentId == null ? "unsaved" : this.assessmentId,
                this.version,
                this.incidentId,
                this.threatToLife, this.scale, this.infrastructureImpact,
                        this.sourceConfidence,
                this.calculatedScore,
                this.recommendedPriority == null ? "none" :
                        this.recommendedPriority
                        .name(),
                this.confirmedPriority == null ? "not confirmed" :
                        this.confirmedPriority
                        .name(),
                isOverridden() ? " (overridden: " + this.overrideReason + ")" :
                        "");
    }
}
