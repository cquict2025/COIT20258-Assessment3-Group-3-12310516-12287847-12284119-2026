package au.edu.cqu.drs.server.event;

/**
 * The kinds of event the server pushes to clients.
 */
public enum EventType {

    /** A command changed a report, incident, assignment or warning. */
    CHANGE("Change"),
    /** An open incident moved to another escalation state. */
    ESCALATION("Escalation"),
    /** The number of clients receiving live events changed. */
    CLIENTS("Clients"),
    /** Sent when nothing else has been, so both ends know the link works. */
    HEARTBEAT("Heartbeat");

    private final String displayName;

    /**
     * Creates an event type.
     *
     * @param displayName the name shown in the activity feed
     */
    EventType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the name shown in the activity feed.
     *
     * @return the display name
     */
    public String getDisplayName() {
        return this.displayName;
    }

    /**
     * Describes the event type.
     *
     * @return the display name
     */
    @Override
    public String toString() {
        return this.displayName;
    }
}
