package au.edu.cqu.drs.model;

import java.io.Serializable;

/**
 * The escalation position of one incident against its priority target
 * (creative feature 1). The alert is calculated, never stored, so that it can
 * never disagree with the incident it describes.
 */
public class EscalationAlert implements Serializable {

    /** Version of the serialised form sent between client and server. */
    private static final long serialVersionUID = 1L;

    private Incident incident;
    private EscalationState state;
    private long minutesElapsed;
    private long minutesRemaining;
    private int percentageOfTargetUsed;

    /**
     * Creates an empty escalation alert.
     */
    public EscalationAlert() {
    }

    /**
     * Creates an escalation alert.
     *
     * @param incident               the incident
     * @param state                  the state
     * @param minutesElapsed         the minutes elapsed
     * @param minutesRemaining       the minutes remaining
     * @param percentageOfTargetUsed the percentage of target used
     */
    public EscalationAlert(Incident incident, EscalationState state,
                           long minutesElapsed, 
                           long minutesRemaining, int percentageOfTargetUsed) {
        this.incident = incident;
        this.state = state;
        this.minutesElapsed = minutesElapsed;
        this.minutesRemaining = minutesRemaining;
        this.percentageOfTargetUsed = percentageOfTargetUsed;
    }

    /**
     * Returns the incident.
     *
     * @return the incident
     */
    public Incident getIncident() {
        return this.incident;
    }

    /**
     * Sets the incident.
     *
     * @param incident the incident
     */
    public void setIncident(Incident incident) {
        this.incident = incident;
    }

    /**
     * Returns the state.
     *
     * @return the state
     */
    public EscalationState getState() {
        return this.state;
    }

    /**
     * Sets the state.
     *
     * @param state the state
     */
    public void setState(EscalationState state) {
        this.state = state;
    }

    /**
     * Returns the minutes elapsed.
     *
     * @return the minutes elapsed
     */
    public long getMinutesElapsed() {
        return this.minutesElapsed;
    }

    /**
     * Sets the minutes elapsed.
     *
     * @param minutesElapsed the minutes elapsed
     */
    public void setMinutesElapsed(long minutesElapsed) {
        this.minutesElapsed = minutesElapsed;
    }

    /**
     * Returns the minutes remaining.
     *
     * @return the minutes remaining
     */
    public long getMinutesRemaining() {
        return this.minutesRemaining;
    }

    /**
     * Sets the minutes remaining.
     *
     * @param minutesRemaining the minutes remaining
     */
    public void setMinutesRemaining(long minutesRemaining) {
        this.minutesRemaining = minutesRemaining;
    }

    /**
     * Returns the percentage of target used.
     *
     * @return the percentage of target used
     */
    public int getPercentageOfTargetUsed() {
        return this.percentageOfTargetUsed;
    }

    /**
     * Sets the percentage of target used.
     *
     * @param percentageOfTargetUsed the percentage of target used
     */
    public void setPercentageOfTargetUsed(int percentageOfTargetUsed) {
        this.percentageOfTargetUsed = percentageOfTargetUsed;
    }

    /**
     * Describes the alert: the incident, its escalation state and how
     * much of the response target has been used or exceeded.
     *
     * @return a one-line summary of this alert
     */
    @Override
    public String toString() {
        return String
                .format("%s | %s | %d of %d minute target used (%d%%) | %s",
                             
                             
                             
                             
                             
                             
                             
                             
                this.incident == null ? "unknown incident" : this.incident
                        .getIncidentId(),
                this.state == null ? "unknown" : this.state.getDisplayName(),
                this.minutesElapsed,
                this.incident == null || this.incident.getPriority() == null
                        ? 0 :
                                this.incident.getPriority()
                                        .getTargetResponseMinutes(),
                this.percentageOfTargetUsed,
                this.minutesRemaining >= 0
                        ? this.minutesRemaining + " minute(s) remaining"
                        : Math.abs(this.minutesRemaining) +
                                " minute(s) overdue");
    }
}
