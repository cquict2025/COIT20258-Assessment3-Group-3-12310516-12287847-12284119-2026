/**
 * Domain objects and enumerations of the Disaster Response System.
 *
 * <p>These classes carry the operational state of the system: reports,
 * incidents, assessments, departmental assignments, resource allocations,
 * timeline entries, staff accounts and audit events. They hold only behaviour
 * that depends on their own state, such as
 * {@link au.edu.cqu.drs.model.Location#distanceKmTo},
 * {@link au.edu.cqu.drs.model.ResourceAllocation#overlaps} and
 * {@link au.edu.cqu.drs.model.IncidentStatus#canTransitionTo}.</p>
 *
 * <p>Rules that span several objects, together with authorisation and
 * persistence, belong to {@link au.edu.cqu.drs.service} instead, so that the
 * same rule applies no matter which screen invokes it.</p>
 */
package au.edu.cqu.drs.model;
