package au.edu.cqu.drs.server;

import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.ReportStatus;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.protocol.ArgumentKey;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.LoginResult;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.service.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link RequestDispatcher} with the core handlers on in-memory
 * storage: routing, sessions, permissions and error handling.
 */
class RequestDispatcherTest {

    private ServerTestSupport.MutableClock clock;
    private ServerBootstrap bootstrap;
    private RequestDispatcher dispatcher;

    /**
     * Builds an in-memory server on a clock the test controls and takes its
     * dispatcher.
     *
     * @throws Exception if the server cannot be built
     */
    @BeforeEach
    void setUp() throws Exception {
        this.clock = new ServerTestSupport.MutableClock();
        this.bootstrap = new ServerBootstrap(
                ServerTestSupport.memoryConfig(4), new ServerLog(),
                this.clock);
        this.dispatcher = this.bootstrap.getDispatcher();
    }

    /**
     * Closes the server.
     */
    @AfterEach
    void tearDown() {
        this.bootstrap.close();
    }

    /**
     * Checks that PING and SUBMIT_REPORT work without signing in.
     */
    @Test
    @DisplayName("TC-RD-01 PING and SUBMIT_REPORT work without signing in")
    void publicCommandsWithoutLogin() {
        Response pong = this.dispatcher.dispatch(
                new Request(Command.PING, null));
        Response submitted = this.dispatcher.dispatch(
                new Request(Command.SUBMIT_REPORT, null).put(
                        ArgumentKey.REPORT, ServerTestSupport.validReport()));

        assertEquals("pong", pong.getValue());
        assertTrue(submitted.isSuccess(), submitted.toString());
        assertEquals(ReportStatus.SUBMITTED,
                ((DisasterReport) submitted.getValue()).getStatus());
    }

    /**
     * Checks that LOGIN returns a token and the user without password details.
     */
    @Test
    @DisplayName("TC-RD-02 LOGIN returns a token and the user without "
            + "password details")
    void loginReturnsTokenAndSafeUser() {
        Response response = this.dispatcher.dispatch(
                ServerTestSupport.loginRequest("coordinator"));

        LoginResult result = (LoginResult) response.getValue();
        User user = result.getUser();
        assertEquals("coordinator", user.getUsername());
        assertNull(user.getPasswordHash());
        assertNull(user.getPasswordSalt());
        Response incidents = this.dispatcher.dispatch(new Request(
                Command.LIST_INCIDENTS, result.getSessionToken()));
        assertTrue(incidents.isSuccess());
    }

    /**
     * Checks that a wrong password is refused by the service.
     */
    @Test
    @DisplayName("TC-RD-03 a wrong password is refused by the service")
    void wrongPasswordRefused() {
        Response response = this.dispatcher.dispatch(
                new Request(Command.LOGIN, null)
                        .put(ArgumentKey.USERNAME, "coordinator")
                        .put(ArgumentKey.PASSWORD, "wrong"));

        assertFalse(response.isSuccess());
        assertEquals(ErrorCode.PERMISSION_DENIED, response.getErrorCode());
        assertEquals("Username or password is incorrect.",
                response.getMessage());
    }

    /**
     * Checks that a missing, invalid or expired token is refused.
     */
    @Test
    @DisplayName("TC-RD-04 a missing, invalid or expired token is refused")
    void invalidOrExpiredToken() {
        String token = ServerTestSupport.login(this.dispatcher, "responder");

        Response missing = this.dispatcher.dispatch(
                new Request(Command.LIST_INCIDENTS, null));
        Response invalid = this.dispatcher.dispatch(
                new Request(Command.LIST_INCIDENTS, "forged-token"));
        this.clock.advance(Duration.ofMinutes(31));
        Response expired = this.dispatcher.dispatch(
                new Request(Command.LIST_INCIDENTS, token));

        for (Response response : List.of(missing, invalid, expired)) {
            assertEquals(ErrorCode.PERMISSION_DENIED,
                    response.getErrorCode());
            assertEquals("session", response.getAffectedField());
        }
    }

    /**
     * Checks that the policy refuses a command outside the user's role.
     */
    @Test
    @DisplayName("TC-RD-05 the policy refuses a command outside the user's "
            + "role")
    void permissionDenied() {
        String token = ServerTestSupport.login(this.dispatcher, "responder");

        Response response = this.dispatcher.dispatch(
                new Request(Command.DECIDE_REPORT, token)
                        .put(ArgumentKey.REPORT_ID, "RPT-0001")
                        .put(ArgumentKey.REPORT_STATUS,
                                ReportStatus.VALIDATED));

        assertEquals(ErrorCode.PERMISSION_DENIED, response.getErrorCode());
        assertNull(response.getAffectedField());
    }

    /**
     * Checks that a coordinator takes a report through to an incident.
     */
    @Test
    @DisplayName("TC-RD-06 a coordinator takes a report through to an "
            + "incident")
    void reportToIncident() {
        String token = ServerTestSupport.login(this.dispatcher,
                "coordinator");
        DisasterReport report = (DisasterReport) this.dispatcher.dispatch(
                new Request(Command.SUBMIT_REPORT, null).put(
                        ArgumentKey.REPORT, ServerTestSupport.validReport()))
                .getValue();

        Response decided = this.dispatcher.dispatch(
                new Request(Command.DECIDE_REPORT, token)
                        .put(ArgumentKey.REPORT_ID, report.getReportId())
                        .put(ArgumentKey.REPORT_STATUS,
                                ReportStatus.VALIDATED)
                        .put(ArgumentKey.REASON, "Confirmed by SES"));
        Response created = this.dispatcher.dispatch(
                new Request(Command.CREATE_INCIDENT_FROM_REPORT, token)
                        .put(ArgumentKey.REPORT_ID, report.getReportId()));
        Response listed = this.dispatcher.dispatch(
                new Request(Command.LIST_INCIDENTS, token));

        assertTrue(decided.isSuccess(), decided.toString());
        assertTrue(created.isSuccess(), created.toString());
        Incident incident = (Incident) created.getValue();
        assertEquals(1, ((List<?>) listed.getValue()).size());
        assertEquals(incident.getIncidentId(),
                ((Incident) ((List<?>) listed.getValue()).get(0))
                        .getIncidentId());
    }

    /**
     * Checks that a missing argument names the field, and an unknown reference
     * is NOT_FOUND.
     */
    @Test
    @DisplayName("TC-RD-07 a missing argument names the field, and an "
            + "unknown reference is NOT_FOUND")
    void badArguments() {
        String token = ServerTestSupport.login(this.dispatcher,
                "coordinator");

        Response missing = this.dispatcher.dispatch(
                new Request(Command.GET_INCIDENT, token));
        Response wrongType = this.dispatcher.dispatch(
                new Request(Command.GET_INCIDENT, token)
                        .put(ArgumentKey.INCIDENT_ID, 42));
        Response unknown = this.dispatcher.dispatch(
                new Request(Command.GET_INCIDENT, token)
                        .put(ArgumentKey.INCIDENT_ID, "INC-9999"));

        assertEquals(ErrorCode.VALIDATION_ERROR, missing.getErrorCode());
        assertEquals(ArgumentKey.INCIDENT_ID, missing.getAffectedField());
        assertEquals(ErrorCode.VALIDATION_ERROR, wrongType.getErrorCode());
        assertEquals(ErrorCode.NOT_FOUND, unknown.getErrorCode());
    }

    /**
     * Checks that a command with no installed module is reported as
     * unavailable.
     */
    @Test
    @DisplayName("TC-RD-08 a command with no installed module is reported "
            + "as unavailable")
    void uninstalledModuleCommand() {
        // Every module is installed now, so this dispatcher is built with
        // the core commands only, as a server would be before any module.
        RequestDispatcher coreOnly = new RequestDispatcher(
                new CoreCommands(this.bootstrap.getContext(),
                        this.bootstrap.getSessions()).handlers(),
                new ModuleRegistry(), this.bootstrap.getSessions(),
                new RoleBasedPolicy(), new ServerLog());
        String token = ServerTestSupport.login(coreOnly, "coordinator");

        Response response = coreOnly.dispatch(
                new Request(Command.ISSUE_WARNING, token));

        assertEquals(ErrorCode.ADAPTER_UNAVAILABLE, response.getErrorCode());
        assertTrue(response.getMessage().contains("not installed"));
    }

    /**
     * Checks that a handler fault becomes a failure without a stack trace, and
     * modules cannot replace core commands.
     */
    @Test
    @DisplayName("TC-RD-09 a handler fault becomes a failure without a stack "
            + "trace, and modules cannot replace core commands")
    void handlerFaultsAndModuleOverrides() {
        ServerLog log = new ServerLog();
        Map<Command, CommandHandler> core = new EnumMap<>(Command.class);
        core.put(Command.PING, (request, user) -> {
            throw new IllegalStateException("database exploded");
        });
        ModuleRegistry modules = new ModuleRegistry();
        modules.register(new CommandModule() {
            /**
             * Offers a PING handler that tries to replace the core one.
             *
             * @return the module's PING handler
             */
            @Override
            public Map<Command, CommandHandler> handlers() {
                return Map.of(Command.PING, (request, user) ->
                        Response.success(request.getRequestId(), "module"));
            }

            /**
             * Names the module in the server log.
             *
             * @return the module's name
             */
            @Override
            public String toString() {
                return "PING module";
            }
        });
        RequestDispatcher faulty = new RequestDispatcher(core, modules,
                this.bootstrap.getSessions(), new RoleBasedPolicy(), log);

        Response response = faulty.dispatch(new Request(Command.PING, null));

        assertEquals(ErrorCode.ADAPTER_UNAVAILABLE, response.getErrorCode());
        assertFalse(response.getMessage().contains("exploded"));
        assertFalse(response.getMessage().contains("Exception"));
        assertTrue(log.recentLines().stream()
                .anyMatch(line -> line.contains("database exploded")));
        assertTrue(log.recentLines().stream()
                .anyMatch(line -> line.contains("PING ignored")));
    }

    /**
     * Checks that LOGOUT ends the session.
     */
    @Test
    @DisplayName("TC-RD-10 LOGOUT ends the session")
    void logoutEndsSession() {
        String token = ServerTestSupport.login(this.dispatcher, "liaison");

        Response logout = this.dispatcher.dispatch(
                new Request(Command.LOGOUT, token));
        Response after = this.dispatcher.dispatch(
                new Request(Command.LIST_INCIDENTS, token));

        assertEquals(Boolean.TRUE, logout.getValue());
        assertEquals(ErrorCode.PERMISSION_DENIED, after.getErrorCode());
        assertNull(this.dispatcher.dispatch(null).getRequestId());
    }

    /**
     * Describes this test class.
     *
     * @return a short description of this object
     */
    @Override
    public String toString() {
        return "RequestDispatcherTest: tests for RequestDispatcher";
    }
}
