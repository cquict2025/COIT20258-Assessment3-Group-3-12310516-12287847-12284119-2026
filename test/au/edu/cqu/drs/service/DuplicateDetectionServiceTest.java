package au.edu.cqu.drs.service;

import au.edu.cqu.drs.TestFixtures;
import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.DuplicateCandidate;
import au.edu.cqu.drs.model.ReportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for creative feature 2, duplicate report detection.
 * The three scoring signals are tested in isolation and then together, and the
 * candidate threshold is tested from both sides.
 */
class DuplicateDetectionServiceTest {

    private static final LocalDateTime OBSERVED = LocalDateTime.of(2026, 9, 17,
            11, 0);
    private static final double TOLERANCE = 0.001;

    private TestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures();
    }

    @Test
    @DisplayName("TC-DD-01 two identical reports score 1.0")
    void identicalReportsScoreOne() {
        DisasterReport first = report(DisasterType.FIRE, -37.8600, 144.7700,
                                      OBSERVED);
        DisasterReport second = report(DisasterType.FIRE, -37.8600, 144.7700,
                                       OBSERVED);

        assertEquals(1.0,
                     fixtures.duplicateDetectionService.similarity(first,
                             second),
                     TOLERANCE);
    }

    @Test
    @DisplayName("TC-DD-02 a different type removes exactly the type weight")
    void differentTypeRemovesTypeWeight() {
        DisasterReport first = report(DisasterType.FIRE, -37.8600, 144.7700,
                                      OBSERVED);
        DisasterReport second = report(DisasterType.FLOOD, -37.8600, 144.7700,
                                       OBSERVED);

        assertEquals(1.0 - DuplicateDetectionService.WEIGHT_TYPE,
                fixtures.duplicateDetectionService.similarity(first,
                        second), TOLERANCE);
    }

    @Test
    @DisplayName("TC-DD-03 a report beyond the proximity radius loses the "
            + "whole proximity weight")
    void distantReportLosesProximityWeight() {
        DisasterReport first = report(DisasterType.FIRE, -37.8600, 144.7700,
                                      OBSERVED);
        DisasterReport second = report(DisasterType.FIRE, -38.2000, 144.7700,
                                       OBSERVED);

        assertEquals(1.0 - DuplicateDetectionService.WEIGHT_PROXIMITY,
                fixtures.duplicateDetectionService.similarity(first,
                        second), TOLERANCE);
    }

    @Test
    @DisplayName("TC-DD-04 a report outside the time window loses the whole "
            + "recency weight")
    void oldReportLosesRecencyWeight() {
        DisasterReport first = report(DisasterType.FIRE, -37.8600, 144.7700,
                                      OBSERVED);
        DisasterReport second = report(DisasterType.FIRE, -37.8600, 144.7700,
                OBSERVED.minusMinutes(DuplicateDetectionService
                        .TIME_WINDOW_MINUTES));

        assertEquals(1.0 - DuplicateDetectionService.WEIGHT_RECENCY,
                fixtures.duplicateDetectionService.similarity(first,
                        second), TOLERANCE);
    }

    @Test
    @DisplayName("TC-DD-05 a report of a different type at a different place "
            + "is not a candidate")
    void unrelatedReportIsNotACandidate() {
        submit(DisasterType.FLOOD, -38.2000, 145.2000, OBSERVED.minusHours(5));
        DisasterReport newReport = submit(DisasterType.FIRE, -37.8600, 144.7700,
                                          OBSERVED);

        assertTrue(fixtures.duplicateDetectionService.findCandidates(newReport)
                .isEmpty());
    }

    @Test
    @DisplayName("TC-DD-06 a nearby report of the same type minutes later is "
            + "offered as a candidate")
    void nearbyRecentReportIsACandidate() {
        DisasterReport original = submit(DisasterType.FIRE, -37.8600, 144.7700,
                OBSERVED.minusMinutes(20));
        DisasterReport newReport = submit(DisasterType.FIRE, -37.8610, 144.7710,
                                          OBSERVED);

        List<DuplicateCandidate> candidates =
                fixtures.duplicateDetectionService.findCandidates(newReport);

        assertEquals(1, candidates.size());
        assertEquals(original.getReportId(),
                     candidates.get(0).getCandidateReport().getReportId());
        assertTrue(candidates.get(0).getSimilarityScore()
                >= DuplicateDetectionService.CANDIDATE_THRESHOLD);
        assertTrue(candidates.get(0).isSameType());
    }

    @Test
    @DisplayName("TC-DD-07 candidates are returned strongest match first")
    void candidatesAreOrderedByScore() {
        submit(DisasterType.FIRE, -37.8800, 144.7900,
               OBSERVED.minusMinutes(200));
        DisasterReport closest = submit(DisasterType.FIRE, -37.8601, 144.7701,
                OBSERVED.minusMinutes(5));
        DisasterReport newReport = submit(DisasterType.FIRE, -37.8600, 144.7700,
                                          OBSERVED);

        List<DuplicateCandidate> candidates =
                fixtures.duplicateDetectionService.findCandidates(newReport);

        assertTrue(candidates.size() >= 2);
        assertEquals(closest.getReportId(),
                     candidates.get(0).getCandidateReport().getReportId());
        assertTrue(candidates.get(0).getSimilarityScore() > candidates.get(1)
                .getSimilarityScore());
    }

    @Test
    @DisplayName("TC-DD-08 a rejected report is never offered as a duplicate")
    void rejectedReportIsExcluded() {
        DisasterReport rejected = submit(DisasterType.FIRE, -37.8600, 144.7700,
                OBSERVED.minusMinutes(10));
        rejected.setStatus(ReportStatus.REJECTED);
        fixtures.reportRepository.save(rejected);
        DisasterReport newReport = submit(DisasterType.FIRE, -37.8600, 144.7700,
                                          OBSERVED);

        assertTrue(fixtures.duplicateDetectionService.findCandidates(newReport)
                .isEmpty());
    }

    @Test
    @DisplayName("TC-DD-09 a report is never a duplicate of itself")
    void reportIsNeverItsOwnDuplicate() {
        DisasterReport report = submit(DisasterType.FIRE, -37.8600, 144.7700,
                                       OBSERVED);

        assertTrue(fixtures.duplicateDetectionService.findCandidates(report)
                .stream()
                .noneMatch(c -> c.getCandidateReport().getReportId()
                        .equals(report.getReportId())));
    }

    @Test
    @DisplayName("TC-DD-10 detection only recommends: the candidate report "
            + "keeps its own status")
    void detectionDoesNotMergeReports() {
        submit(DisasterType.FIRE, -37.8600, 144.7700,
               OBSERVED.minusMinutes(10));
        DisasterReport newReport = submit(DisasterType.FIRE, -37.8600, 144.7700,
                                          OBSERVED);

        List<DuplicateCandidate> candidates =
                fixtures.duplicateDetectionService.findCandidates(newReport);

        assertFalse(candidates.isEmpty());
        assertEquals(ReportStatus.SUBMITTED,
                     candidates.get(0).getCandidateReport().getStatus(), 
                "A coordinator must confirm a link; nothing may be merged "
                        + "automatically");
    }

    private DisasterReport report(DisasterType type, double latitude,
                                  double longitude, 
                                  LocalDateTime observedAt) {
        return fixtures.validReport(type, "Laverton", latitude, longitude,
                                    observedAt);
    }

    private DisasterReport submit(DisasterType type, double latitude,
                                  double longitude, 
                                  LocalDateTime observedAt) {
        return fixtures.reportService
                .submitReport(report(type, latitude, longitude, observedAt))
                .getValue();
    }
}
