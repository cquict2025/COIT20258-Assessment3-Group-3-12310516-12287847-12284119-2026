package au.edu.cqu.drs.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for behaviour that belongs to the domain objects themselves: lifecycle
 * rules, distance, overlap and the hand-written toString() formats required by
 * the assessment specification.
 */
class ModelBehaviourTest {

    @Test
    @DisplayName("TC-MD-01 the incident lifecycle permits and refuses the "
            + "documented transitions")
    void lifecycleTransitions() {
        assertTrue(IncidentStatus.ACTIVE
                .canTransitionTo(IncidentStatus.MONITORING));
        assertTrue(IncidentStatus.ACTIVE
                .canTransitionTo(IncidentStatus.CLOSED));
        assertTrue(IncidentStatus.CLOSED
                .canTransitionTo(IncidentStatus.REOPENED));
        assertFalse(IncidentStatus.CLOSED
                .canTransitionTo(IncidentStatus.ACTIVE));
        assertFalse(IncidentStatus.ACTIVE
                .canTransitionTo(IncidentStatus.REOPENED));
    }

    @Test
    @DisplayName("TC-MD-02 the distance between two identical points is zero")
    void distanceToSamePointIsZero() {
        Location location = new Location("1 Test Street", "Laverton", -37.8600,
                                         144.7700);

        assertEquals(0.0, location.distanceKmTo(location), 0.0001);
    }

    @Test
    @DisplayName("TC-MD-03 one degree of latitude measures approximately "
            + "111 km")
    void oneDegreeOfLatitude() {
        Location north = new Location("", "North", 0.0, 0.0);
        Location south = new Location("", "South", 1.0, 0.0);

        assertEquals(111.19, north.distanceKmTo(south), 0.5);
    }

    @Test
    @DisplayName("TC-MD-04 a null comparison location returns the maximum "
            + "distance")
    void nullLocationReturnsMaximumDistance() {
        Location location = new Location("", "Laverton", -37.86, 144.77);

        assertEquals(Double.MAX_VALUE, location.distanceKmTo(null));
    }

    @Test
    @DisplayName("TC-MD-05 overlapping and adjacent availability windows are "
            + "distinguished")
    void allocationOverlapRules() {
        LocalDateTime nine = LocalDateTime.of(2026, 9, 17, 9, 0);
        ResourceAllocation morning = new ResourceAllocation("ALC-1", "ASG-1",
                "Pumper 12", 
                Department.FIRE_AND_EMERGENCY, 1, nine, nine.plusHours(4));
        ResourceAllocation overlapping = new ResourceAllocation("ALC-2",
                "ASG-2", "Pumper 12", 
                Department.FIRE_AND_EMERGENCY, 1, nine.plusHours(2), nine
                        .plusHours(6));
        ResourceAllocation adjacent = new ResourceAllocation("ALC-3", "ASG-3",
                "Pumper 12", 
                Department.FIRE_AND_EMERGENCY, 1, nine.plusHours(4), nine
                        .plusHours(8));

        assertTrue(morning.overlaps(overlapping));
        assertFalse(morning.overlaps(adjacent));
        assertFalse(morning.overlaps(null));
    }

    @Test
    @DisplayName("TC-MD-06 an incident reports unresolved assignments before "
            + "closure")
    void unresolvedAssignmentsAreDetected() {
        Incident incident = new Incident("INC-0001", "Test", DisasterType.FIRE,
                new Location("", "Laverton", -37.86,
                        144.77), LocalDateTime.now());
        DepartmentAssignment awaiting = new DepartmentAssignment("ASG-0001",
                "INC-0001", 
                Department.HOSPITAL, "Prepare triage", "Immediate");
        awaiting.setStatus(AssignmentStatus.AWAITING_RESPONSE);
        incident.addAssignment(awaiting);

        assertTrue(incident.hasUnresolvedAssignments());

        awaiting.setStatus(AssignmentStatus.ACCEPTED);
        assertFalse(incident.hasUnresolvedAssignments());
    }

    @Test
    @DisplayName("TC-MD-07 the report toString() omits the reporter's contact "
            + "details")
    void reportToStringOmitsContact() {
        DisasterReport report = new DisasterReport("RPT-0001",
                                                   DisasterType.FIRE, 
                new Location("2 Dock Road", "Laverton", -37.86, 144.77),
                LocalDateTime.of(2026, 9, 17, 11, 30), "Flames visible");
        report.setReporterContact("0400 000 000");
        report.setStatus(ReportStatus.SUBMITTED);

        String text = report.toString();

        assertTrue(text.contains("RPT-0001"));
        assertTrue(text.contains("Fire"));
        assertTrue(text.contains("17/09/2026 11:30"));
        assertFalse(text.contains("0400 000 000"),
                "Restricted contact data must not appear in a general summary");
    }

    @Test
    @DisplayName("TC-MD-08 the assessment toString() shows the factors, score "
            + "and override")
    void assessmentToStringIsExplainable() {
        Assessment assessment = new Assessment("INC-0001", 5, 4, 3, 4);
        assessment.setAssessmentId("ASM-0001");
        assessment.setCalculatedScore(84);
        assessment.setRecommendedPriority(Priority.P1);
        assessment.setConfirmedPriority(Priority.P2);
        assessment.setOverrideReason("Sprinkler system confirmed operating");

        String text = assessment.toString();

        assertTrue(text.contains("life=5"));
        assertTrue(text.contains("score=84"));
        assertTrue(text.contains("recommended P1"));
        assertTrue(text.contains("confirmed P2"));
        assertTrue(text.contains("Sprinkler system confirmed operating"));
    }

    @Test
    @DisplayName("TC-MD-09 the user toString() never exposes the stored "
            + "credentials")
    void userToStringHidesCredentials() {
        User user = new User("USR-001", "coordinator", "Alex Coordinator",
                             UserRole.COORDINATOR);
        user.setPasswordSalt("saltvalue");
        user.setPasswordHash("hashvalue");

        String text = user.toString();

        assertTrue(text.contains("coordinator"));
        assertFalse(text.contains("saltvalue"));
        assertFalse(text.contains("hashvalue"));
    }

    @Test
    @DisplayName("TC-MD-10 a report of type OTHER displays its written "
            + "category")
    void otherTypeUsesWrittenLabel() {
        DisasterReport report = new DisasterReport();
        report.setDisasterType(DisasterType.OTHER);
        report.setOtherTypeDescription("Landslide");

        assertEquals("Landslide", report.getTypeLabel());
    }

    @Test
    @DisplayName("TC-MD-11 priority ranking orders P1 ahead of P4")
    void priorityRanking() {
        assertTrue(Priority.P1.getRank() < Priority.P2.getRank());
        assertTrue(Priority.P2.getRank() < Priority.P3.getRank());
        assertTrue(Priority.P3.getRank() < Priority.P4.getRank());
    }
}
