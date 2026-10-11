package au.edu.cqu.drs.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of an incident (FR-06). The permitted transitions are held with the
 * states themselves so that every service and every screen enforces the same
 * rule, rather than each controller repeating it.
 */
public enum IncidentStatus {

    ACTIVE("Active"),
    MONITORING("Monitoring"),
    CLOSED("Closed"),
    REOPENED("Reopened");

    private final String displayName;

    IncidentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Returns true when an incident may move from this state to the target
     * state.
     *
     * @param target the proposed new state
     * @return true when the transition is permitted by the lifecycle rules
     */
    public boolean canTransitionTo(IncidentStatus target) {
        Set<IncidentStatus> permitted = switch (this) {
            case ACTIVE -> EnumSet.of(MONITORING, CLOSED);
            case MONITORING -> EnumSet.of(ACTIVE, CLOSED);
            case CLOSED -> EnumSet.of(REOPENED);
            case REOPENED -> EnumSet.of(ACTIVE, MONITORING, CLOSED);
        };
        return permitted.contains(target);
    }

    @Override
    public String toString() {
        return this.displayName;
    }
}
