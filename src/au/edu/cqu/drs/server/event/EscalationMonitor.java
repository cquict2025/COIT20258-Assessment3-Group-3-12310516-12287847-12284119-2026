package au.edu.cqu.drs.server.event;

import au.edu.cqu.drs.model.EscalationAlert;
import au.edu.cqu.drs.model.EscalationState;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.server.RequestDispatcher;
import au.edu.cqu.drs.server.ServerLog;
import au.edu.cqu.drs.service.EscalationService;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Checks the escalation state of every open incident once a minute and
 * publishes an event only when an incident's state changes.
 *
 * <p>Escalation depends on time passing, not on any command, so no request
 * would ever report that a P1 has just gone past its 15-minute target. The
 * monitor runs {@link EscalationService#evaluate} over the open incidents on
 * a {@link ScheduledExecutorService} and remembers each incident's last
 * state in a map, so an incident that stays Breached is announced once,
 * not every minute. Time comes from an injected {@link Clock}, so tests can
 * move it forward instead of waiting.</p>
 */
public class EscalationMonitor implements AutoCloseable {

    /** Seconds between checks. */
    public static final int PERIOD_SECONDS = 60;

    private final EscalationService escalation;
    private final EventBus bus;
    private final Clock clock;
    private final ServerLog log;
    private final RequestDispatcher dispatcher;
    private final Map<String, EscalationState> lastStates;
    private ScheduledExecutorService timer;

    /**
     * Creates a monitor that reads the incidents directly; for tests.
     *
     * @param escalation the escalation service
     * @param bus        the bus to publish changes on
     * @param clock      the clock that decides how much time has passed
     * @param log        the server log
     */
    public EscalationMonitor(EscalationService escalation, EventBus bus,
                             Clock clock, ServerLog log) {
        this(escalation, bus, clock, log, null);
    }

    /**
     * Creates a monitor that reads the incidents while no command is
     * changing them.
     *
     * @param escalation the escalation service
     * @param bus        the bus to publish changes on
     * @param clock      the clock that decides how much time has passed
     * @param log        the server log
     * @param dispatcher the dispatcher whose read lock is held, or null
     */
    public EscalationMonitor(EscalationService escalation, EventBus bus,
                             Clock clock, ServerLog log,
                             RequestDispatcher dispatcher) {
        this.escalation = escalation;
        this.bus = bus;
        this.clock = clock;
        this.log = log;
        this.dispatcher = dispatcher;
        this.lastStates = new HashMap<>();
    }

    /**
     * Starts checking now and then every {@link #PERIOD_SECONDS} seconds.
     */
    public synchronized void start() {
        if (this.timer != null) {
            return;
        }
        this.timer = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "drs-escalation-monitor");
            thread.setDaemon(true);
            return thread;
        });
        this.timer.scheduleAtFixedRate(this::checkSafely, 0, PERIOD_SECONDS,
                TimeUnit.SECONDS);
    }

    /**
     * Evaluates every open incident and publishes an event for each one
     * whose escalation state has changed since the last check. An incident
     * seen for the first time counts as having been On track.
     *
     * @return the events published, in the order published
     */
    public synchronized List<ServerEvent> check() {
        LocalDateTime now = LocalDateTime.now(this.clock);
        List<EscalationAlert> alerts = read(() ->
                this.escalation.evaluateAllOpen(now));
        Map<String, EscalationState> current = new HashMap<>();
        List<ServerEvent> published = new ArrayList<>();
        for (EscalationAlert alert : alerts) {
            String incidentId = alert.getIncident().getIncidentId();
            current.put(incidentId, alert.getState());
            EscalationState before = this.lastStates.getOrDefault(
                    incidentId, EscalationState.ON_TRACK);
            if (alert.getState() != before) {
                ServerEvent event = ServerEvent.about(EventType.ESCALATION,
                        ChangeEvents.INCIDENT, incidentId, describe(alert),
                        now);
                published.add(event);
            }
        }
        this.lastStates.clear();
        this.lastStates.putAll(current);
        for (ServerEvent event : published) {
            this.bus.publish(event);
        }
        return published;
    }

    /**
     * Returns the last known state of an incident.
     *
     * @param incidentId the incident
     * @return the state, or null if it was not open at the last check
     */
    public synchronized EscalationState lastStateOf(String incidentId) {
        return this.lastStates.get(incidentId);
    }

    /**
     * Stops the checks.
     */
    @Override
    public synchronized void close() {
        if (this.timer != null) {
            this.timer.shutdownNow();
            this.timer = null;
        }
    }

    /**
     * Runs a check from the timer. An exception would cancel every later
     * check, so it is logged instead.
     */
    private void checkSafely() {
        try {
            check();
        } catch (RuntimeException e) {
            this.log.error("Escalation check failed", e);
        }
    }

    /**
     * Reads under the dispatcher's read lock when there is a dispatcher.
     *
     * @param work the read
     * @param <T>  the type of its result
     * @return the result
     */
    private <T> T read(Supplier<T> work) {
        if (this.dispatcher == null) {
            return work.get();
        }
        return this.dispatcher.whileReading(work);
    }

    /**
     * Writes the feed line for a change of escalation state.
     *
     * @param alert the incident's new state
     * @return the summary
     */
    private static String describe(EscalationAlert alert) {
        Incident incident = alert.getIncident();
        return String.format("Incident %s (%s) is now %s: %d min since it "
                + "was raised, target %d min", incident.getIncidentId(),
                incident.getPriority().getDisplayName(),
                alert.getState().getDisplayName(),
                alert.getMinutesElapsed(),
                incident.getPriority().getTargetResponseMinutes());
    }

    /**
     * Describes the monitor.
     *
     * @return the number of incidents tracked and whether it runs
     */
    @Override
    public synchronized String toString() {
        String state = "stopped";
        if (this.timer != null) {
            state = "running every " + PERIOD_SECONDS + " s";
        }
        return "EscalationMonitor: " + this.lastStates.size()
                + " open incident(s), " + state;
    }
}
