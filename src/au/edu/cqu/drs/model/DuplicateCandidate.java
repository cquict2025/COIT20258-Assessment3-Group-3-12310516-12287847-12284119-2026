package au.edu.cqu.drs.model;

import java.io.Serializable;

/**
 * A report that resembles another closely enough to warrant a coordinator's
 * attention, together with the reasons for the match (UC-04, creative
 * feature 2). The class exists so that the screen can explain a match instead
 * of presenting an unexplained score.
 */
public class DuplicateCandidate implements Serializable {

    /** Version of the serialised form sent between client and server. */
    private static final long serialVersionUID = 1L;

    private DisasterReport candidateReport;
    private double similarityScore;
    private double distanceKm;
    private long minutesApart;
    private boolean sameType;

    /**
     * Creates an empty duplicate candidate.
     */
    public DuplicateCandidate() {
    }

    /**
     * Creates a duplicate candidate.
     *
     * @param candidateReport the candidate report
     * @param similarityScore the similarity score
     * @param distanceKm      the distance km
     * @param minutesApart    the minutes apart
     * @param sameType        the same type
     */
    public DuplicateCandidate(DisasterReport candidateReport,
                              double similarityScore, 
                              double distanceKm, long minutesApart, boolean
                                      sameType) {
        this.candidateReport = candidateReport;
        this.similarityScore = similarityScore;
        this.distanceKm = distanceKm;
        this.minutesApart = minutesApart;
        this.sameType = sameType;
    }

    /**
     * Returns the candidate report.
     *
     * @return the candidate report
     */
    public DisasterReport getCandidateReport() {
        return this.candidateReport;
    }

    /**
     * Sets the candidate report.
     *
     * @param candidateReport the candidate report
     */
    public void setCandidateReport(DisasterReport candidateReport) {
        this.candidateReport = candidateReport;
    }

    /**
     * Returns the similarity score.
     *
     * @return the similarity score
     */
    public double getSimilarityScore() {
        return this.similarityScore;
    }

    /**
     * Sets the similarity score.
     *
     * @param similarityScore the similarity score
     */
    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }

    /**
     * Returns the distance km.
     *
     * @return the distance km
     */
    public double getDistanceKm() {
        return this.distanceKm;
    }

    /**
     * Sets the distance km.
     *
     * @param distanceKm the distance km
     */
    public void setDistanceKm(double distanceKm) {
        this.distanceKm = distanceKm;
    }

    /**
     * Returns the minutes apart.
     *
     * @return the minutes apart
     */
    public long getMinutesApart() {
        return this.minutesApart;
    }

    /**
     * Sets the minutes apart.
     *
     * @param minutesApart the minutes apart
     */
    public void setMinutesApart(long minutesApart) {
        this.minutesApart = minutesApart;
    }

    /**
     * Tells whether this is same type.
     *
     * @return {@code true} if it is same type
     */
    public boolean isSameType() {
        return this.sameType;
    }

    /**
     * Sets the same type.
     *
     * @param sameType the same type
     */
    public void setSameType(boolean sameType) {
        this.sameType = sameType;
    }

    /**
     * Returns the human-readable justification shown beside the score on
     * the review screen.
     *
     * @return the type match, distance and time apart
     */
    public String getMatchReason() {
        return String.format("%s, %.1f km apart, %d minute(s) apart",
                this.sameType ? "same disaster type" :
                        "different disaster type",
                this.distanceKm,
                this.minutesApart);
    }

    /**
     * Describes the candidate report, its similarity score and why
     * it matched.
     *
     * @return a one-line summary of this candidate
     */
    @Override
    public String toString() {
        return String.format("Duplicate candidate %s | similarity %.2f | %s",
                this.candidateReport == null ? "unknown" : this.candidateReport
                        .getReportId(),
                this.similarityScore,
                getMatchReason());
    }
}
