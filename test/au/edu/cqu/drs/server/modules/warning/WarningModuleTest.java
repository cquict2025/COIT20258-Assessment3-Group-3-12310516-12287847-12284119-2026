package au.edu.cqu.drs.server.modules.warning;

import au.edu.cqu.drs.client.ServerConnection;
import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.ReportStatus;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.warning.Warning;
import au.edu.cqu.drs.model.warning.WarningAcknowledgement;
import au.edu.cqu.drs.model.warning.WarningKeys;
import au.edu.cqu.drs.model.warning.WarningLevel;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.LoginResult;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.server.ServerBootstrap;
import au.edu.cqu.drs.server.ServerContext;
import au.edu.cqu.drs.server.ServerLog;
import au.edu.cqu.drs.server.ServerTestSupport;
import au.edu.cqu.drs.service.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feature 1 end to end through a real socket: a server on a free port with
 * the warning module loaded from modules.properties, and the client's own
 * {@link ServerConnection}.
 */
class WarningModuleTest {

    private ServerBootstrap bootstrap;
    private ServerConnection connection;
    private String incidentId;

    @BeforeEach
    void startServer() throws Exception {
        this.bootstrap = new ServerBootstrap(
                ServerTestSupport.memoryConfig(4), new ServerLog(),
                Clock.systemDefaultZone());
        this.bootstrap.start();
        this.connection = new ServerConnection("localhost",
                this.bootstrap.getServer().getPort());
        this.incidentId = openIncident();
    }

    @AfterEach
    void stopServer() {
        this.connection.close();
        this.bootstrap.close();
    }

    @Test
    @DisplayName("TC-WM-01 the server loads the warning module")
    void moduleIsLoaded() {
        assertTrue(this.bootstrap.getLog().recentLines().stream()
                .noneMatch(line -> line.contains("WarningModule is not "
                        + "installed")));
    }

    @Test
    @DisplayName("TC-WM-02 a coordinator issues a warning and the public "
            + "sees it without signing in")
    void issueThenPublicBoard() {
        Response issued = send(signIn("coordinator"), Command.ISSUE_WARNING,
                WarningKeys.WARNING, draft());

        assertTrue(issued.isSuccess(), issued.toString());
        assertEquals("WRN-0001",
                ((Warning) issued.getValue()).getWarningId());

        Response board = this.connection.send(
                new Request(Command.LIST_PUBLIC_WARNINGS, null));
        assertTrue(board.isSuccess(), board.toString());
        List<?> warnings = (List<?>) board.getValue();
        assertEquals(1, warnings.size());
        assertEquals("Laverton", ((Warning) warnings.get(0))
                .getAffectedArea());
    }

    @Test
    @DisplayName("TC-WM-03 the server refuses a responder who tries to "
            + "issue a warning")
    void responderRefused() {
        Response refused = send(signIn("responder"), Command.ISSUE_WARNING,
                WarningKeys.WARNING, draft());

        assertEquals(ErrorCode.PERMISSION_DENIED, refused.getErrorCode());
    }

    @Test
    @DisplayName("TC-WM-04 a liaison acknowledges and the coordinator's "
            + "list shows it")
    void liaisonAcknowledges() {
        String coordinator = signIn("coordinator");
        Warning issued = (Warning) send(coordinator, Command.ISSUE_WARNING,
                WarningKeys.WARNING, draft()).getValue();

        Response ack = send(signIn("liaison"), Command.ACK_WARNING,
                WarningKeys.WARNING_ID, issued.getWarningId());
        Response list = send(coordinator, Command.LIST_WARNINGS, null, null);

        assertTrue(ack.isSuccess(), ack.toString());
        assertInstanceOf(WarningAcknowledgement.class, ack.getValue());
        Warning listed = (Warning) ((List<?>) list.getValue()).get(0);
        assertEquals(1, listed.getAcknowledgements().size());
    }

    @Test
    @DisplayName("TC-WM-05 an update and a cancel show up in the history")
    void historyThroughTheServer() {
        String coordinator = signIn("coordinator");
        Warning issued = (Warning) send(coordinator, Command.ISSUE_WARNING,
                WarningKeys.WARNING, draft()).getValue();
        Warning changed = issued.copy();
        changed.setMessage("Fire spreading towards Altona North.");
        send(coordinator, Command.UPDATE_WARNING, WarningKeys.WARNING,
                changed);
        Response cancel = this.connection.send(
                new Request(Command.CANCEL_WARNING, coordinator)
                        .put(WarningKeys.WARNING_ID, issued.getWarningId())
                        .put(WarningKeys.REASON, "Fire contained"));

        Response history = send(coordinator, Command.LIST_WARNINGS,
                WarningKeys.WARNING_ID, issued.getWarningId());

        assertTrue(cancel.isSuccess(), cancel.toString());
        assertEquals(3, ((List<?>) history.getValue()).size());
    }

    @Test
    @DisplayName("TC-WM-06 a request without a warning is answered with a "
            + "clear error")
    void missingWarningIsExplained() {
        Response response = send(signIn("coordinator"),
                Command.ISSUE_WARNING, null, null);

        assertEquals(ErrorCode.VALIDATION_ERROR, response.getErrorCode());
        assertEquals(WarningKeys.WARNING, response.getAffectedField());
    }

    private Response send(String token, Command command, String key,
            java.io.Serializable value) {
        Request request = new Request(command, token);
        if (key != null) {
            request.put(key, value);
        }
        return this.connection.send(request);
    }

    private String signIn(String username) {
        Response response = this.connection.send(
                ServerTestSupport.loginRequest(username));
        assertTrue(response.isSuccess(), response.toString());
        return ((LoginResult) response.getValue()).getSessionToken();
    }

    /**
     * Creates an open incident through the services, as a coordinator
     * would from a validated report.
     */
    private String openIncident() {
        ServerContext context = this.bootstrap.getContext();
        User coordinator = context.getUserRepository()
                .findByUsername("coordinator").orElseThrow();
        DisasterReport report = context.getReportService()
                .submitReport(ServerTestSupport.validReport()).getValue();
        context.getReportService().decideReport(coordinator,
                report.getReportId(), ReportStatus.VALIDATED, null);
        Incident incident = context.getIncidentService()
                .createFromReport(coordinator, context.getReportService()
                        .findById(report.getReportId()).orElseThrow())
                .getValue();
        return incident.getIncidentId();
    }

    private Warning draft() {
        return new Warning(this.incidentId, WarningLevel.WATCH_AND_ACT,
                "Laverton", "Smoke from a warehouse fire.",
                LocalDateTime.now().plusHours(4));
    }
}
