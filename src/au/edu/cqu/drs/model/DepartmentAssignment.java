package au.edu.cqu.drs.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * A request from the DRS to one partner department for one incident
 * (UC-07, UC-09, FR-08). Assessment 1 named this concept inconsistently
 * ("Assignment" in the architecture table, "DepartmentAssignment" in the
 * information model); the revised specification uses this name everywhere.
 */
public class DepartmentAssignment implements Serializable {

    /** Version of the serialised form sent between client and server. */
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private String assignmentId;
    private String incidentId;
    private Department department;
    private String requestedAction;
    private String urgency;
    private LocalDateTime targetResponseBy;
    private AssignmentStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
    private String responseReason;
    private LocalDateTime estimatedArrival;
    private int deliveryAttempts;

    private final List<ResourceAllocation> allocations = new ArrayList<>();

    /**
     * Creates an empty department assignment.
     */
    public DepartmentAssignment() {
        this.status = AssignmentStatus.PENDING_TRANSMISSION;
        this.deliveryAttempts = 0;
    }

    /**
     * Creates a department assignment.
     *
     * @param assignmentId    the assignment id
     * @param incidentId      the incident id
     * @param department      the department
     * @param requestedAction the requested action
     * @param urgency         the urgency
     */
    public DepartmentAssignment(String assignmentId, String incidentId,
                                Department department, 
                                String requestedAction, String urgency) {
        this();
        this.assignmentId = assignmentId;
        this.incidentId = incidentId;
        this.department = department;
        this.requestedAction = requestedAction;
        this.urgency = urgency;
    }

    /**
     * Returns the assignment id.
     *
     * @return the assignment id
     */
    public String getAssignmentId() {
        return this.assignmentId;
    }

    /**
     * Sets the assignment id.
     *
     * @param assignmentId the assignment id
     */
    public void setAssignmentId(String assignmentId) {
        this.assignmentId = assignmentId;
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
     * Returns the department.
     *
     * @return the department
     */
    public Department getDepartment() {
        return this.department;
    }

    /**
     * Sets the department.
     *
     * @param department the department
     */
    public void setDepartment(Department department) {
        this.department = department;
    }

    /**
     * Returns the requested action.
     *
     * @return the requested action
     */
    public String getRequestedAction() {
        return this.requestedAction;
    }

    /**
     * Sets the requested action.
     *
     * @param requestedAction the requested action
     */
    public void setRequestedAction(String requestedAction) {
        this.requestedAction = requestedAction;
    }

    /**
     * Returns the urgency.
     *
     * @return the urgency
     */
    public String getUrgency() {
        return this.urgency;
    }

    /**
     * Sets the urgency.
     *
     * @param urgency the urgency
     */
    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    /**
     * Returns the target response by.
     *
     * @return the target response by
     */
    public LocalDateTime getTargetResponseBy() {
        return this.targetResponseBy;
    }

    /**
     * Sets the target response by.
     *
     * @param targetResponseBy the target response by
     */
    public void setTargetResponseBy(LocalDateTime targetResponseBy) {
        this.targetResponseBy = targetResponseBy;
    }

    /**
     * Returns the status.
     *
     * @return the status
     */
    public AssignmentStatus getStatus() {
        return this.status;
    }

    /**
     * Sets the status.
     *
     * @param status the status
     */
    public void setStatus(AssignmentStatus status) {
        this.status = status;
    }

    /**
     * Returns the created at.
     *
     * @return the created at
     */
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    /**
     * Sets the created at.
     *
     * @param createdAt the created at
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the responded at.
     *
     * @return the responded at
     */
    public LocalDateTime getRespondedAt() {
        return this.respondedAt;
    }

    /**
     * Sets the responded at.
     *
     * @param respondedAt the responded at
     */
    public void setRespondedAt(LocalDateTime respondedAt) {
        this.respondedAt = respondedAt;
    }

    /**
     * Returns the response reason.
     *
     * @return the response reason
     */
    public String getResponseReason() {
        return this.responseReason;
    }

    /**
     * Sets the response reason.
     *
     * @param responseReason the response reason
     */
    public void setResponseReason(String responseReason) {
        this.responseReason = responseReason;
    }

    /**
     * Returns the estimated arrival.
     *
     * @return the estimated arrival
     */
    public LocalDateTime getEstimatedArrival() {
        return this.estimatedArrival;
    }

    /**
     * Sets the estimated arrival.
     *
     * @param estimatedArrival the estimated arrival
     */
    public void setEstimatedArrival(LocalDateTime estimatedArrival) {
        this.estimatedArrival = estimatedArrival;
    }

    /**
     * Returns the delivery attempts.
     *
     * @return the delivery attempts
     */
    public int getDeliveryAttempts() {
        return this.deliveryAttempts;
    }

    /**
     * Sets the delivery attempts.
     *
     * @param deliveryAttempts the delivery attempts
     */
    public void setDeliveryAttempts(int deliveryAttempts) {
        this.deliveryAttempts = deliveryAttempts;
    }

    /**
     * Adds one to the number of delivery attempts made to the department.
     */
    public void recordDeliveryAttempt() {
        this.deliveryAttempts++;
    }

    /**
     * Returns the allocations.
     *
     * @return the allocations
     */
    public List<ResourceAllocation> getAllocations() {
        return List.copyOf(this.allocations);
    }

    /**
     * Adds a resource allocation to this assignment. A null allocation is
     * ignored.
     *
     * @param allocation the allocation to add
     */
    public void addAllocation(ResourceAllocation allocation) {
        if (allocation != null) {
            this.allocations.add(allocation);
        }
    }

    /**
     * Describes the assignment: its incident, department, requested
     * action, urgency, status and any estimated arrival.
     *
     * @return a one-line summary of this assignment
     */
    @Override
    public String toString() {
        return String.format("Assignment %s | incident %s | %s | action: %s | "
                + "urgency %s | %s%s",
                             
                             
                             
                             
                this.assignmentId == null ? "unsaved" : this.assignmentId,
                this.incidentId,
                this.department == null ? "no department" : this.department
                        .getDisplayName(),
                this.requestedAction,
                this.urgency,
                this.status == null ? "no status" :
                        this.status.getDisplayName(),
                this.estimatedArrival == null ? "" : " | ETA "
                        + this.estimatedArrival.format(TIMESTAMP_FORMAT));
    }
}
