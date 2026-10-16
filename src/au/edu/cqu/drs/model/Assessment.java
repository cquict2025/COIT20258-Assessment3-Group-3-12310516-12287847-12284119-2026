package au.edu.cqu.drs.model;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * A scored, versioned judgement about one incident (UC-05, FR-04, FR-05).
 * The four factors are recorded alongside the calculated score so that the
 * recommendation remains explainable, which was the central design commitment
 * of Assessment 1: the system recommends, a coordinator decides.
 */
public class Assessment implements Serializable {

    /** Version of the serialised form sent between client and server. */
    private static final long serialVersionUID = 1L;

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

    /**
     * Creates an empty assessment.
     */
    public Assessment() {
        this.version = 1;
    }

    /**
     * Creates an assessment.
     *
     * @param incidentId           the incident id
     * @param threatToLife         the threat to life
     * @param scale                the scale
     * @param infrastructureImpact the infrastructure impact
     * @param sourceConfidence     the source confidence
     */
    public Assessment(String incidentId, int threatToLife, int scale,
                      int infrastructureImpact, int sourceConfidence) {
        this();
        this.incidentId = incidentId;
        this.threatToLife = threatToLife;
        this.scale = scale;
        this.infrastructureImpact = infrastructureImpact;
        this.sourceConfidence = sourceConfidence;
    }

    /**
     * Returns the assessment id.
     *
     * @return the assessment id
     */
    public String getAssessmentId() {
        return this.assessmentId;
    }

    /**
     * Sets the assessment id.
     *
     * @param assessmentId the assessment id
     */
    public void setAssessmentId(String assessmentId) {
        this.assessmentId = assessmentId;
    }

    /**
     * Returns the incident id.
     *
     * @return the incident id
     */
    public String getIncidentId() {
        return this.incidentId;
    }

    /**
     * Sets the incident id.
     *
     * @param incidentId the incident id
     */
    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    /**
     * Returns the threat to life.
     *
     * @return the threat to life
     */
    public int getThreatToLife() {
        return this.threatToLife;
    }

    /**
     * Sets the threat to life.
     *
     * @param threatToLife the threat to life
     */
    public void setThreatToLife(int threatToLife) {
        this.threatToLife = threatToLife;
    }

    /**
     * Returns the scale.
     *
     * @return the scale
     */
    public int getScale() {
        return this.scale;
    }

    /**
     * Sets the scale.
     *
     * @param scale the scale
     */
    public void setScale(int scale) {
        this.scale = scale;
    }

    /**
     * Returns the infrastructure impact.
     *
     * @return the infrastructure impact
     */
    public int getInfrastructureImpact() {
        return this.infrastructureImpact;
    }

    /**
     * Sets the infrastructure impact.
     *
     * @param infrastructureImpact the infrastructure impact
     */
    public void setInfrastructureImpact(int infrastructureImpact) {
        this.infrastructureImpact = infrastructureImpact;
    }

    /**
     * Returns the source confidence.
     *
     * @return the source confidence
     */
    public int getSourceConfidence() {
        return this.sourceConfidence;
    }

    /**
     * Sets the source confidence.
     *
     * @param sourceConfidence the source confidence
     */
    public void setSourceConfidence(int sourceConfidence) {
        this.sourceConfidence = sourceConfidence;
    }

    /**
     * Returns the calculated score.
     *
     * @return the calculated score
     */
    public int getCalculatedScore() {
        return this.calculatedScore;
    }

    /**
     * Sets the calculated score.
     *
     * @param calculatedScore the calculated score
     */
    public void setCalculatedScore(int calculatedScore) {
        this.calculatedScore = calculatedScore;
    }

    /**
     * Returns the recommended priority.
     *
     * @return the recommended priority
     */
    public Priority getRecommendedPriority() {
        return this.recommendedPriority;
    }

    /**
     * Sets the recommended priority.
     *
     * @param recommendedPriority the recommended priority
     */
    public void setRecommendedPriority(Priority recommendedPriority) {
        this.recommendedPriority = recommendedPriority;
    }

    /**
     * Returns the confirmed priority.
     *
     * @return the confirmed priority
     */
    public Priority getConfirmedPriority() {
        return this.confirmedPriority;
    }

    /**
     * Sets the confirmed priority.
     *
     * @param confirmedPriority the confirmed priority
     */
    public void setConfirmedPriority(Priority confirmedPriority) {
        this.confirmedPriority = confirmedPriority;
    }

    /**
     * Returns the override reason.
     *
     * @return the override reason
     */
    public String getOverrideReason() {
        return this.overrideReason;
    }

    /**
     * Sets the override reason.
     *
     * @param overrideReason the override reason
     */
    public void setOverrideReason(String overrideReason) {
        this.overrideReason = overrideReason;
    }

    /**
     * Returns the assessed by.
     *
     * @return the assessed by
     */
    public String getAssessedBy() {
        return this.assessedBy;
    }

    /**
     * Sets the assessed by.
     *
     * @param assessedBy the assessed by
     */
    public void setAssessedBy(String assessedBy) {
        this.assessedBy = assessedBy;
    }

    /**
     * Returns the assessed at.
     *
     * @return the assessed at
     */
    public LocalDateTime getAssessedAt() {
        return this.assessedAt;
    }

    /**
     * Sets the assessed at.
     *
     * @param assessedAt the assessed at
     */
    public void setAssessedAt(LocalDateTime assessedAt) {
        this.assessedAt = assessedAt;
    }

    /**
     * Returns the version.
     *
     * @return the version
     */
    public int getVersion() {
        return this.version;
    }

    /**
     * Sets the version.
     *
     * @param version the version
     */
    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * Tells whether the coordinator confirmed a priority other than the
     * recommendation.
     *
     * @return {@code true} if the recommendation was overridden
     */
    public boolean isOverridden() {
        return this.confirmedPriority != null
                && this.confirmedPriority != this.recommendedPriority;
    }

    /**
     * Describes the assessment: its factors, score, recommended and
     * confirmed priority, and any override reason.
     *
     * @return a one-line summary of this assessment
     */
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
