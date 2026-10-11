package au.edu.cqu.drs.service;

import au.edu.cqu.drs.TestFixtures;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.EscalationAlert;
import au.edu.cqu.drs.model.EscalationState;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.IncidentStatus;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.model.Priority;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for creative feature 1, the incident escalation timer.
 * The state boundaries at 75% and 100% of the target response time are tested
 * on
 * each side, which is the boundary value technique from the testing lecture.
 */
class EscalationServiceTest {

    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 9, 17, 12, 0);

    private TestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures();
    }

    @ParameterizedTest(name = "TC-ES-01 P1 after {0} minutes is {1}")
    @CsvSource({
            "0, ON_TRACK",
            "10, ON_TRACK",
            "11, ON_TRACK",
            "12, AT_RISK",
            "15, AT_RISK",
            "16, BREACHED",
            "45, BREACHED"
    })
    @DisplayName("TC-ES-01 the P1 target of 15 minutes changes state at 75% "
            + "and 100%")
    // 75% of 15 minutes is 11.25, so the first whole minute at or past the
    // at-risk boundary is 12; 11 minutes rounds to 73% and is still on track.
    void p1BoundaryStates(int minutesElapsed, String expectedState) {
        Incident incident = incident(Priority.P1,
                                     NOW.minusMinutes(minutesElapsed));

        EscalationAlert alert = fixtures.escalationService.evaluate(incident,
                NOW);

        assertEquals(EscalationState.valueOf(expectedState), alert.getState());
        assertEquals(minutesElapsed, alert.getMinutesElapsed());
    }

    @Test
    @DisplayName("TC-ES-02 each priority carries the documented target "
            + "response time")
    void prioritiesCarryTargets() {
        assertEquals(15, Priority.P1.getTargetResponseMinutes());
        assertEquals(60, Priority.P2.getTargetResponseMinutes());
        assertEquals(240, Priority.P3.getTargetResponseMinutes());
        assertEquals(1440, Priority.P4.getTargetResponseMinutes());
    }

    @Test
    @DisplayName("TC-ES-03 remaining time becomes negative once the target is "
            + "exceeded")
    void remainingTimeGoesNegativeAfterBreach() {
        Incident incident = incident(Priority.P2, NOW.minusMinutes(90));

        EscalationAlert alert = fixtures.escalationService.evaluate(incident,
                NOW);

        assertEquals(EscalationState.BREACHED, alert.getState());
        assertEquals(-30, alert.getMinutesRemaining());
        assertEquals(150, alert.getPercentageOfTargetUsed());
    }

    @Test
    @DisplayName("TC-ES-04 the clock stops at the first field response")
    void clockStopsAtFirstResponse() {
        Incident incident = incident(Priority.P1, NOW.minusMinutes(60));
        incident.setFirstResponseAt(NOW.minusMinutes(55));
        fixtures.incidentRepository.save(incident);

        EscalationAlert alert = fixtures.escalationService.evaluate(incident,
                NOW);

        assertEquals(5, alert.getMinutesElapsed());
        assertEquals(EscalationState.ON_TRACK, alert.getState());
    }

    @Test
    @DisplayName("TC-ES-05 only incidents needing attention appear in the "
            + "alert list")
    void alertListExcludesOnTrackIncidents() {
        incident(Priority.P1, NOW.minusMinutes(1));
        incident(Priority.P1, NOW.minusMinutes(40));
        incident(Priority.P2, NOW.minusMinutes(50));

        List<EscalationAlert> alerts =
                fixtures.escalationService.findAlerts(NOW);

        assertEquals(2, alerts.size());
        assertEquals(EscalationState.BREACHED, alerts.get(0).getState(),
                "Breached incidents must be listed before at-risk incidents");
        assertEquals(EscalationState.AT_RISK, alerts.get(1).getState());
    }

    @Test
    @DisplayName("TC-ES-06 a closed incident never raises an alert")
    void closedIncidentRaisesNoAlert() {
        Incident incident = incident(Priority.P1, NOW.minusMinutes(300));
        incident.setStatus(IncidentStatus.CLOSED);
        fixtures.incidentRepository.save(incident);

        assertTrue(fixtures.escalationService.findAlerts(NOW).isEmpty());
    }

    @Test
    @DisplayName("TC-ES-07 evaluating without an incident is rejected rather "
            + "than silently ignored")
    void nullIncidentIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> fixtures.escalationService.evaluate(null, NOW));
    }

    private Incident incident(Priority priority, LocalDateTime createdAt) {
        Incident incident = new Incident(null,
                                         priority.name() + " test incident", 
                DisasterType.FIRE, new Location("1 Test Street", "Laverton",
                                                -37.86, 144.77),
                createdAt);
        incident.setPriority(priority);
        return fixtures.incidentRepository.save(incident);
    }
}
