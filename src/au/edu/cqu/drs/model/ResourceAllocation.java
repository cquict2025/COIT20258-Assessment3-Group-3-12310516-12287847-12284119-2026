package au.edu.cqu.drs.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A responder or item of equipment committed to one departmental assignment
 * (UC-08, FR-09). The availability window is held so that the coordination
 * service can reject an allocation that clashes with an existing commitment.
 */
public class ResourceAllocation implements Serializable {

    /** Version of the serialised form sent between client and server. */
    private static final long serialVersionUID = 1L;

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

    /**
     * Creates an empty resource allocation.
     */
    public ResourceAllocation() {
        this.quantity = 1;
    }

    /**
     * Creates a resource allocation.
     *
     * @param allocationId  the allocation id
     * @param assignmentId  the assignment id
     * @param resourceName  the resource name
     * @param department    the department
     * @param quantity      the quantity
     * @param availableFrom the available from
     * @param availableTo   the available to
     */
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

    /**
     * Returns the allocation id.
     *
     * @return the allocation id
     */
    public String getAllocationId() {
        return this.allocationId;
    }

    /**
     * Sets the allocation id.
     *
     * @param allocationId the allocation id
     */
    public void setAllocationId(String allocationId) {
        this.allocationId = allocationId;
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
     * Returns the resource name.
     *
     * @return the resource name
     */
    public String getResourceName() {
        return this.resourceName;
    }

    /**
     * Sets the resource name.
     *
     * @param resourceName the resource name
     */
    public void setResourceName(String resourceName) {
        this.resourceName = resourceName;
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
     * Returns the quantity.
     *
     * @return the quantity
     */
    public int getQuantity() {
        return this.quantity;
    }

    /**
     * Sets the quantity.
     *
     * @param quantity the quantity
     */
    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    /**
     * Returns the available from.
     *
     * @return the available from
     */
    public LocalDateTime getAvailableFrom() {
        return this.availableFrom;
    }

    /**
     * Sets the available from.
     *
     * @param availableFrom the available from
     */
    public void setAvailableFrom(LocalDateTime availableFrom) {
        this.availableFrom = availableFrom;
    }

    /**
     * Returns the available to.
     *
     * @return the available to
     */
    public LocalDateTime getAvailableTo() {
        return this.availableTo;
    }

    /**
     * Sets the available to.
     *
     * @param availableTo the available to
     */
    public void setAvailableTo(LocalDateTime availableTo) {
        this.availableTo = availableTo;
    }

    /**
     * Returns the staging point.
     *
     * @return the staging point
     */
    public String getStagingPoint() {
        return this.stagingPoint;
    }

    /**
     * Sets the staging point.
     *
     * @param stagingPoint the staging point
     */
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

    /**
     * Describes the allocation: the resource, quantity, department,
     * availability window and staging point.
     *
     * @return a one-line summary of this allocation
     */
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
