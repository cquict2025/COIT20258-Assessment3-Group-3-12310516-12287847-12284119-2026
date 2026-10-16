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

    /**
     * Creates a constant with the name shown to users.
     *
     * @param displayName the name shown to users
     */
    IncidentStatus(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the display name.
     *
     * @return the display name
     */
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

    /**
     * Returns the display name, so the constant reads well in lists and logs.
     *
     * @return the display name
     */
    @Override
    public String toString() {
        return this.displayName;
    }
}
