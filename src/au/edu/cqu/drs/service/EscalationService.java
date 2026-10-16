package au.edu.cqu.drs.service;

import au.edu.cqu.drs.model.EscalationAlert;
import au.edu.cqu.drs.model.EscalationState;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.IncidentStatus;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * Creative feature 1: incident escalation timer and breach alerts.
 *
 * <p>Each priority carries a target first-response time. This service measures
 * the time from incident creation to the first field update (or to now, when no
 * response has been recorded) and reports whether the incident is on track, at
 * risk, or breached. Nothing is stored: the alert is always derived from the
 * incident, so a stale alert cannot contradict the incident it describes.</p>
 *
 * <p>The feature is not named in the Assessment 2 specification. It addresses a
 * real gap in the Assessment 1 design, where priority governed dashboard order
 * but nothing told a coordinator that a P1 incident had gone unanswered.</p>
 */
public class EscalationService {

    /**
     * Proportion of the target time after which an incident is flagged as at
     * risk.
     */
    public static final int AT_RISK_PERCENTAGE = 75;
    /**
     * Proportion of the target time after which an incident has breached its
     * target.
     */
    public static final int BREACH_PERCENTAGE = 100;

    private final IncidentService incidentService;

    /**
     * Creates an escalation service.
     *
     * @param incidentService the incident service
     */
    public EscalationService(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    /**
     * Calculates the escalation position of one incident at a given moment.
     *
     * @param incident the incident to evaluate
     * @param now      the moment to evaluate against, supplied so that the
     *                 calculation is deterministic and testable
     * @return the alert describing elapsed time, remaining time and state
     */
    public EscalationAlert evaluate(Incident incident, LocalDateTime now) {
        if (incident == null || incident.getCreatedAt() == null
                || now == null) {
            throw new IllegalArgumentException("Incident, creation time and "
                    + "now are required.");
        }

        LocalDateTime responseDeadlineStart = incident.getCreatedAt();
        LocalDateTime measuredTo = incident.getFirstResponseAt() != null
                ? incident.getFirstResponseAt()
                : now;

        long elapsed = Duration.between(responseDeadlineStart,
                measuredTo).toMinutes();
        if (elapsed < 0) {
            elapsed = 0;
        }
        int target = incident.getPriority().getTargetResponseMinutes();
        int percentageUsed = (int) Math.round(elapsed * 100.0 / target);
        long remaining = target - elapsed;

        EscalationState state;
        if (percentageUsed > BREACH_PERCENTAGE) {
            state = EscalationState.BREACHED;
        } else if (percentageUsed >= AT_RISK_PERCENTAGE) {
            state = EscalationState.AT_RISK;
        } else {
            state = EscalationState.ON_TRACK;
        }

        return new EscalationAlert(incident, state, elapsed, remaining,
                                   percentageUsed);
    }

    /**
     * Evaluates every open incident and returns the alerts that need attention,
     * most urgent first.
     *
     * @param now the moment to evaluate against
     * @return at-risk and breached alerts, breached first, then by priority
     */
    public List<EscalationAlert> findAlerts(LocalDateTime now) {
        return this.incidentService.findOpenIncidentsByPriority().stream()
                .filter(i -> i.getStatus() != IncidentStatus.CLOSED)
                .map(i -> evaluate(i, now))
                .filter(alert -> alert.getState() != EscalationState.ON_TRACK)
                .sorted(Comparator
                        .comparing((EscalationAlert a) -> a
                                .getState() == EscalationState.BREACHED
                                ? 0 : 1)
                        .thenComparingInt(a -> a.getIncident().getPriority()
                                .getRank())
                        .thenComparingLong(a -> -a.getMinutesElapsed()))
                .toList();
    }

    /**
     * Evaluates every open incident, whatever its state.
     *
     * @param now the moment to evaluate against
     * @return one alert per open incident
     */
    public List<EscalationAlert> evaluateAllOpen(LocalDateTime now) {
        return this.incidentService.findOpenIncidentsByPriority().stream()
                .map(i -> evaluate(i, now))
                .toList();
    }

    /**
     * Describes what this service does.
     *
     * @return a short description of this object
     */
    @Override
    public String toString() {
        return "EscalationService: checks response times against priority "
                + "targets";
    }
}
