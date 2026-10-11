package au.edu.cqu.drs.repository;

import au.edu.cqu.drs.model.DepartmentAssignment;

/**
 * Outbound contract to partner departments, kept as an interface exactly as
 * Assessment 1 promised so that the stub used by this prototype can be replaced
 * by a real departmental API without changing any service or controller.
 */
public interface PartnerGateway {

    /**
     * Attempts to deliver an assignment to the partner department.
     *
     * @param assignment the assignment to transmit
     * @return true when the partner acknowledged receipt
     */
    boolean send(DepartmentAssignment assignment);
}
