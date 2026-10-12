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

    public DepartmentAssignment() {
        this.status = AssignmentStatus.PENDING_TRANSMISSION;
        this.deliveryAttempts = 0;
    }

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

    public String getAssignmentId() {
        return this.assignmentId;
    }

    public void setAssignmentId(String assignmentId) {
        this.assignmentId = assignmentId;
    }

    public String getIncidentId() {
        return this.incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public Department getDepartment() {
        return this.department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public String getRequestedAction() {
        return this.requestedAction;
    }

    public void setRequestedAction(String requestedAction) {
        this.requestedAction = requestedAction;
    }

    public String getUrgency() {
        return this.urgency;
    }

    public void setUrgency(String urgency) {
        this.urgency = urgency;
    }

    public LocalDateTime getTargetResponseBy() {
        return this.targetResponseBy;
    }

    public void setTargetResponseBy(LocalDateTime targetResponseBy) {
        this.targetResponseBy = targetResponseBy;
    }

    public AssignmentStatus getStatus() {
        return this.status;
    }

    public void setStatus(AssignmentStatus status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getRespondedAt() {
        return this.respondedAt;
    }

    public void setRespondedAt(LocalDateTime respondedAt) {
        this.respondedAt = respondedAt;
    }

    public String getResponseReason() {
        return this.responseReason;
    }

    public void setResponseReason(String responseReason) {
        this.responseReason = responseReason;
    }

    public LocalDateTime getEstimatedArrival() {
        return this.estimatedArrival;
    }

    public void setEstimatedArrival(LocalDateTime estimatedArrival) {
        this.estimatedArrival = estimatedArrival;
    }

    public int getDeliveryAttempts() {
        return this.deliveryAttempts;
    }

    public void setDeliveryAttempts(int deliveryAttempts) {
        this.deliveryAttempts = deliveryAttempts;
    }

    public void recordDeliveryAttempt() {
        this.deliveryAttempts++;
    }

    public List<ResourceAllocation> getAllocations() {
        return List.copyOf(this.allocations);
    }

    public void addAllocation(ResourceAllocation allocation) {
        if (allocation != null) {
            this.allocations.add(allocation);
        }
    }

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
