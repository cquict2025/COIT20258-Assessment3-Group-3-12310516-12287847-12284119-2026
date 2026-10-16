package au.edu.cqu.drs.repository.memory;

import au.edu.cqu.drs.model.DepartmentAssignment;
import au.edu.cqu.drs.repository.PartnerGateway;

/**
 * Stub partner adapter for the prototype. Assessment 1 scoped the prototype to
 * simulated departmental interfaces, so this class acknowledges every request
 * but can be switched to fail on demand, which lets the coordination service
 * demonstrate the "delivery failed, retry required" behaviour of UR-10.
 */
public class SimulatedPartnerGateway implements PartnerGateway {

    private boolean available = true;
    private int sendCount = 0;

    /**
     * Tells whether the simulated partner currently accepts deliveries.
     *
     * @return {@code true} if it is available
     */
    public boolean isAvailable() {
        return this.available;
    }

    /**
     * Switches the simulated partner on or off, so that delivery failure
     * can be demonstrated.
     *
     * @param available {@code true} to accept deliveries, {@code false} to
     *                  fail them
     */
    public void setAvailable(boolean available) {
        this.available = available;
    }

    /**
     * Returns how many delivery attempts have been made.
     *
     * @return the number of calls to {@link #send}
     */
    public int getSendCount() {
        return this.sendCount;
    }

    /**
     * Pretends to deliver an assignment to a partner department. Every
     * call is counted, and the result is whether the gateway is currently
     * available.
     *
     * @param assignment the assignment to deliver (not inspected)
     * @return true if the gateway is available, false otherwise
     */
    @Override
    public boolean send(DepartmentAssignment assignment) {
        this.sendCount++;
        return this.available;
    }

    /**
     * Describes the gateway.
     *
     * @return whether it is available and how many sends it has had
     */
    @Override
    public String toString() {
        return "SimulatedPartnerGateway: "
                + (this.available ? "available" : "unavailable")
                + ", " + this.sendCount + " send(s)";
    }
}
