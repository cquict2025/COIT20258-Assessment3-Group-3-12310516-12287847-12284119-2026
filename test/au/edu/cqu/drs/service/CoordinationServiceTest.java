package au.edu.cqu.drs.service;

import au.edu.cqu.drs.TestFixtures;
import au.edu.cqu.drs.model.AssignmentStatus;
import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.DepartmentAssignment;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.model.ResourceAllocation;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for departmental coordination and resource allocation (FR-07, FR-08,
        FR-09). */
class CoordinationServiceTest {

    private TestFixtures fixtures;
    private User coordinator;
    private User liaison;
    private Incident incident;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures();
        coordinator = fixtures.createUser("coordinator", UserRole.COORDINATOR);
        liaison = fixtures.createUser("liaison", UserRole.LIAISON);
        incident = fixtures.incidentRepository
                .save(new Incident(null, "Warehouse fire",
                DisasterType.FIRE, new Location("2 Dock Road", "Laverton",
                                                -37.86, 144.77),
                LocalDateTime.now().minusMinutes(3)));
    }

    @Test
    @DisplayName("TC-CO-01 a fire recommends fire and emergency first")
    void fireRecommendsFireAndEmergency() {
        assertTrue(fixtures.coordinationService
                .recommendDepartments(DisasterType.FIRE)
                .contains(Department.FIRE_AND_EMERGENCY));
        assertTrue(fixtures.coordinationService
                .recommendDepartments(DisasterType.FLOOD)
                .contains(Department.WATER_SUPPLY));
    }

    @Test
    @DisplayName("TC-CO-02 a valid request is stored and awaits a response")
    void validRequestAwaitsResponse() {
        ServiceResult<DepartmentAssignment> result =
                fixtures.coordinationService.createAssignment(
                coordinator, incident.getIncidentId(), Department
                        .FIRE_AND_EMERGENCY,
                "Suppress fire and protect the adjoining building",
                        "Immediate");

        assertTrue(result.isSuccess());
        assertEquals(AssignmentStatus.AWAITING_RESPONSE,
                     result.getValue().getStatus());
        assertEquals(1, result.getValue().getDeliveryAttempts());
    }

    @Test
    @DisplayName("TC-CO-03 a second open request to the same department is "
            + "refused as a conflict")
    void duplicateOpenRequestIsRefused() {
        fixtures.coordinationService.createAssignment(coordinator,
                                                      incident.getIncidentId(), 
                Department.HOSPITAL, "Prepare for burns patients", "Immediate");

        ServiceResult<DepartmentAssignment> second =
                fixtures.coordinationService.createAssignment(
                coordinator, incident.getIncidentId(), Department.HOSPITAL,
                "Prepare for burns patients", "Immediate");

        assertTrue(second.isFailure());
        assertEquals(ErrorCode.CONFLICT, second.getErrorCode());
    }

    @Test
    @DisplayName("TC-CO-04 an unreachable partner leaves the request saved as "
            + "delivery failed")
    void unreachablePartnerLeavesRequestSaved() {
        fixtures.partnerGateway.setAvailable(false);

        ServiceResult<DepartmentAssignment> result =
                fixtures.coordinationService.createAssignment(
                coordinator, incident.getIncidentId(), Department.ELECTRICITY,
                "Isolate the supply to the block", "Immediate");

        assertTrue(result.isFailure());
        assertEquals(ErrorCode.ADAPTER_UNAVAILABLE, result.getErrorCode());
        DepartmentAssignment stored = fixtures.coordinationService
                .findByIncident(incident.getIncidentId()).get(0);
        assertEquals(AssignmentStatus.DELIVERY_FAILED, stored.getStatus(),
                "A technical failure must never look like a departmental "
                        + "rejection");
    }

    @Test
    @DisplayName("TC-CO-05 retrying a failed delivery succeeds once the "
            + "partner is reachable")
    void retrySucceedsWhenPartnerReturns() {
        fixtures.partnerGateway.setAvailable(false);
        fixtures.coordinationService.createAssignment(coordinator,
                                                      incident.getIncidentId(), 
                Department.ELECTRICITY, "Isolate the supply", "Immediate");
        DepartmentAssignment failed = fixtures.coordinationService
                .findByIncident(incident.getIncidentId()).get(0);
        fixtures.partnerGateway.setAvailable(true);

        ServiceResult<DepartmentAssignment> retry = fixtures.coordinationService
                .retryDelivery(coordinator, failed.getAssignmentId());

        assertTrue(retry.isSuccess());
        assertEquals(AssignmentStatus.AWAITING_RESPONSE,
                     retry.getValue().getStatus());
        assertEquals(2, retry.getValue().getDeliveryAttempts());
    }

    @Test
    @DisplayName("TC-CO-06 accepting a request without an ETA is refused")
    void acceptanceWithoutEtaIsRefused() {
        DepartmentAssignment assignment =
                fixtures.coordinationService.createAssignment(
                coordinator, incident.getIncidentId(), Department
                        .FIRE_AND_EMERGENCY,
                "Suppress fire", "Immediate").getValue();

        ServiceResult<DepartmentAssignment> result =
                fixtures.coordinationService
                .respondToAssignment(liaison, assignment.getAssignmentId(),
                        AssignmentStatus.ACCEPTED, null, null);

        assertTrue(result.isFailure());
        assertEquals("estimatedArrival", result.getAffectedField());
    }

    @Test
    @DisplayName("TC-CO-07 rejecting a request without a reason is refused")
    void rejectionWithoutReasonIsRefused() {
        DepartmentAssignment assignment =
                fixtures.coordinationService.createAssignment(
                coordinator, incident.getIncidentId(), Department.SCHOOLS,
                "Open the hall as an evacuation centre", "Same day").getValue();

        ServiceResult<DepartmentAssignment> result =
                fixtures.coordinationService
                .respondToAssignment(liaison, assignment.getAssignmentId(),
                        AssignmentStatus.REJECTED, "  ", null);

        assertTrue(result.isFailure());
        assertEquals("reason", result.getAffectedField());
    }

    @Test
    @DisplayName("TC-CO-08 a coordinator cannot answer on behalf of a "
            + "department")
    void coordinatorCannotAnswerForDepartment() {
        DepartmentAssignment assignment =
                fixtures.coordinationService.createAssignment(
                coordinator, incident.getIncidentId(), Department.HOSPITAL,
                "Prepare triage", "Immediate").getValue();

        ServiceResult<DepartmentAssignment> result =
                fixtures.coordinationService
                .respondToAssignment(coordinator, assignment.getAssignmentId(),
                        AssignmentStatus.ACCEPTED, null, LocalDateTime.now()
                                .plusMinutes(20));

        assertTrue(result.isFailure());
        assertEquals(ErrorCode.PERMISSION_DENIED, result.getErrorCode());
    }

    @Test
    @DisplayName("TC-CO-09 an overlapping allocation of the same resource is "
            + "refused")
    void overlappingAllocationIsRefused() {
        DepartmentAssignment assignment =
                fixtures.coordinationService.createAssignment(
                coordinator, incident.getIncidentId(), Department
                        .FIRE_AND_EMERGENCY,
                "Suppress fire", "Immediate").getValue();
        LocalDateTime start = LocalDateTime.now().withHour(9).withMinute(0)
                .withSecond(0).withNano(0);

        fixtures.coordinationService.allocateResource(coordinator,
                new ResourceAllocation(null, assignment.getAssignmentId(),
                                       "Pumper 12", 
                        Department.FIRE_AND_EMERGENCY, 1, start, start
                                .plusHours(4)));

        ServiceResult<ResourceAllocation> clash =
                fixtures.coordinationService.allocateResource(
                coordinator, new ResourceAllocation(null,
                                                    assignment
                                                            .getAssignmentId(),
                                                    "Pumper 12", 
                        Department.FIRE_AND_EMERGENCY, 1, start
                                .plusHours(2), start.plusHours(6)));

        assertTrue(clash.isFailure());
        assertEquals(ErrorCode.CONFLICT, clash.getErrorCode());
    }

    @Test
    @DisplayName("TC-CO-10 an adjacent allocation that only touches at an "
            + "endpoint is accepted")
    void adjacentAllocationIsAccepted() {
        DepartmentAssignment assignment =
                fixtures.coordinationService.createAssignment(
                coordinator, incident.getIncidentId(), Department
                        .FIRE_AND_EMERGENCY,
                "Suppress fire", "Immediate").getValue();
        LocalDateTime start = LocalDateTime.now().withHour(9).withMinute(0)
                .withSecond(0).withNano(0);

        fixtures.coordinationService.allocateResource(coordinator,
                new ResourceAllocation(null, assignment.getAssignmentId(),
                                       "Pumper 12", 
                        Department.FIRE_AND_EMERGENCY, 1, start, start
                                .plusHours(4)));

        ServiceResult<ResourceAllocation> adjacent =
                fixtures.coordinationService.allocateResource(
                coordinator, new ResourceAllocation(null,
                                                    assignment
                                                            .getAssignmentId(),
                                                    "Pumper 12", 
                        Department.FIRE_AND_EMERGENCY, 1, start
                                .plusHours(4), start.plusHours(8)));

        assertTrue(adjacent.isSuccess(),
                "A resource released at the end of a window can be committed "
                        + "again from that time");
    }

    @Test
    @DisplayName("TC-CO-11 an allocation window that ends before it starts is "
            + "refused")
    void invalidWindowIsRefused() {
        DepartmentAssignment assignment =
                fixtures.coordinationService.createAssignment(
                coordinator, incident.getIncidentId(), Department.WATER_SUPPLY,
                "Restore supply", "Same day").getValue();
        LocalDateTime start = LocalDateTime.now();

        ServiceResult<ResourceAllocation> result =
                fixtures.coordinationService.allocateResource(
                coordinator, new ResourceAllocation(null,
                                                    assignment
                                                            .getAssignmentId(),
                                                    "Tanker 3", 
                        Department.WATER_SUPPLY, 1, start, start
                                .minusHours(1)));

        assertTrue(result.isFailure());
        assertEquals("availableFrom", result.getAffectedField());
    }

    @Test
    @DisplayName("TC-CO-12 an accepted request counts as resolved but an "
            + "awaited one does not")
    void resolvedStatesAreCorrect() {
        assertTrue(AssignmentStatus.ACCEPTED.isResolved());
        assertTrue(AssignmentStatus.REJECTED.isResolved());
        assertFalse(AssignmentStatus.AWAITING_RESPONSE.isResolved());
        assertFalse(AssignmentStatus.DELIVERY_FAILED.isResolved());
    }
}
