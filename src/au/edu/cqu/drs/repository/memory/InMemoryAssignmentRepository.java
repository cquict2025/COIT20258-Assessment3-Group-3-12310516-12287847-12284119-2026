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

    @Override
    public Optional<DepartmentAssignment> findById(String assignmentId) {
        return Optional.ofNullable(this.assignmentsById.get(assignmentId));
    }

    @Override
    public List<DepartmentAssignment> findByIncidentId(String incidentId) {
        return this.assignmentsById.values().stream()
                .filter(a -> incidentId.equals(a.getIncidentId()))
                .toList();
    }

    @Override
    public List<DepartmentAssignment> findAll() {
        return new ArrayList<>(this.assignmentsById.values());
    }

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

    @Override
    public List<ResourceAllocation> findAllocationsByResourceName(String
            resourceName) {
        return this.allocations.stream()
                .filter(a -> a.getResourceName().equalsIgnoreCase(resourceName))
                .toList();
    }

    @Override
    public List<ResourceAllocation> findAllocationsByAssignmentId(String
            assignmentId) {
        return this.allocations.stream()
                .filter(a -> assignmentId.equals(a.getAssignmentId()))
                .toList();
    }
}
