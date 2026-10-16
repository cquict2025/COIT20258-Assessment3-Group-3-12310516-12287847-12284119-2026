package au.edu.cqu.drs.server.event;

import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.EscalationState;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.IncidentStatus;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.repository.memory.InMemoryAuditRepository;
import au.edu.cqu.drs.repository.memory.InMemoryFieldUpdateRepository;
import au.edu.cqu.drs.repository.memory.InMemoryIncidentRepository;
import au.edu.cqu.drs.repository.memory.InMemoryUserRepository;
import au.edu.cqu.drs.server.ServerLog;
import au.edu.cqu.drs.server.ServerTestSupport.MutableClock;
import au.edu.cqu.drs.service.AuditService;
import au.edu.cqu.drs.service.AuthenticationService;
import au.edu.cqu.drs.service.EscalationService;
import au.edu.cqu.drs.service.IncidentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link EscalationMonitor} with a clock the test moves forward,
 * so no test waits for real minutes to pass.
 */
class EscalationMonitorTest {

    private MutableClock clock;
    private InMemoryIncidentRepository incidents;
    private EscalationMonitor monitor;
    private List<ServerEvent> received;

    /**
     * Builds an escalation monitor over in-memory incidents, with a clock the
     * test controls, and collects every event it publishes.
     */
    @BeforeEach
    void setUp() {
        this.clock = new MutableClock();
        this.incidents = new InMemoryIncidentRepository();
        AuditService audit = new AuditService(new InMemoryAuditRepository());
        IncidentService incidentService = new IncidentService(this.incidents,
                new InMemoryFieldUpdateRepository(),
                new AuthenticationService(new InMemoryUserRepository(),
                        audit), audit);
        EventBus bus = new EventBus();
        this.received = new CopyOnWriteArrayList<>();
        bus.subscribe(this.received::add);
        this.monitor = new EscalationMonitor(
                new EscalationService(incidentService), bus, this.clock,
                new ServerLog());
    }

    /**
     * Checks that a P1 with no field update is flagged At risk, then Breached
     * after 15 minutes, and announced once.
     */
    @Test
    @DisplayName("TC-EV-06 a P1 with no field update is flagged At risk, "
            + "then Breached after 15 minutes, and announced once")
    void flagsBreachedP1Once() {
        Incident incident = saveIncident(Priority.P1);
        String id = incident.getIncidentId();

        List<ServerEvent> atStart = this.monitor.check();
        this.clock.advance(Duration.ofMinutes(12));
        List<ServerEvent> atRisk = this.monitor.check();
        this.clock.advance(Duration.ofMinutes(4));
        List<ServerEvent> breached = this.monitor.check();
        this.clock.advance(Duration.ofMinutes(1));
        List<ServerEvent> minuteLater = this.monitor.check();
        this.clock.advance(Duration.ofMinutes(1));
        List<ServerEvent> twoMinutesLater = this.monitor.check();

        assertTrue(atStart.isEmpty());
        assertEquals(1, atRisk.size());
        assertTrue(atRisk.get(0).getSummary().contains("At risk"),
                atRisk.get(0).getSummary());
        assertEquals(1, breached.size());
        ServerEvent event = breached.get(0);
        assertEquals(EventType.ESCALATION, event.getType());
        assertEquals("Incident", event.getEntityType());
        assertEquals(id, event.getEntityId());
        assertTrue(event.getSummary().contains("Breached"),
                event.getSummary());
        assertTrue(minuteLater.isEmpty());
        assertTrue(twoMinutesLater.isEmpty());
        assertEquals(2, this.received.size());
        assertEquals(EscalationState.BREACHED, this.monitor.lastStateOf(id));
    }

    /**
     * Checks that a P1 answered in time stays On track and is never announced;
     * a closed incident is forgotten.
     */
    @Test
    @DisplayName("TC-EV-07 a P1 answered in time stays On track and is "
            + "never announced; a closed incident is forgotten")
    void answeredIncidentIsQuiet() {
        Incident incident = saveIncident(Priority.P1);
        incident.setFirstResponseAt(LocalDateTime.now(this.clock)
                .plusMinutes(5));
        this.incidents.save(incident);

        this.clock.advance(Duration.ofMinutes(30));
        List<ServerEvent> events = this.monitor.check();
        this.incidents.save(closed(incident));
        this.monitor.check();

        assertTrue(events.isEmpty());
        assertTrue(this.received.isEmpty());
        assertNull(this.monitor.lastStateOf(incident.getIncidentId()));
    }

    /**
     * Saves an open incident raised now by the test clock.
     *
     * @param priority the incident priority
     * @return the saved incident
     */
    private Incident saveIncident(Priority priority) {
        Incident incident = new Incident(null, "Flood - Laverton",
                DisasterType.FLOOD, new Location("1 Test Street", "Laverton",
                        -37.86, 144.77), LocalDateTime.now(this.clock));
        incident.setPriority(priority);
        return this.incidents.save(incident);
    }

    /**
     * Marks an incident closed.
     *
     * @param incident the incident
     * @return the same incident, now closed
     */
    private Incident closed(Incident incident) {
        incident.setStatus(IncidentStatus.CLOSED);
        incident.setClosedAt(LocalDateTime.now(this.clock));
        return incident;
    }

    /**
     * Describes this test class.
     *
     * @return a short description of this object
     */
    @Override
    public String toString() {
        return "EscalationMonitorTest: tests for EscalationMonitor";
    }
}
