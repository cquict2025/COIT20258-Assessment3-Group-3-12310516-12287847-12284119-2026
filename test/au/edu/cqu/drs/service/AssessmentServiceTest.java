package au.edu.cqu.drs.service;

import au.edu.cqu.drs.TestFixtures;
import au.edu.cqu.drs.model.Assessment;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the explainable prioritisation rules (FR-04, FR-05).
 * Test cases are chosen by equivalence partitioning across the score ranges and
 * by testing the value on each side of every threshold boundary.
 */
class AssessmentServiceTest {

    private TestFixtures fixtures;
    private User coordinator;
    private Incident incident;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures();
        coordinator = fixtures.createUser("coordinator", UserRole.COORDINATOR);
        incident = new Incident(null, "Warehouse fire - Laverton",
                                DisasterType.FIRE, 
                new Location("2 Dock Road", "Laverton", -37.8636, 144.7700),
                LocalDateTime.now().minusMinutes(5));
        incident = fixtures.incidentRepository.save(incident);
    }

    @Test
    @DisplayName("TC-AS-01 the maximum factor values normalise to a score of "
            + "100")
    void maximumFactorsScoreOneHundred() {
        assertEquals(100, fixtures.assessmentService.calculateScore(5, 5, 5));
    }

    @Test
    @DisplayName("TC-AS-02 the minimum factor values normalise to a score of "
            + "zero")
    void minimumFactorsScoreZero() {
        assertEquals(0, fixtures.assessmentService.calculateScore(0, 0, 0));
    }

    @ParameterizedTest(name = "TC-AS-03 score {0} maps to {1}")
    @CsvSource({
            "100, P1",
            "75, P1",
            "74, P2",
            "50, P2",
            "49, P3",
            "25, P3",
            "24, P4",
            "0, P4"
    })
    @DisplayName("TC-AS-03 each threshold boundary maps to the documented "
            + "priority")
    void thresholdBoundariesMapCorrectly(int score, String expectedPriority) {
        assertEquals(Priority.valueOf(expectedPriority),
                fixtures.assessmentService.recommendPriority(score));
    }

    @Test
    @DisplayName("TC-AS-04 scoring stores the recommendation but does not "
            + "confirm a priority")
    void scoringDoesNotConfirmPriority() {
        Assessment assessment = new Assessment(incident.getIncidentId(), 5, 4,
                                               3, 4);

        ServiceResult<Assessment> result =
                fixtures.assessmentService.score(coordinator, assessment);

        assertTrue(result.isSuccess());
        assertEquals(84, result.getValue().getCalculatedScore());
        assertEquals(Priority.P1, result.getValue().getRecommendedPriority());
        assertNull(result.getValue().getConfirmedPriority(),
                "A recommendation must not confirm itself");
    }

    @Test
    @DisplayName("TC-AS-05 a factor above the permitted range is rejected with "
            + "the field named")
    void factorAboveRangeIsRejected() {
        Assessment assessment = new Assessment(incident.getIncidentId(), 6, 3,
                                               3, 4);

        ServiceResult<Assessment> result =
                fixtures.assessmentService.score(coordinator, assessment);

        assertTrue(result.isFailure());
        assertEquals(ErrorCode.VALIDATION_ERROR, result.getErrorCode());
        assertEquals("threatToLife", result.getAffectedField());
    }

    @Test
    @DisplayName("TC-AS-06 confidence below the clarification floor blocks "
            + "scoring")
    void lowConfidenceRequiresClarification() {
        Assessment assessment = new Assessment(incident.getIncidentId(), 5, 5,
                                               5, 1);

        ServiceResult<Assessment> result =
                fixtures.assessmentService.score(coordinator, assessment);

        assertTrue(result.isFailure());
        assertEquals("sourceConfidence", result.getAffectedField());
    }

    @Test
    @DisplayName("TC-AS-07 confirming the recommendation needs no reason and "
            + "updates the incident")
    void confirmingRecommendationUpdatesIncident() {
        Assessment scored = fixtures.assessmentService
                .score(coordinator,
                       new Assessment(incident.getIncidentId(), 5, 4, 3, 4))
                .getValue();

        ServiceResult<Assessment> result = fixtures.assessmentService
                .confirmPriority(coordinator, scored, Priority.P1, null);

        assertTrue(result.isSuccess());
        assertFalse(result.getValue().isOverridden());
        assertEquals(Priority.P1,
                fixtures.incidentRepository.findById(incident.getIncidentId())
                        .orElseThrow().getPriority());
    }

    @Test
    @DisplayName("TC-AS-08 an override without a reason is refused")
    void overrideWithoutReasonIsRefused() {
        Assessment scored = fixtures.assessmentService
                .score(coordinator,
                       new Assessment(incident.getIncidentId(), 1, 1, 1, 4))
                .getValue();

        ServiceResult<Assessment> result = fixtures.assessmentService
                .confirmPriority(coordinator, scored, Priority.P1, "   ");

        assertTrue(result.isFailure());
        assertEquals("overrideReason", result.getAffectedField());
    }

    @Test
    @DisplayName("TC-AS-09 an override with a reason is accepted and written "
            + "to the audit log")
    void overrideWithReasonIsAudited() {
        Assessment scored = fixtures.assessmentService
                .score(coordinator,
                       new Assessment(incident.getIncidentId(), 1, 1, 1, 4))
                .getValue();

        ServiceResult<Assessment> result =
                fixtures.assessmentService.confirmPriority(
                coordinator, scored, Priority.P1, "Aged care facility within "
                        + "200 m");

        assertTrue(result.isSuccess());
        assertTrue(result.getValue().isOverridden());
        assertTrue(fixtures.auditRepository.findAll().stream()
                        .anyMatch(e -> "CONFIRM_PRIORITY".equals(e.getAction())
                                && e.getDetail()
                                        .contains("Aged care facility")),
                "The override reason must appear in the audit log");
    }

    @Test
    @DisplayName("TC-AS-10 a responder cannot assess an incident")
    void responderCannotAssess() {
        User responder = fixtures.createUser("responder", UserRole.RESPONDER);

        ServiceResult<Assessment> result =
                fixtures.assessmentService.score(responder,
                
                
                
                
                
                
                
                
                new Assessment(incident.getIncidentId(), 3, 3, 3, 4));

        assertTrue(result.isFailure());
        assertEquals(ErrorCode.PERMISSION_DENIED, result.getErrorCode());
    }

    @Test
    @DisplayName("TC-AS-11 a second assessment of the same incident increments "
            + "the version")
    void secondAssessmentIncrementsVersion() {
        fixtures.assessmentService.score(coordinator,
                new Assessment(incident.getIncidentId(), 2, 2, 2, 4));

        ServiceResult<Assessment> second =
                fixtures.assessmentService.score(coordinator,
                
                
                
                
                
                
                
                
                new Assessment(incident.getIncidentId(), 4, 4, 4, 4));

        assertNotNull(second.getValue());
        assertEquals(2, second.getValue().getVersion());
    }
}
