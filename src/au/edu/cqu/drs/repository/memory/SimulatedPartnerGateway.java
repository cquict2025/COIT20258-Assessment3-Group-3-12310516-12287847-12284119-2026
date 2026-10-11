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

    public boolean isAvailable() {
        return this.available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int getSendCount() {
        return this.sendCount;
    }

    @Override
    public boolean send(DepartmentAssignment assignment) {
        this.sendCount++;
        return this.available;
    }
}
