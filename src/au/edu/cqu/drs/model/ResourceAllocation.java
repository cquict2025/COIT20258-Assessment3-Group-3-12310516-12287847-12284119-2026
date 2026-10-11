package au.edu.cqu.drs.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A responder or item of equipment committed to one departmental assignment
 * (UC-08, FR-09). The availability window is held so that the coordination
 * service can reject an allocation that clashes with an existing commitment.
 */
public class ResourceAllocation {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private String allocationId;
    private String assignmentId;
    private String resourceName;
    private Department department;
    private int quantity;
    private LocalDateTime availableFrom;
    private LocalDateTime availableTo;
    private String stagingPoint;

    public ResourceAllocation() {
        this.quantity = 1;
    }

    public ResourceAllocation(String allocationId, String assignmentId,
                              String resourceName, 
                              Department department, int quantity,
                              LocalDateTime availableFrom, LocalDateTime
                                      availableTo) {
        this.allocationId = allocationId;
        this.assignmentId = assignmentId;
        this.resourceName = resourceName;
        this.department = department;
        this.quantity = quantity;
        this.availableFrom = availableFrom;
        this.availableTo = availableTo;
    }

    public String getAllocationId() {
        return this.allocationId;
    }

    public void setAllocationId(String allocationId) {
        this.allocationId = allocationId;
    }

    public String getAssignmentId() {
        return this.assignmentId;
    }

    public void setAssignmentId(String assignmentId) {
        this.assignmentId = assignmentId;
    }

    public String getResourceName() {
        return this.resourceName;
    }

    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
    }

    public Department getDepartment() {
        return this.department;
    }

    public void setDepartment(Department department) {
        this.department = department;
    }

    public int getQuantity() {
        return this.quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public LocalDateTime getAvailableFrom() {
        return this.availableFrom;
    }

    public void setAvailableFrom(LocalDateTime availableFrom) {
        this.availableFrom = availableFrom;
    }

    public LocalDateTime getAvailableTo() {
        return this.availableTo;
    }

    public void setAvailableTo(LocalDateTime availableTo) {
        this.availableTo = availableTo;
    }

    public String getStagingPoint() {
        return this.stagingPoint;
    }

    public void setStagingPoint(String stagingPoint) {
        this.stagingPoint = stagingPoint;
    }

    /**
     * True when this allocation's availability window overlaps another's.
     * Two windows that merely touch at an endpoint do not overlap, because a
     * resource released at 14:00 can be committed again from 14:00.
     *
     * @param other the allocation to compare with
     * @return true when the two windows overlap in time
     */
    public boolean overlaps(ResourceAllocation other) {
        if (other == null || this.availableFrom == null || this.availableTo ==
                null
                || other.availableFrom == null || other.availableTo == null) {
            return false;
        }
        return this.availableFrom.isBefore(other.availableTo)
                && other.availableFrom.isBefore(this.availableTo);
    }

    @Override
    public String toString() {
        return String.format("Allocation %s | assignment %s | %s x%d (%s) | %s "
                + "to %s | staging: %s",
                             
                             
                             
                             
                this.allocationId == null ? "unsaved" : this.allocationId,
                this.assignmentId,
                this.resourceName,
                this.quantity,
                this.department == null ? "unassigned" :
                        this.department.getDisplayName(),
                this.availableFrom == null ? "?" : this.availableFrom
                        .format(TIMESTAMP_FORMAT),
                this.availableTo == null ? "?" : this.availableTo
                        .format(TIMESTAMP_FORMAT),
                this.stagingPoint == null ? "not set" : this.stagingPoint);
    }
}
