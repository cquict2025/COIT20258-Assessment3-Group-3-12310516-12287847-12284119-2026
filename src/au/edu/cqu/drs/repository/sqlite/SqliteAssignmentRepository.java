package au.edu.cqu.drs.repository.sqlite;

import au.edu.cqu.drs.model.AssignmentStatus;
import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.DepartmentAssignment;
import au.edu.cqu.drs.model.ResourceAllocation;
import au.edu.cqu.drs.repository.AssignmentRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQLite adapter for {@link AssignmentRepository}. */
public class SqliteAssignmentRepository implements AssignmentRepository {

    private final Database database;

    /**
     * Creates a SQLite assignment repository.
     *
     * @param database the database
     */
    public SqliteAssignmentRepository(Database database) {
        this.database = database;
    }

    /**
     * Inserts an assignment, or updates the stored row with the same
     * identifier. A new assignment is given the next ASG- identifier.
     *
     * @param assignment the assignment to save
     * @return the same assignment, with its identifier set
     * @throws IllegalStateException if the row cannot be written
     */
    @Override
    public DepartmentAssignment save(DepartmentAssignment assignment) {
        Connection connection = this.database.getConnection();
        if (assignment.getAssignmentId() == null) {
            assignment.setAssignmentId(
                    SqlSupport.nextId(connection, "assignments",
                                      "assignment_id", "ASG", 4));
        }
        String sql = """
                INSERT INTO assignments (
                    assignment_id, incident_id, department, requested_action,
                    urgency, target_response_by, status, created_at,
                    responded_at, response_reason, estimated_arrival,
                    delivery_attempts)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(assignment_id) DO UPDATE SET
                    department = excluded.department,
                    requested_action = excluded.requested_action,
                    urgency = excluded.urgency,
                    target_response_by = excluded.target_response_by,
                    status = excluded.status,
                    responded_at = excluded.responded_at,
                    response_reason = excluded.response_reason,
                    estimated_arrival = excluded.estimated_arrival,
                    delivery_attempts = excluded.delivery_attempts""";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, assignment.getAssignmentId());
            statement.setString(2, assignment.getIncidentId());
            statement.setString(3,
                                SqlSupport.fromEnum(assignment
                                        .getDepartment()));
            statement.setString(4, assignment.getRequestedAction());
            statement.setString(5, assignment.getUrgency());
            statement.setString(6,
                                SqlSupport.toText(assignment
                                        .getTargetResponseBy()));
            statement.setString(7, SqlSupport.fromEnum(assignment.getStatus()));
            statement.setString(8,
                                SqlSupport.toText(assignment.getCreatedAt()));
            statement.setString(9,
                                SqlSupport.toText(assignment.getRespondedAt()));
            statement.setString(10, assignment.getResponseReason());
            statement.setString(11,
                                SqlSupport.toText(assignment
                                        .getEstimatedArrival()));
            statement.setInt(12, assignment.getDeliveryAttempts());
            statement.executeUpdate();
            return assignment;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save the departmental "
                    + "assignment.",
                                            
                                            
                                            
                                            e);
        }
    }

    /**
     * Finds an assignment by its identifier, with its resource
     * allocations.
     *
     * @param assignmentId the assignment identifier
     * @return the assignment, or empty if there is none
     * @throws IllegalStateException if the row cannot be read
     */
    @Override
    public Optional<DepartmentAssignment> findById(String assignmentId) {
        try (PreparedStatement statement = this.database.getConnection()
                .prepareStatement("SELECT * FROM assignments WHERE "
                        + "assignment_id = ?")) {
            statement.setString(1, assignmentId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the assignment.",
                                            e);
        }
    }

    /**
     * Finds the assignments made for an incident, ordered by identifier.
     *
     * @param incidentId the incident identifier
     * @return the assignments for the incident
     */
    @Override
    public List<DepartmentAssignment> findByIncidentId(String incidentId) {
        return queryAssignments(
                "SELECT * FROM assignments WHERE incident_id = ? ORDER BY "
                        + "assignment_id", incidentId);
    }

    /**
     * Lists every assignment, ordered by identifier.
     *
     * @return all assignments
     */
    @Override
    public List<DepartmentAssignment> findAll() {
        return queryAssignments("SELECT * FROM assignments ORDER BY "
                + "assignment_id",
                                
                                
                                
                                null);
    }

    /**
     * Inserts a resource allocation, or updates the stored row with the
     * same identifier. A new allocation is given the next ALC- identifier.
     *
     * @param allocation the allocation to save
     * @return the same allocation, with its identifier set
     * @throws IllegalStateException if the row cannot be written
     */
    @Override
    public ResourceAllocation saveAllocation(ResourceAllocation allocation) {
        Connection connection = this.database.getConnection();
        if (allocation.getAllocationId() == null) {
            allocation.setAllocationId(
                    SqlSupport.nextId(connection, "allocations",
                                      "allocation_id", "ALC", 4));
        }
        String sql = """
                INSERT INTO allocations (
                    allocation_id, assignment_id, resource_name, department,
                    quantity, available_from, available_to, staging_point)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT(allocation_id) DO UPDATE SET
                    resource_name = excluded.resource_name,
                    department = excluded.department,
                    quantity = excluded.quantity,
                    available_from = excluded.available_from,
                    available_to = excluded.available_to,
                    staging_point = excluded.staging_point""";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, allocation.getAllocationId());
            statement.setString(2, allocation.getAssignmentId());
            statement.setString(3, allocation.getResourceName());
            statement.setString(4,
                                SqlSupport.fromEnum(allocation
                                        .getDepartment()));
            statement.setInt(5, allocation.getQuantity());
            statement.setString(6,
                                SqlSupport.toText(allocation
                                        .getAvailableFrom()));
            statement.setString(7,
                                SqlSupport.toText(allocation.getAvailableTo()));
            statement.setString(8, allocation.getStagingPoint());
            statement.executeUpdate();
            return allocation;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save the resource "
                    + "allocation.",
                                            
                                            
                                            
                                            e);
        }
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
        return queryAllocations(
                "SELECT * FROM allocations WHERE LOWER(resource_name) = "
                        + "LOWER(?)", resourceName);
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
        return queryAllocations("SELECT * FROM allocations WHERE assignment_id "
                + "= ?",
                                
                                
                                
                                assignmentId);
    }

    /**
     * Runs a query against the assignments table and maps every row.
     *
     * @param sql the query to run
     * @param parameter the single query parameter, or null if there is none
     * @return the assignments found
     * @throws IllegalStateException if the rows cannot be read
     */
    private List<DepartmentAssignment> queryAssignments(String sql,
            String parameter) {
        List<DepartmentAssignment> assignments = new ArrayList<>();
        try (PreparedStatement statement = this.database.getConnection()
                .prepareStatement(sql)) {
            if (parameter != null) {
                statement.setString(1, parameter);
            }
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    assignments.add(map(rs));
                }
            }
            return assignments;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read assignments.", e);
        }
    }

    /**
     * Runs a one-parameter query against the allocations table and maps
     * every row.
     *
     * @param sql the query to run
     * @param parameter the query parameter
     * @return the allocations found
     * @throws IllegalStateException if the rows cannot be read
     */
    private List<ResourceAllocation> queryAllocations(String sql,
            String parameter) {
        List<ResourceAllocation> allocations = new ArrayList<>();
        try (PreparedStatement statement = this.database.getConnection()
                .prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    allocations.add(mapAllocation(rs));
                }
            }
            return allocations;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read resource "
                    + "allocations.",
                                            
                                            
                                            
                                            e);
        }
    }

    /**
     * Builds an assignment from the current row of a result set and loads
     * its resource allocations.
     *
     * @param rs the result set, positioned on the row to read
     * @return the assignment read from the row
     * @throws SQLException if a column cannot be read
     */
    private DepartmentAssignment map(ResultSet rs) throws SQLException {
        DepartmentAssignment assignment = new DepartmentAssignment();
        assignment.setAssignmentId(rs.getString("assignment_id"));
        assignment.setIncidentId(rs.getString("incident_id"));
        assignment.setDepartment(SqlSupport
                .toEnum(Department.class, rs.getString("department")));
        assignment.setRequestedAction(rs.getString("requested_action"));
        assignment.setUrgency(rs.getString("urgency"));
        assignment.setTargetResponseBy(SqlSupport
                .toDateTime(rs.getString("target_response_by")));
        assignment.setStatus(SqlSupport
                .toEnum(AssignmentStatus.class, rs.getString("status")));
        assignment.setCreatedAt(SqlSupport
                .toDateTime(rs.getString("created_at")));
        assignment.setRespondedAt(SqlSupport
                .toDateTime(rs.getString("responded_at")));
        assignment.setResponseReason(rs.getString("response_reason"));
        assignment.setEstimatedArrival(SqlSupport
                .toDateTime(rs.getString("estimated_arrival")));
        assignment.setDeliveryAttempts(rs.getInt("delivery_attempts"));
        for (ResourceAllocation allocation
                : findAllocationsByAssignmentId(assignment.getAssignmentId())) {
            assignment.addAllocation(allocation);
        }
        return assignment;
    }

    /**
     * Builds a resource allocation from the current row of a result set.
     *
     * @param rs the result set, positioned on the row to read
     * @return the allocation read from the row
     * @throws SQLException if a column cannot be read
     */
    private ResourceAllocation mapAllocation(ResultSet rs) throws SQLException {
        ResourceAllocation allocation = new ResourceAllocation();
        allocation.setAllocationId(rs.getString("allocation_id"));
        allocation.setAssignmentId(rs.getString("assignment_id"));
        allocation.setResourceName(rs.getString("resource_name"));
        allocation.setDepartment(SqlSupport
                .toEnum(Department.class, rs.getString("department")));
        allocation.setQuantity(rs.getInt("quantity"));
        allocation.setAvailableFrom(SqlSupport
                .toDateTime(rs.getString("available_from")));
        allocation.setAvailableTo(SqlSupport
                .toDateTime(rs.getString("available_to")));
        allocation.setStagingPoint(rs.getString("staging_point"));
        return allocation;
    }

    /**
     * Describes the repository.
     *
     * @return the database it reads and writes
     */
    @Override
    public String toString() {
        return "SqliteAssignmentRepository: " + this.database;
    }
}
