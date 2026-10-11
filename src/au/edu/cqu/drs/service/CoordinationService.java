package au.edu.cqu.drs.service;

import au.edu.cqu.drs.model.AssignmentStatus;
import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.DepartmentAssignment;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.IncidentStatus;
import au.edu.cqu.drs.model.ResourceAllocation;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.repository.AssignmentRepository;
import au.edu.cqu.drs.repository.IncidentRepository;
import au.edu.cqu.drs.repository.PartnerGateway;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Coordination of partner departments and their resources
 * (UC-07, UC-08, UC-09, FR-07, FR-08, FR-09).
 *
 * <p>A failed transmission leaves the assignment stored and visible as
 * DELIVERY_FAILED rather than discarding it, because Assessment 1 (UR-10)
 * required a technical failure never to look like a departmental rejection.</p>
 */
public class CoordinationService {

    /** Default department recommendations per disaster type (FR-07). */
    private static final Map<DisasterType, List<Department>> DEFAULT_MAPPINGS =
            Map.of(
            DisasterType.FIRE, List.of(Department.FIRE_AND_EMERGENCY,
                                       Department.HOSPITAL, 
                    Department.LAW_ENFORCEMENT, Department.ELECTRICITY),
            DisasterType.EARTHQUAKE, List.of(Department.FIRE_AND_EMERGENCY,
                                             Department.HOSPITAL, 
                    Department.TRANSPORTATION, Department
                            .WATER_SUPPLY, Department.ELECTRICITY),
            DisasterType.HURRICANE, List.of(Department.TRANSPORTATION,
                                            Department.ELECTRICITY, 
                    Department.WASTE_MANAGEMENT, Department.SCHOOLS, Department
                            .LAW_ENFORCEMENT),
            DisasterType.FLOOD, List.of(Department.WATER_SUPPLY,
                                        Department.TRANSPORTATION, 
                    Department.HOSPITAL, Department.WASTE_MANAGEMENT),
            DisasterType.STORM, List.of(Department.ELECTRICITY,
                                        Department.TRANSPORTATION, 
                    Department.WASTE_MANAGEMENT),
            DisasterType.HAZARDOUS_MATERIAL, List.of(Department
                    .FIRE_AND_EMERGENCY,
                                                     
                                                     
                                                     
                                                     
                                                     
                                                     
                                                     
                                                     
                    Department.HOSPITAL, Department.LAW_ENFORCEMENT, Department
                            .WATER_SUPPLY),
            DisasterType.OTHER, List.of(Department.LAW_ENFORCEMENT,
                                        Department.FIRE_AND_EMERGENCY));

    private final AssignmentRepository assignmentRepository;
    private final IncidentRepository incidentRepository;
    private final PartnerGateway partnerGateway;
    private final AuthenticationService authenticationService;
    private final AuditService auditService;

    public CoordinationService(AssignmentRepository assignmentRepository,
                               IncidentRepository incidentRepository,
                               PartnerGateway partnerGateway,
                               AuthenticationService authenticationService,
                               AuditService auditService) {
        this.assignmentRepository = assignmentRepository;
        this.incidentRepository = incidentRepository;
        this.partnerGateway = partnerGateway;
        this.authenticationService = authenticationService;
        this.auditService = auditService;
    }

    /**
     * Recommends the departments usually required for a disaster type (FR-07).
     *
     * @param disasterType the type of disaster
     * @return the recommended departments, never null
     */
    public List<Department> recommendDepartments(DisasterType disasterType) {
        if (disasterType == null) {
            return List.of();
        }
        return DEFAULT_MAPPINGS.getOrDefault(disasterType, List.of());
    }

    /**
     * Creates a departmental assignment and attempts to transmit it (UC-07).
     *
     * @param coordinator     the signed-in coordinator
     * @param incidentId      the incident being coordinated
     * @param department      the partner department
     * @param requestedAction what the department is being asked to do
     * @param urgency         the urgency label shown to the partner
     * @return the stored assignment, or a typed failure
     */
    public ServiceResult<DepartmentAssignment> createAssignment(User
            coordinator,
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            
            String incidentId, 
                    Department department,
                            String requestedAction,
                                    String urgency) {
        if (!this.authenticationService
                .isAuthorised(coordinator, UserRole.COORDINATOR)) {
            return ServiceResult.failure(ErrorCode.PERMISSION_DENIED,
                    "Only a coordinator can assign a department.");
        }
        if (department == null) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Select a department.", "department");
        }
        if (requestedAction == null || requestedAction.isBlank()) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Describe the action being requested.", "requestedAction");
        }

        Optional<Incident> found = this.incidentRepository.findById(incidentId);
        if (found.isEmpty()) {
            return ServiceResult.failure(ErrorCode.NOT_FOUND,
                                         "That incident no longer exists.");
        }
        Incident incident = found.get();
        if (incident.getStatus() == IncidentStatus.CLOSED) {
            return ServiceResult.failure(ErrorCode.CONFLICT,
                    "This incident is closed. Reopen it before assigning "
                            + "departments.");
        }

        boolean duplicate =
                this.assignmentRepository.findByIncidentId(incidentId)
                .stream()
                .anyMatch(a -> a.getDepartment() == department
                        && !a.getStatus().isResolved());
        if (duplicate) {
            return ServiceResult.failure(ErrorCode.CONFLICT,
                    department.getDisplayName() + " already has an open "
                            + "request for this incident.",
                    "department");
        }

        DepartmentAssignment assignment = new DepartmentAssignment(null,
                incidentId, department, 
                requestedAction, urgency);
        assignment.setCreatedAt(LocalDateTime.now());
        assignment.setTargetResponseBy(LocalDateTime.now()
                .plusMinutes(incident.getPriority()
                        .getTargetResponseMinutes()));
        DepartmentAssignment saved = this.assignmentRepository.save(assignment);

        saved.recordDeliveryAttempt();
        boolean delivered = this.partnerGateway.send(saved);
        saved.setStatus(delivered ? AssignmentStatus.AWAITING_RESPONSE
                : AssignmentStatus.DELIVERY_FAILED);
        this.assignmentRepository.save(saved);
        incident.addAssignment(saved);
        incident.incrementVersion();
        this.incidentRepository.save(incident);

        this.auditService.record(coordinator.getUsername(), "CREATE_ASSIGNMENT",
                            "Assignment", 
                saved.getAssignmentId(),
                department.getDisplayName() + " - " + saved.getStatus()
                        .getDisplayName());

        if (!delivered) {
            return ServiceResult.failure(ErrorCode.ADAPTER_UNAVAILABLE,
                    department.getDisplayName()
                            + " could not be reached. The request is saved and "
                                    + "can be retried.");
        }
        return ServiceResult.success(saved);
    }

    /**
     * Retries a failed transmission without creating a second assignment
     * (UR-10).
     *
     * @param coordinator  the signed-in coordinator
     * @param assignmentId the assignment to resend
     * @return the assignment after the retry, or a typed failure
     */
    public ServiceResult<DepartmentAssignment> retryDelivery(User coordinator,
            String assignmentId) {
        if (!this.authenticationService
                .isAuthorised(coordinator, UserRole.COORDINATOR)) {
            return ServiceResult.failure(ErrorCode.PERMISSION_DENIED,
                    "Only a coordinator can retry a delivery.");
        }
        Optional<DepartmentAssignment> found =
                this.assignmentRepository.findById(assignmentId);
        if (found.isEmpty()) {
            return ServiceResult.failure(ErrorCode.NOT_FOUND,
                                         "That assignment no longer exists.");
        }
        DepartmentAssignment assignment = found.get();
        if (assignment.getStatus() != AssignmentStatus.DELIVERY_FAILED
                && assignment.getStatus() != AssignmentStatus
                        .PENDING_TRANSMISSION) {
            return ServiceResult.failure(ErrorCode.CONFLICT,
                    "Only a failed or pending assignment can be retried.");
        }

        assignment.recordDeliveryAttempt();
        boolean delivered = this.partnerGateway.send(assignment);
        assignment.setStatus(delivered ? AssignmentStatus.AWAITING_RESPONSE
                : AssignmentStatus.DELIVERY_FAILED);
        DepartmentAssignment saved = this.assignmentRepository.save(assignment);
        this.auditService.record(coordinator.getUsername(), "RETRY_DELIVERY",
                            "Assignment", 
                assignmentId, "Attempt " + saved.getDeliveryAttempts() + ": "
                        + saved.getStatus().getDisplayName());

        if (!delivered) {
            return ServiceResult.failure(ErrorCode.ADAPTER_UNAVAILABLE,
                    "The department still cannot be reached. The request "
                            + "remains saved.");
        }
        return ServiceResult.success(saved);
    }

    /**
     * Records a department liaison's response to an assignment (UC-09).
     *
     * @param liaison      the signed-in liaison
     * @param assignmentId the assignment being answered
     * @param response     ACCEPTED, REJECTED or CLARIFICATION_REQUESTED
     * @param reason       reason, mandatory for rejection and clarification
     * @param eta          estimated arrival, mandatory for acceptance
     * @return the updated assignment, or a typed failure
     */
    public ServiceResult<DepartmentAssignment> respondToAssignment(User liaison,
            String assignmentId, 
                    AssignmentStatus response,
                            String reason,
                                    LocalDateTime eta) {
        if (!this.authenticationService.isAuthorised(liaison, UserRole.LIAISON))
                {
            return ServiceResult.failure(ErrorCode.PERMISSION_DENIED,
                    "Only a department liaison can answer an assignment.");
        }
        Optional<DepartmentAssignment> found =
                this.assignmentRepository.findById(assignmentId);
        if (found.isEmpty()) {
            return ServiceResult.failure(ErrorCode.NOT_FOUND,
                                         "That assignment no longer exists.");
        }
        DepartmentAssignment assignment = found.get();

        if (assignment.getStatus() == AssignmentStatus.DELIVERY_FAILED) {
            return ServiceResult.failure(ErrorCode.CONFLICT,
                    "This request has not reached the department yet.");
        }
        if (response == AssignmentStatus.ACCEPTED && eta == null) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Give an estimated time of arrival when accepting.",
                            "estimatedArrival");
        }
        if ((response == AssignmentStatus.REJECTED
                || response == AssignmentStatus.CLARIFICATION_REQUESTED)
                && (reason == null || reason.isBlank())) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Give a reason for this response.", "reason");
        }
        if (response != AssignmentStatus.ACCEPTED
                && response != AssignmentStatus.REJECTED
                && response != AssignmentStatus.CLARIFICATION_REQUESTED) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "A response must be Accepted, Rejected or "
                            + "Clarification requested.", "response");
        }

        assignment.setStatus(response);
        assignment.setResponseReason(reason);
        assignment.setEstimatedArrival(eta);
        assignment.setRespondedAt(LocalDateTime.now());
        DepartmentAssignment saved = this.assignmentRepository.save(assignment);
        this.auditService.record(liaison.getUsername(), "RESPOND_ASSIGNMENT",
                            "Assignment", 
                assignmentId, response.name() + (reason == null ? "" : ": "
                        + reason));
        return ServiceResult.success(saved);
    }

    /**
     * Commits a resource to an assignment, rejecting a clash with an existing
     * commitment of the same resource (UC-08, FR-09).
     *
     * @param user       the signed-in coordinator or liaison
     * @param allocation the proposed allocation
     * @return the saved allocation, or a typed conflict failure
     */
    public ServiceResult<ResourceAllocation> allocateResource(User user,
            ResourceAllocation allocation) {
        if (!this.authenticationService
                .isAuthorised(user, UserRole.COORDINATOR, UserRole.LIAISON)) {
            return ServiceResult.failure(ErrorCode.PERMISSION_DENIED,
                    "You do not have permission to allocate resources.");
        }
        if (allocation == null || allocation.getResourceName() == null
                || allocation.getResourceName().isBlank()) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Name the resource being allocated.", "resourceName");
        }
        if (allocation.getQuantity() <= 0) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Quantity must be at least 1.", "quantity");
        }
        if (allocation.getAvailableFrom() == null
                || allocation.getAvailableTo() == null
                || !allocation.getAvailableFrom()
                        .isBefore(allocation.getAvailableTo())) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "The availability window must start before it ends.",
                            "availableFrom");
        }
        if (this.assignmentRepository.findById(allocation.getAssignmentId())
                .isEmpty()) {
            return ServiceResult.failure(ErrorCode.NOT_FOUND,
                                         "That assignment no longer exists.");
        }

        boolean clash = this.assignmentRepository
                .findAllocationsByResourceName(allocation.getResourceName())
                        .stream()
                .anyMatch(existing -> existing.overlaps(allocation));
        if (clash) {
            return ServiceResult.failure(ErrorCode.CONFLICT,
                    allocation.getResourceName()
                            + " is already committed during that period.",
                                    "availableFrom");
        }

        ResourceAllocation saved =
                this.assignmentRepository.saveAllocation(allocation);
        this.assignmentRepository.findById(allocation.getAssignmentId())
                .ifPresent(a -> a.addAllocation(saved));
        this.auditService.record(user.getUsername(), "ALLOCATE_RESOURCE",
                            "Assignment", 
                allocation.getAssignmentId(),
                saved.getResourceName() + " x" + saved.getQuantity());
        return ServiceResult.success(saved);
    }

    public List<DepartmentAssignment> findByIncident(String incidentId) {
        return this.assignmentRepository.findByIncidentId(incidentId);
    }

    public List<DepartmentAssignment> findAll() {
        return this.assignmentRepository.findAll();
    }
}
