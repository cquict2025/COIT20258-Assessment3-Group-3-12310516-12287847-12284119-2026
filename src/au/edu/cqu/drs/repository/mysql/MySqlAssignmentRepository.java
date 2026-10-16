package au.edu.cqu.drs.repository.mysql;

import au.edu.cqu.drs.model.AssignmentStatus;
import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.DepartmentAssignment;
import au.edu.cqu.drs.model.ResourceAllocation;
import au.edu.cqu.drs.repository.AssignmentRepository;
import au.edu.cqu.drs.repository.mysql.MySqlSupport.CodeKey;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MySQL adapter for {@link AssignmentRepository}: departmental assignments
 * and the resources allocated to them.
 */
public class MySqlAssignmentRepository implements AssignmentRepository {

    private static final List<String> ASSIGNMENT_COLUMNS = List.of(
            "incident_id", "department", "requested_action", "urgency",
            "target_response_by", "status", "created_at", "responded_at",
            "response_reason", "estimated_arrival", "delivery_attempts");

    private static final List<String> ALLOCATION_COLUMNS = List.of(
            "assignment_id", "resource_name", "department", "quantity",
            "available_from", "available_to", "staging_point");

    private final MySqlDatabase database;

    /**
     * Creates the repository.
     *
     * @param database the pooled MySQL database
     */
    public MySqlAssignmentRepository(MySqlDatabase database) {
        this.database = database;
    }

    /**
     * Inserts a new assignment, giving it a code, or updates an existing one.
     *
     * @param assignment the assignment
     * @return the same assignment, with its code set
     */
    @Override
    public DepartmentAssignment save(DepartmentAssignment assignment) {
        try {
            String code = this.database.inTransaction(connection ->
                    MySqlSupport.save(connection, CodeKey.ASSIGNMENT,
                            ASSIGNMENT_COLUMNS, assignment.getAssignmentId(),
                            (statement, first) ->
                                    bind(statement, first, assignment)));
            assignment.setAssignmentId(code);
            return assignment;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save the departmental "
                    + "assignment.", e);
        }
    }

    /**
     * Finds an assignment, with its allocations, by code.
     *
     * @param assignmentId the code, for example ASG-0001
     * @return the assignment, if there is one
     */
    @Override
    public Optional<DepartmentAssignment> findById(String assignmentId) {
        List<DepartmentAssignment> found = queryAssignments(
                "SELECT * FROM assignments WHERE assignment_id = ?",
                assignmentId);
        return found.isEmpty() ? Optional.empty()
                : Optional.of(found.get(0));
    }

    /**
     * Lists the assignments of an incident.
     *
     * @param incidentId the incident code
     * @return the assignments in code order
     */
    @Override
    public List<DepartmentAssignment> findByIncidentId(String incidentId) {
        return queryAssignments("SELECT * FROM assignments "
                + "WHERE incident_id = ? ORDER BY id", incidentId);
    }

    /**
     * Lists every assignment.
     *
     * @return the assignments in code order
     */
    @Override
    public List<DepartmentAssignment> findAll() {
        return queryAssignments("SELECT * FROM assignments ORDER BY id",
                null);
    }

    /**
     * Inserts a new allocation, giving it a code, or updates an existing one.
     *
     * @param allocation the allocation
     * @return the same allocation, with its code set
     */
    @Override
    public ResourceAllocation saveAllocation(ResourceAllocation allocation) {
        try {
            String code = this.database.inTransaction(connection ->
                    MySqlSupport.save(connection, CodeKey.ALLOCATION,
                            ALLOCATION_COLUMNS, allocation.getAllocationId(),
                            (statement, first) ->
                                    bind(statement, first, allocation)));
            allocation.setAllocationId(code);
            return allocation;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save the resource "
                    + "allocation.", e);
        }
    }

    /**
     * Lists the allocations of a named resource, ignoring case, which the
     * double-booking check uses.
     *
     * @param resourceName the resource, for example Pumper 12
     * @return the allocations
     */
    @Override
    public List<ResourceAllocation> findAllocationsByResourceName(
            String resourceName) {
        return queryAllocations("SELECT * FROM allocations "
                + "WHERE LOWER(resource_name) = LOWER(?) ORDER BY id",
                resourceName);
    }

    /**
     * Lists the allocations of an assignment.
     *
     * @param assignmentId the assignment code
     * @return the allocations
     */
    @Override
    public List<ResourceAllocation> findAllocationsByAssignmentId(
            String assignmentId) {
        return queryAllocations("SELECT * FROM allocations "
                + "WHERE assignment_id = ? ORDER BY id", assignmentId);
    }

    /**
     * Runs an assignment query and loads each assignment's allocations on
     * the same connection.
     *
     * @param sql       the SELECT statement, with at most one parameter
     * @param parameter the value for the parameter, or null when the query
     *                  has none
     * @return the assignments found, each with its allocations
     */
    private List<DepartmentAssignment> queryAssignments(String sql,
            String parameter) {
        try {
            return this.database.withConnection(connection -> {
                List<DepartmentAssignment> assignments = new ArrayList<>();
                try (PreparedStatement statement =
                        connection.prepareStatement(sql)) {
                    if (parameter != null) {
                        statement.setString(1, parameter);
                    }
                    try (ResultSet rs = statement.executeQuery()) {
                        while (rs.next()) {
                            assignments.add(map(rs));
                        }
                    }
                }
                for (DepartmentAssignment assignment : assignments) {
                    for (ResourceAllocation allocation : allocations(
                            connection, "SELECT * FROM allocations "
                                    + "WHERE assignment_id = ? ORDER BY id",
                            assignment.getAssignmentId())) {
                        assignment.addAllocation(allocation);
                    }
                }
                return assignments;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read assignments.", e);
        }
    }

    /**
     * Runs an allocation query on a connection borrowed from the pool.
     *
     * @param sql       the SELECT statement, with one parameter
     * @param parameter the value for the parameter
     * @return the allocations found
     */
    private List<ResourceAllocation> queryAllocations(String sql,
            String parameter) {
        try {
            return this.database.withConnection(connection ->
                    allocations(connection, sql, parameter));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read resource "
                    + "allocations.", e);
        }
    }

    /**
     * Reads allocations on a connection the caller already holds, so loading
     * an assignment never needs a second connection from the pool.
     *
     * @param connection the connection to use
     * @param sql        the SELECT statement, with one parameter
     * @param parameter  the value for the parameter
     * @return the allocations found, in the query's order
     * @throws SQLException if the query fails
     */
    private List<ResourceAllocation> allocations(Connection connection,
            String sql, String parameter) throws SQLException {
        List<ResourceAllocation> allocations = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    allocations.add(mapAllocation(rs));
                }
            }
        }
        return allocations;
    }

    /**
     * Sets the assignment's column values on a statement, in the order of
     * {@code ASSIGNMENT_COLUMNS}.
     *
     * @param statement  the INSERT or UPDATE statement to fill
     * @param first      the position of the first column's parameter
     * @param assignment the assignment whose values are bound
     * @throws SQLException if a parameter cannot be set
     */
    private void bind(PreparedStatement statement, int first,
            DepartmentAssignment assignment) throws SQLException {
        int i = first;
        statement.setString(i++, assignment.getIncidentId());
        statement.setString(i++,
                MySqlSupport.fromEnum(assignment.getDepartment()));
        statement.setString(i++, assignment.getRequestedAction());
        statement.setString(i++, assignment.getUrgency());
        MySqlSupport.setDateTime(statement, i++,
                assignment.getTargetResponseBy());
        statement.setString(i++,
                MySqlSupport.fromEnum(assignment.getStatus()));
        MySqlSupport.setDateTime(statement, i++, assignment.getCreatedAt());
        MySqlSupport.setDateTime(statement, i++, assignment.getRespondedAt());
        statement.setString(i++, assignment.getResponseReason());
        MySqlSupport.setDateTime(statement, i++,
                assignment.getEstimatedArrival());
        statement.setInt(i, assignment.getDeliveryAttempts());
    }

    /**
     * Sets the allocation's column values on a statement, in the order of
     * {@code ALLOCATION_COLUMNS}.
     *
     * @param statement  the INSERT or UPDATE statement to fill
     * @param first      the position of the first column's parameter
     * @param allocation the allocation whose values are bound
     * @throws SQLException if a parameter cannot be set
     */
    private void bind(PreparedStatement statement, int first,
            ResourceAllocation allocation) throws SQLException {
        int i = first;
        statement.setString(i++, allocation.getAssignmentId());
        statement.setString(i++, allocation.getResourceName());
        statement.setString(i++,
                MySqlSupport.fromEnum(allocation.getDepartment()));
        statement.setInt(i++, allocation.getQuantity());
        MySqlSupport.setDateTime(statement, i++,
                allocation.getAvailableFrom());
        MySqlSupport.setDateTime(statement, i++, allocation.getAvailableTo());
        statement.setString(i, allocation.getStagingPoint());
    }

    /**
     * Builds an assignment, without its allocations, from the current row
     * of a result set.
     *
     * @param rs the result set, positioned on a row
     * @return the assignment read from the row
     * @throws SQLException if a column cannot be read
     */
    private DepartmentAssignment map(ResultSet rs) throws SQLException {
        DepartmentAssignment assignment = new DepartmentAssignment();
        assignment.setAssignmentId(rs.getString("assignment_id"));
        assignment.setIncidentId(rs.getString("incident_id"));
        assignment.setDepartment(MySqlSupport.toEnum(Department.class,
                rs.getString("department")));
        assignment.setRequestedAction(rs.getString("requested_action"));
        assignment.setUrgency(rs.getString("urgency"));
        assignment.setTargetResponseBy(MySqlSupport.getDateTime(rs,
                "target_response_by"));
        assignment.setStatus(MySqlSupport.toEnum(AssignmentStatus.class,
                rs.getString("status")));
        assignment.setCreatedAt(MySqlSupport.getDateTime(rs, "created_at"));
        assignment.setRespondedAt(MySqlSupport.getDateTime(rs,
                "responded_at"));
        assignment.setResponseReason(rs.getString("response_reason"));
        assignment.setEstimatedArrival(MySqlSupport.getDateTime(rs,
                "estimated_arrival"));
        assignment.setDeliveryAttempts(rs.getInt("delivery_attempts"));
        return assignment;
    }

    /**
     * Builds a resource allocation from the current row of a result set.
     *
     * @param rs the result set, positioned on a row
     * @return the allocation read from the row
     * @throws SQLException if a column cannot be read
     */
    private ResourceAllocation mapAllocation(ResultSet rs)
            throws SQLException {
        ResourceAllocation allocation = new ResourceAllocation();
        allocation.setAllocationId(rs.getString("allocation_id"));
        allocation.setAssignmentId(rs.getString("assignment_id"));
        allocation.setResourceName(rs.getString("resource_name"));
        allocation.setDepartment(MySqlSupport.toEnum(Department.class,
                rs.getString("department")));
        allocation.setQuantity(rs.getInt("quantity"));
        allocation.setAvailableFrom(MySqlSupport.getDateTime(rs,
                "available_from"));
        allocation.setAvailableTo(MySqlSupport.getDateTime(rs,
                "available_to"));
        allocation.setStagingPoint(rs.getString("staging_point"));
        return allocation;
    }

    /**
     * Describes the repository.
     *
     * @return the tables it uses
     */
    @Override
    public String toString() {
        return "MySqlAssignmentRepository: assignments, allocations";
    }
}
