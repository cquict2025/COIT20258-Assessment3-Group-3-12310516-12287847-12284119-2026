package au.edu.cqu.drs.repository.memory;

import au.edu.cqu.drs.model.DepartmentAssignment;
import au.edu.cqu.drs.model.ResourceAllocation;
import au.edu.cqu.drs.repository.AssignmentRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory test double for assignments and resource allocations. */
public class InMemoryAssignmentRepository implements AssignmentRepository {

    private final Map<String, DepartmentAssignment> assignmentsById =
            new LinkedHashMap<>();
    private final List<ResourceAllocation> allocations = new ArrayList<>();
    private int assignmentSequence = 0;
    private int allocationSequence = 0;

    /**
     * Stores an assignment, replacing any stored one with the same
     * identifier. A new assignment is given the next ASG- identifier.
     *
     * @param assignment the assignment to store
     * @return the same assignment, with its identifier set
     */
    @Override
    public DepartmentAssignment save(DepartmentAssignment assignment) {
        if (assignment.getAssignmentId() == null) {
            this.assignmentSequence++;
            assignment.setAssignmentId(String
                    .format("ASG-%04d", this.assignmentSequence));
        }
        this.assignmentsById.put(assignment.getAssignmentId(), assignment);
        return assignment;
    }

    /**
     * Finds an assignment by its identifier.
     *
     * @param assignmentId the assignment identifier
     * @return the assignment, or empty if there is none
     */
    @Override
    public Optional<DepartmentAssignment> findById(String assignmentId) {
        return Optional.ofNullable(this.assignmentsById.get(assignmentId));
    }

    /**
     * Finds the assignments made for an incident.
     *
     * @param incidentId the incident identifier
     * @return the assignments for the incident, in the order stored
     */
    @Override
    public List<DepartmentAssignment> findByIncidentId(String incidentId) {
        return this.assignmentsById.values().stream()
                .filter(a -> incidentId.equals(a.getIncidentId()))
                .toList();
    }

    /**
     * Lists every stored assignment.
     *
     * @return a new list of all assignments, in the order stored
     */
    @Override
    public List<DepartmentAssignment> findAll() {
        return new ArrayList<>(this.assignmentsById.values());
    }

    /**
     * Stores a resource allocation, replacing any stored one with the same
     * identifier. A new allocation is given the next ALC- identifier.
     *
     * @param allocation the allocation to store
     * @return the same allocation, with its identifier set
     */
    @Override
    public ResourceAllocation saveAllocation(ResourceAllocation allocation) {
        if (allocation.getAllocationId() == null) {
            this.allocationSequence++;
            allocation.setAllocationId(String
                    .format("ALC-%04d", this.allocationSequence));
        }
        this.allocations.removeIf(a -> a.getAllocationId()
                .equals(allocation.getAllocationId()));
        this.allocations.add(allocation);
        return allocation;
    }

    /**
     * Finds the allocations of a named resource, ignoring case.
     *
     * @param resourceName the resource name
     * @return the allocations of that resource
     */
    @Override
    public List<ResourceAllocation> findAllocationsByResourceName(String
            resourceName) {
        return this.allocations.stream()
                .filter(a -> a.getResourceName().equalsIgnoreCase(resourceName))
                .toList();
    }

    /**
     * Finds the resource allocations made under an assignment.
     *
     * @param assignmentId the assignment identifier
     * @return the allocations for the assignment
     */
    @Override
    public List<ResourceAllocation> findAllocationsByAssignmentId(String
            assignmentId) {
        return this.allocations.stream()
                .filter(a -> assignmentId.equals(a.getAssignmentId()))
                .toList();
    }

    /**
     * Describes the repository.
     *
     * @return how many assignments and allocations it holds
     */
    @Override
    public String toString() {
        return "InMemoryAssignmentRepository: "
                + this.assignmentsById.size() + " assignment(s), "
                + this.allocations.size() + " allocation(s)";
    }
}
