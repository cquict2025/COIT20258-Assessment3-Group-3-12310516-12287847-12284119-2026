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

    public DuplicateCandidate() {
    }

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

    public DisasterReport getCandidateReport() {
        return this.candidateReport;
    }

    public void setCandidateReport(DisasterReport candidateReport) {
        this.candidateReport = candidateReport;
    }

    public double getSimilarityScore() {
        return this.similarityScore;
    }

    public void setSimilarityScore(double similarityScore) {
        this.similarityScore = similarityScore;
    }

    public double getDistanceKm() {
        return this.distanceKm;
    }

    public void setDistanceKm(double distanceKm) {
        this.distanceKm = distanceKm;
    }

    public long getMinutesApart() {
        return this.minutesApart;
    }

    public void setMinutesApart(long minutesApart) {
        this.minutesApart = minutesApart;
    }

    public boolean isSameType() {
        return this.sameType;
    }

    public void setSameType(boolean sameType) {
        this.sameType = sameType;
    }

    /**
     * Human-readable justification shown beside the score on the review screen.
     */
    public String getMatchReason() {
        return String.format("%s, %.1f km apart, %d minute(s) apart",
                this.sameType ? "same disaster type" :
                        "different disaster type",
                this.distanceKm,
                this.minutesApart);
    }

    @Override
    public String toString() {
        return String.format("Duplicate candidate %s | similarity %.2f | %s",
                this.candidateReport == null ? "unknown" : this.candidateReport
                        .getReportId(),
                this.similarityScore,
                getMatchReason());
    }
}
