package au.edu.cqu.drs.server.event;

import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.protocol.ArgumentKey;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.service.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link EventBus} and {@link ChangeEvents}.
 */
class EventBusTest {

    @Test
    @DisplayName("TC-EV-01 an event reaches all three subscribers")
    void eventReachesEverySubscriber() {
        EventBus bus = new EventBus();
        List<List<ServerEvent>> received = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<ServerEvent> inbox = new CopyOnWriteArrayList<>();
            received.add(inbox);
            bus.subscribe(inbox::add);
        }
        ServerEvent event = ServerEvent.about(EventType.CHANGE, "Report",
                "R-0001", "Report R-0001 submitted", LocalDateTime.now());

        bus.publish(event);

        assertEquals(3, bus.getSubscriberCount());
        for (List<ServerEvent> inbox : received) {
            assertEquals(List.of(event), inbox);
        }
    }

    @Test
    @DisplayName("TC-EV-02 a subscriber that fails does not stop the others "
            + "and can be removed")
    void failingSubscriberIsSkipped() {
        EventBus bus = new EventBus();
        List<ServerEvent> inbox = new ArrayList<>();
        EventSubscriber faulty = event -> {
            throw new IllegalStateException("broken subscriber");
        };
        bus.subscribe(faulty);
        bus.subscribe(inbox::add);
        List<Integer> counts = new ArrayList<>();
        bus.addSubscriberCountListener(counts::add);

        bus.publish(ServerEvent.heartbeat(LocalDateTime.now()));
        bus.unsubscribe(faulty);
        bus.unsubscribe(faulty);

        assertEquals(1, inbox.size());
        assertEquals(List.of(1), counts);
    }

    @Test
    @DisplayName("TC-EV-03 a successful change becomes an event naming the "
            + "entity and who acted; reads and failures publish nothing")
    void changesAreDescribed() {
        LocalDateTime now = LocalDateTime.now();
        DisasterReport report = new DisasterReport();
        report.setReportId("R-0007");
        Request submit = new Request(Command.SUBMIT_REPORT, null)
                .put(ArgumentKey.REPORT, report);
        Response saved = Response.success(submit.getRequestId(), report);
        User coordinator = new User("U-1", "coordinator", "Casey Coord",
                UserRole.COORDINATOR);
        Request list = new Request(Command.LIST_REPORTS, "token");
        Request imported = new Request(Command.IMPORT_REPORT, "token");

        ServerEvent event = ChangeEvents.describe(submit, saved, null, now);
        ServerEvent byStaff = ChangeEvents.describe(submit, saved,
                coordinator, now);

        assertEquals(EventType.CHANGE, event.getType());
        assertEquals("Report", event.getEntityType());
        assertEquals("R-0007", event.getEntityId());
        assertEquals("Report R-0007 submitted by a member of the public",
                event.getSummary());
        assertTrue(byStaff.getSummary().endsWith("by Casey Coord"));
        assertNull(ChangeEvents.describe(submit, Response.failure(
                submit.getRequestId(), ErrorCode.VALIDATION_ERROR, "bad"),
                null, now));
        assertNull(ChangeEvents.describe(list, Response.success(
                list.getRequestId(), new ArrayList<>()), coordinator, now));
        assertFalse(ChangeEvents.isPublished(Command.IMPORT_REPORT));
        assertNull(ChangeEvents.describe(imported, Response.success(
                imported.getRequestId(), true), coordinator, now));
        assertTrue(ChangeEvents.isPublished(Command.ISSUE_WARNING));
    }
}
