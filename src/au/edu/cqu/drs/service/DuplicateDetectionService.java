package au.edu.cqu.drs.service;

import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.DuplicateCandidate;
import au.edu.cqu.drs.model.ReportStatus;
import au.edu.cqu.drs.repository.ReportRepository;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

/**
 * Creative feature 2: duplicate report detection with similarity scoring.
 *
 * <p>A single fire produces many calls. This service surfaces earlier reports
 * that resemble a new one, scoring three comparable signals:</p>
 *
 * <ul>
 *   <li>disaster type match, weighted 0.4;</li>
 *   <li>distance between the two locations, weighted 0.4 and decaying linearly
 *       to zero at the proximity radius;</li>
 * <li>time between the two observations, weighted 0.2 and decaying linearly to
 *       zero at the end of the time window.</li>
 * </ul>
 *
 * <p>The service only ever recommends. Assessment 1 (UC-04) requires a human to
 * confirm a link, because automatically merging two genuinely separate
 * disasters
 * would hide one of them.</p>
 */
public class DuplicateDetectionService {

    /** Weight of a disaster-type match in the similarity score. */
    public static final double WEIGHT_TYPE = 0.4;
    /** Weight of location proximity in the similarity score. */
    public static final double WEIGHT_PROXIMITY = 0.4;
    /** Weight of time proximity in the similarity score. */
    public static final double WEIGHT_RECENCY = 0.2;

    /**
     * Distance in kilometres beyond which two reports score zero for proximity.
     */
    public static final double PROXIMITY_RADIUS_KM = 5.0;
    /** Minutes beyond which two reports score zero for recency. */
    public static final long TIME_WINDOW_MINUTES = 360;
    /**
     * Lowest score at which a report is offered to the coordinator as a
     * candidate.
     */
    public static final double CANDIDATE_THRESHOLD = 0.60;

    private final ReportRepository reportRepository;

    public DuplicateDetectionService(ReportRepository reportRepository) {
        this.reportRepository = reportRepository;
    }

    /**
     * Scores the similarity of two reports between 0.0 and 1.0.
     *
     * @param first  the new report
     * @param second the report being compared against
     * @return the similarity score
     */
    public double similarity(DisasterReport first, DisasterReport second) {
        if (first == null || second == null) {
            return 0.0;
        }

        double typeScore =
                first.getDisasterType() == second.getDisasterType() ? 1.0 : 0.0;

        double distanceKm = first.getLocation() == null
                ? Double.MAX_VALUE
                : first.getLocation().distanceKmTo(second.getLocation());
        double proximityScore = Math.max(0.0,
                1.0 - distanceKm / PROXIMITY_RADIUS_KM);

        long minutesApart = minutesBetween(first, second);
        double recencyScore = Math.max(0.0,
                1.0 - (double) minutesApart / TIME_WINDOW_MINUTES);

        return WEIGHT_TYPE * typeScore
                + WEIGHT_PROXIMITY * proximityScore
                + WEIGHT_RECENCY * recencyScore;
    }

    /**
     * Finds earlier reports that may describe the same event.
     *
     * @param report the newly submitted report
     * @return candidates at or above the threshold, strongest match first
     */
    public List<DuplicateCandidate> findCandidates(DisasterReport report) {
        if (report == null) {
            return List.of();
        }
        return this.reportRepository.findAll().stream()
                .filter(other -> other.getReportId() != null)
                .filter(other -> !other.getReportId()
                        .equals(report.getReportId()))
                .filter(other -> other.getStatus() != ReportStatus.REJECTED
                        && other.getStatus() != ReportStatus.DRAFT)
                .map(other -> toCandidate(report, other))
                .filter(candidate -> candidate
                        .getSimilarityScore() >= CANDIDATE_THRESHOLD)
                .sorted(Comparator
                        .comparingDouble(
                                DuplicateCandidate::getSimilarityScore)
                        .reversed())
                .toList();
    }

    private DuplicateCandidate toCandidate(DisasterReport report,
                                           DisasterReport other) {
        double distanceKm = report.getLocation() == null
                ? Double.MAX_VALUE
                : report.getLocation().distanceKmTo(other.getLocation());
        return new DuplicateCandidate(
                other,
                similarity(report, other),
                distanceKm == Double.MAX_VALUE ? -1 : distanceKm,
                minutesBetween(report, other),
                report.getDisasterType() == other.getDisasterType());
    }

    private long minutesBetween(DisasterReport first, DisasterReport second) {
        if (first.getObservedAt() == null || second.getObservedAt() == null) {
            return TIME_WINDOW_MINUTES;
        }
        return Math.abs(Duration.between(first.getObservedAt(),
                second.getObservedAt()).toMinutes());
    }
}
