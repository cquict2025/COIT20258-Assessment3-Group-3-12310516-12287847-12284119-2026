package au.edu.cqu.drs.repository;

import au.edu.cqu.drs.model.DepartmentAssignment;
import au.edu.cqu.drs.model.ResourceAllocation;

import java.util.List;
import java.util.Optional;

/**
 * Data-access contract for departmental assignments and their resources
 * (FR-08, FR-09).
 */
public interface AssignmentRepository {

    /**
     * Saves an assignment, giving it an identifier if it has none yet.
     *
     * @param assignment the assignment to save
     * @return the saved assignment, with its identifier set
     */
    DepartmentAssignment save(DepartmentAssignment assignment);

    /**
     * Finds an assignment by its identifier.
     *
     * @param assignmentId the assignment identifier
     * @return the assignment, or empty if there is none
     */
    Optional<DepartmentAssignment> findById(String assignmentId);

    /**
     * Finds the assignments made for an incident.
     *
     * @param incidentId the incident identifier
     * @return the assignments for the incident
     */
    List<DepartmentAssignment> findByIncidentId(String incidentId);

    /**
     * Lists every stored assignment.
     *
     * @return all assignments
     */
    List<DepartmentAssignment> findAll();

    /**
     * Saves a resource allocation, giving it an identifier if it has none
     * yet.
     *
     * @param allocation the allocation to save
     * @return the saved allocation, with its identifier set
     */
    ResourceAllocation saveAllocation(ResourceAllocation allocation);

    /**
     * Finds the allocations of a named resource.
     *
     * @param resourceName the resource name
     * @return the allocations of that resource
     */
    List<ResourceAllocation> findAllocationsByResourceName(String resourceName);

    /**
     * Finds the resource allocations made under an assignment.
     *
     * @param assignmentId the assignment identifier
     * @return the allocations for the assignment
     */
    List<ResourceAllocation> findAllocationsByAssignmentId(String assignmentId);
}
