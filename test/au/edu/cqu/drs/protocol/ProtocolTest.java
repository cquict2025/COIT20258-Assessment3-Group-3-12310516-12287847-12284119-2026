package au.edu.cqu.drs.protocol;

import au.edu.cqu.drs.model.Assessment;
import au.edu.cqu.drs.model.AuditEvent;
import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.DepartmentAssignment;
import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.DuplicateCandidate;
import au.edu.cqu.drs.model.EscalationAlert;
import au.edu.cqu.drs.model.EscalationState;
import au.edu.cqu.drs.model.FieldUpdate;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.model.ResourceAllocation;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.service.ErrorCode;
import au.edu.cqu.drs.service.ServiceResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Round-trip tests for the protocol messages: what is sent through
 * {@link MessageStreams} must arrive with every field intact.
 */
class ProtocolTest {

    private static final LocalDateTime OBSERVED =
            LocalDateTime.of(2026, 10, 12, 9, 30);

    @Test
    @DisplayName("TC-PR-01 a request keeps its ID, command, token, time, "
            + "arguments and signature")
    void requestRoundTrip() throws Exception {
        DisasterReport report = sampleReport();
        Request request = new Request(Command.SUBMIT_REPORT, "token-123")
                .put("report", report)
                .put("count", 3);
        request.setSignature(new byte[] {1, 2, 3});

        Request copy = roundTrip(request, Request.class);

        assertEquals(request.getRequestId(), copy.getRequestId());
        assertEquals(Command.SUBMIT_REPORT, copy.getCommand());
        assertEquals("token-123", copy.getSessionToken());
        assertEquals(request.getTimestamp(), copy.getTimestamp());
        assertEquals(3, copy.get("count", Integer.class));
        assertArrayEquals(new byte[] {1, 2, 3}, copy.getSignature());
        DisasterReport sent = copy.get("report", DisasterReport.class);
        assertEquals("RPT-0001", sent.getReportId());
        assertEquals(DisasterType.FLOOD, sent.getDisasterType());
        assertEquals(OBSERVED, sent.getObservedAt());
        assertEquals("Laverton", sent.getLocation().getSuburb());
        assertEquals(-37.86, sent.getLocation().getLatitude(), 0.0001);
    }

    @Test
    @DisplayName("TC-PR-02 a successful response carries a whole incident "
            + "with its assignments and allocations")
    void successResponseRoundTrip() throws Exception {
        Incident incident = sampleIncident();

        Response copy = roundTrip(Response.success("r-1", incident),
                Response.class);

        assertTrue(copy.isSuccess());
        assertEquals("r-1", copy.getRequestId());
        Incident sent = (Incident) copy.getValue();
        assertEquals("INC-0001", sent.getIncidentId());
        assertEquals(List.of("RPT-0001"), sent.getSourceReportIds());
        DepartmentAssignment assignment = sent.getAssignments().get(0);
        assertEquals(Department.FIRE_AND_EMERGENCY,
                assignment.getDepartment());
        assertEquals("Sandbags",
                assignment.getAllocations().get(0).getResourceName());
    }

    @Test
    @DisplayName("TC-PR-03 a failed response keeps its error code, message "
            + "and field")
    void failureResponseRoundTrip() throws Exception {
        Response response = Response.failure("r-2",
                ErrorCode.VALIDATION_ERROR, "Enter a description.",
                "description");

        Response copy = roundTrip(response, Response.class);

        assertFalse(copy.isSuccess());
        assertEquals("r-2", copy.getRequestId());
        assertEquals(ErrorCode.VALIDATION_ERROR, copy.getErrorCode());
        assertEquals("Enter a description.", copy.getMessage());
        assertEquals("description", copy.getAffectedField());
        assertNull(copy.getValue());
    }

    @Test
    @DisplayName("TC-PR-04 every model class survives a round trip")
    void everyModelClassRoundTrips() throws Exception {
        Incident incident = sampleIncident();
        List<Serializable> objects = List.of(
                sampleReport(),
                incident,
                incident.getAssignments().get(0),
                incident.getAssignments().get(0).getAllocations().get(0),
                new Location("1 Test Street", "Laverton", -37.86, 144.77),
                new Assessment(),
                new AuditEvent("coordinator", "CREATE", "Incident",
                        "INC-0001", "created", OBSERVED),
                new DuplicateCandidate(),
                new EscalationAlert(incident, EscalationState.AT_RISK, 50,
                        10, 83),
                new FieldUpdate(),
                new User("USR-0001", "coordinator", "Casey Coordinator",
                        UserRole.COORDINATOR));

        for (Serializable object : objects) {
            Object copy = roundTrip(Response.success("r", object),
                    Response.class).getValue();
            assertEquals(object.getClass(), copy.getClass());
            assertEquals(object.toString(), copy.toString());
        }
    }

    @Test
    @DisplayName("TC-PR-05 lists, sets and maps from java.util are accepted")
    void collectionsAreAccepted() throws Exception {
        EnumMap<Priority, Integer> counts = new EnumMap<>(Priority.class);
        counts.put(Priority.P1, 2);
        Request request = new Request(Command.LIST_INCIDENTS, "t")
                .put("immutableList", (Serializable) List.of("a", "b"))
                .put("immutableMap", (Serializable) Map.of("k", 1))
                .put("arrayList", new ArrayList<>(List.of(1, 2, 3)))
                .put("hashSet", new HashSet<>(Set.of("x")))
                .put("enumSet", EnumSet.of(Priority.P1, Priority.P2))
                .put("enumMap", counts);

        Request copy = roundTrip(request, Request.class);

        assertEquals(List.of("a", "b"), copy.get("immutableList", List.class));
        assertEquals(Map.of("k", 1), copy.get("immutableMap", Map.class));
        assertEquals(List.of(1, 2, 3), copy.get("arrayList", List.class));
        assertEquals(Set.of("x"), copy.get("hashSet", Set.class));
        assertEquals(EnumSet.of(Priority.P1, Priority.P2),
                copy.get("enumSet", Set.class));
        assertEquals(counts, copy.get("enumMap", Map.class));
    }

    @Test
    @DisplayName("TC-PR-06 a response converts to and from a service result")
    void serviceResultConversion() {
        Incident incident = sampleIncident();
        Response success = Response.fromServiceResult("r-3",
                ServiceResult.success(incident));
        Response failure = Response.fromServiceResult("r-4",
                ServiceResult.failure(ErrorCode.PERMISSION_DENIED,
                        "Only coordinators may do this.", "role"));

        ServiceResult<Incident> back = success.toServiceResult(
                Incident.class);
        ServiceResult<Incident> refused = failure.toServiceResult(
                Incident.class);

        assertTrue(back.isSuccess());
        assertEquals("INC-0001", back.getValue().getIncidentId());
        assertTrue(refused.isFailure());
        assertEquals(ErrorCode.PERMISSION_DENIED, refused.getErrorCode());
        assertEquals("Only coordinators may do this.", refused.getMessage());
        assertEquals("role", refused.getAffectedField());
        assertThrows(IllegalArgumentException.class,
                () -> success.toServiceResult(User.class));
        assertThrows(IllegalArgumentException.class,
                () -> Response.fromServiceResult("r-5",
                        ServiceResult.success(new Object())));
    }

    @Test
    @DisplayName("TC-PR-07 an object changed and sent again arrives with "
            + "the change")
    void resentObjectArrivesChanged() throws Exception {
        Incident incident = sampleIncident();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = MessageStreams.openOutput(bytes);
        MessageStreams.write(out, Response.success("first", incident));
        incident.setTitle("Flood at Laverton, upgraded");
        MessageStreams.write(out, Response.success("second", incident));

        ObjectInputStream in = MessageStreams.openInput(
                new ByteArrayInputStream(bytes.toByteArray()));
        Response first = MessageStreams.readResponse(in);
        Response second = MessageStreams.readResponse(in);

        assertEquals("Flood at Laverton",
                ((Incident) first.getValue()).getTitle());
        assertEquals("Flood at Laverton, upgraded",
                ((Incident) second.getValue()).getTitle());
    }

    @Test
    @DisplayName("TC-PR-08 reading a message of the wrong type fails")
    void wrongMessageTypeIsRejected() throws Exception {
        byte[] bytes = serialise(new Request(Command.PING, null));

        ObjectInputStream in = MessageStreams.openInput(
                new ByteArrayInputStream(bytes));

        assertThrows(InvalidObjectException.class,
                () -> MessageStreams.readResponse(in));
    }

    @Test
    @DisplayName("TC-PR-09 only PING, LOGIN, SUBMIT_REPORT, SAVE_DRAFT and "
            + "LIST_PUBLIC_WARNINGS work without a login")
    void publicCommands() {
        EnumSet<Command> open = EnumSet.noneOf(Command.class);
        for (Command command : Command.values()) {
            if (!command.isLoginRequired()) {
                open.add(command);
            }
        }

        assertEquals(EnumSet.of(Command.PING, Command.LOGIN,
                Command.SUBMIT_REPORT, Command.SAVE_DRAFT,
                Command.LIST_PUBLIC_WARNINGS), open);
    }

    @Test
    @DisplayName("TC-PR-10 a request refuses a missing command, a blank "
            + "argument name and a value of the wrong type")
    void requestValidation() {
        Request request = new Request(Command.GET_INCIDENT, "t")
                .put("incidentId", "INC-0001");

        assertThrows(IllegalArgumentException.class,
                () -> new Request(null, "t"));
        assertThrows(IllegalArgumentException.class,
                () -> request.put(" ", "value"));
        assertThrows(IllegalArgumentException.class,
                () -> request.get("incidentId", Integer.class));
        assertNull(request.get("missing", String.class));
        assertTrue(request.has("incidentId"));
        assertThrows(UnsupportedOperationException.class,
                () -> request.getPayload().put("x", "y"));
    }

    @Test
    @DisplayName("TC-PR-11 a request and response cross a real socket")
    void requestAndResponseOverSocket() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            CompletableFuture<Request> received =
                    CompletableFuture.supplyAsync(() -> answerOnce(server));
            try (Socket socket = new Socket("localhost",
                    server.getLocalPort())) {
                ObjectOutputStream out = MessageStreams.openOutput(socket);
                ObjectInputStream in = MessageStreams.openInput(socket);
                Request ping = new Request(Command.PING, null);

                MessageStreams.write(out, ping);
                Response pong = MessageStreams.readResponse(in);

                assertEquals(ping.getRequestId(), pong.getRequestId());
                assertEquals("pong", pong.getValue());
            }
            assertEquals(Command.PING,
                    received.get(5, TimeUnit.SECONDS).getCommand());
        }
    }

    @Test
    @DisplayName("TC-PR-12 toString is readable and never shows the full "
            + "session token")
    void readableToString() {
        Request request = new Request(Command.GET_INCIDENT,
                "secret-session-token").put("incidentId", "INC-0001");
        Response success = Response.success("r-6", "pong");
        Response failure = Response.failure("r-7", ErrorCode.NOT_FOUND,
                "No such incident.", "incidentId");

        String text = request.toString();

        assertTrue(text.contains("GET_INCIDENT"));
        assertTrue(text.contains("[incidentId]"));
        assertTrue(text.contains("token secr..."));
        assertFalse(text.contains("secret-session-token"));
        assertEquals("Response r-6: OK, pong", success.toString());
        assertEquals("Response r-7: FAILED NOT_FOUND on incidentId, "
                + "No such incident.", failure.toString());
        assertEquals("PING (public)", Command.PING.toString());
        assertEquals("LOGOUT", Command.LOGOUT.toString());
        assertTrue(new ProtocolFilter().toString().contains("depth <= 20"));
    }

    /**
     * Accepts one connection, reads a request and answers "pong".
     *
     * @param server the listening socket
     * @return the request received
     */
    private static Request answerOnce(ServerSocket server) {
        try (Socket socket = server.accept()) {
            ObjectOutputStream out = MessageStreams.openOutput(socket);
            ObjectInputStream in = MessageStreams.openInput(socket);
            Request request = MessageStreams.readRequest(in);
            MessageStreams.write(out,
                    Response.success(request.getRequestId(), "pong"));
            return request;
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Writes a message and reads it back through the filtered streams.
     *
     * @param message the message to send
     * @param type    the expected type
     * @param <T>     the expected type
     * @return the copy read back
     * @throws Exception if the round trip fails
     */
    static <T> T roundTrip(Serializable message, Class<T> type)
            throws Exception {
        ObjectInputStream in = MessageStreams.openInput(
                new ByteArrayInputStream(serialise(message)));
        return MessageStreams.read(in, type);
    }

    /**
     * Serialises a message the way the client and server do.
     *
     * @param message the message
     * @return the bytes written
     * @throws IOException if writing fails
     */
    static byte[] serialise(Serializable message) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ObjectOutputStream out = MessageStreams.openOutput(bytes);
        MessageStreams.write(out, message);
        return bytes.toByteArray();
    }

    /**
     * Builds a flood report at Laverton.
     *
     * @return the report
     */
    private static DisasterReport sampleReport() {
        return new DisasterReport("RPT-0001", DisasterType.FLOOD,
                new Location("1 Test Street", "Laverton", -37.86, 144.77),
                OBSERVED, "Water over the road");
    }

    /**
     * Builds an incident with one assignment holding one allocation.
     *
     * @return the incident
     */
    private static Incident sampleIncident() {
        Incident incident = new Incident("INC-0001", "Flood at Laverton",
                DisasterType.FLOOD,
                new Location("1 Test Street", "Laverton", -37.86, 144.77),
                OBSERVED);
        incident.addSourceReportId("RPT-0001");
        DepartmentAssignment assignment = new DepartmentAssignment(
                "ASG-0001", "INC-0001", Department.FIRE_AND_EMERGENCY,
                "Sandbag homes", "Urgent");
        assignment.addAllocation(new ResourceAllocation("ALC-0001",
                "ASG-0001", "Sandbags", Department.FIRE_AND_EMERGENCY, 200,
                OBSERVED, OBSERVED.plusHours(6)));
        incident.addAssignment(assignment);
        return incident;
    }
}
