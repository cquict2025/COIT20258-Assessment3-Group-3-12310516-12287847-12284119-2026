package au.edu.cqu.drs.repository;

import au.edu.cqu.drs.model.DepartmentAssignment;
import au.edu.cqu.drs.model.ResourceAllocation;

import java.util.List;
import java.util.Optional;

/** Data-access contract for departmental assignments and their resources
        (FR-08,
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        
        FR-09). */
public interface AssignmentRepository {

    DepartmentAssignment save(DepartmentAssignment assignment);

    Optional<DepartmentAssignment> findById(String assignmentId);

    List<DepartmentAssignment> findByIncidentId(String incidentId);

    List<DepartmentAssignment> findAll();

    ResourceAllocation saveAllocation(ResourceAllocation allocation);

    List<ResourceAllocation> findAllocationsByResourceName(String resourceName);

    List<ResourceAllocation> findAllocationsByAssignmentId(String assignmentId);
}
