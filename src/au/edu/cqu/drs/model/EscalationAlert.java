package au.edu.cqu.drs.model;

/**
 * The escalation position of one incident against its priority target
 * (creative feature 1). The alert is calculated, never stored, so that it can
 * never disagree with the incident it describes.
 */
public class EscalationAlert {

    private Incident incident;
    private EscalationState state;
    private long minutesElapsed;
    private long minutesRemaining;
    private int percentageOfTargetUsed;

    public EscalationAlert() {
    }

    public EscalationAlert(Incident incident, EscalationState state,
                           long minutesElapsed, 
                           long minutesRemaining, int percentageOfTargetUsed) {
        this.incident = incident;
        this.state = state;
        this.minutesElapsed = minutesElapsed;
        this.minutesRemaining = minutesRemaining;
        this.percentageOfTargetUsed = percentageOfTargetUsed;
    }

    public Incident getIncident() {
        return this.incident;
    }

    public void setIncident(Incident incident) {
        this.incident = incident;
    }

    public EscalationState getState() {
        return this.state;
    }

    public void setState(EscalationState state) {
        this.state = state;
    }

    public long getMinutesElapsed() {
        return this.minutesElapsed;
    }

    public void setMinutesElapsed(long minutesElapsed) {
        this.minutesElapsed = minutesElapsed;
    }

    public long getMinutesRemaining() {
        return this.minutesRemaining;
    }

    public void setMinutesRemaining(long minutesRemaining) {
        this.minutesRemaining = minutesRemaining;
    }

    public int getPercentageOfTargetUsed() {
        return this.percentageOfTargetUsed;
    }

    public void setPercentageOfTargetUsed(int percentageOfTargetUsed) {
        this.percentageOfTargetUsed = percentageOfTargetUsed;
    }

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
