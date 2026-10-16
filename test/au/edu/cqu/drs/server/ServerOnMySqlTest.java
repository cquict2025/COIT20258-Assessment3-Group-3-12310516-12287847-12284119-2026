package au.edu.cqu.drs.server;

import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.ReportStatus;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.warning.Warning;
import au.edu.cqu.drs.model.warning.WarningKeys;
import au.edu.cqu.drs.model.warning.WarningLevel;
import au.edu.cqu.drs.protocol.ArgumentKey;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.repository.mysql.MySqlDatabase;
import au.edu.cqu.drs.repository.mysql.MySqlTestDatabase;
import au.edu.cqu.drs.security.FieldCipher;
import au.edu.cqu.drs.server.modules.importer.ImportModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the whole server on MySQL (T6): the services on Milan's adapters,
 * the warning and import modules on the same database, and personal
 * details encrypted with the keystore key.
 *
 * <p>Like the repository integration tests, these use the drs_test
 * database and are skipped when MySQL or the drs_test account is not
 * available. Set DRS_TEST_MYSQL_HOST, DRS_TEST_MYSQL_PORT,
 * DRS_TEST_MYSQL_USER and DRS_TEST_MYSQL_PASSWORD to point them at another
 * server.</p>
 */
class ServerOnMySqlTest {

    private static final String KEYSTORE_PASSWORD = "server-test-keystore";

    private static String skipReason;

    @TempDir
    Path folder;

    private ServerBootstrap bootstrap;

    /**
     * Empties the MySQL test database, or records why the tests must be
     * skipped when MySQL cannot be reached.
     */
    @BeforeAll
    static void emptyTheTestDatabase() {
        try (MySqlDatabase database = MySqlTestDatabase.openFresh()) {
            skipReason = null;
        } catch (IllegalStateException e) {
            skipReason = "MySQL server tests skipped: " + e.getMessage();
            System.out.println(skipReason);
        }
    }

    /**
     * Starts the server on MySQL, unless the tests are being skipped.
     *
     * @throws Exception if the server cannot start
     */
    @BeforeEach
    void start() throws Exception {
        Assumptions.assumeTrue(skipReason == null, skipReason);
        this.bootstrap = startServer();
    }

    /**
     * Closes the server, if one was started.
     */
    @AfterEach
    void stop() {
        if (this.bootstrap != null) {
            this.bootstrap.close();
        }
    }

    /**
     * Checks that on MySQL the server signs in a seeded account, encrypts the
     * reporter's name and keeps data after a restart.
     *
     * @throws Exception if the test cannot complete its setup or I/O
     */
    @Test
    @DisplayName("TC-CF-04 on MySQL the server signs in a seeded account, "
            + "encrypts the reporter's name and keeps data after a "
            + "restart")
    void runsOnMySql() throws Exception {
        ServerContext context = this.bootstrap.getContext();
        assertNotNull(context.getMySqlDatabase());
        assertNotNull(context.getFieldCipher());
        String token = ServerTestSupport.login(
                this.bootstrap.getDispatcher(), "coordinator");
        DisasterReport report = ServerTestSupport.validReport();
        report.setReporterName("Alex Reporter");
        Response submitted = this.bootstrap.getDispatcher().dispatch(
                new Request(Command.SUBMIT_REPORT, null)
                        .put(ArgumentKey.REPORT, report));
        assertTrue(submitted.isSuccess(), submitted.toString());
        String reportId = ((DisasterReport) submitted.getValue())
                .getReportId();

        String stored = storedReporterName(context, reportId);
        this.bootstrap.close();
        this.bootstrap = startServer();
        String newToken = ServerTestSupport.login(
                this.bootstrap.getDispatcher(), "coordinator");
        Response found = this.bootstrap.getDispatcher().dispatch(
                new Request(Command.GET_REPORT, newToken)
                        .put(ArgumentKey.REPORT_ID, reportId));

        assertNotNull(token);
        assertTrue(stored.startsWith(FieldCipher.PREFIX), stored);
        assertTrue(found.isSuccess(), found.toString());
        assertEquals("Alex Reporter",
                ((DisasterReport) found.getValue()).getReporterName());
    }

    /**
     * Checks that on MySQL the warning module stores warnings in the database,
     * the import module is active and permissions come from the access matrix.
     *
     * @throws Exception if the test cannot complete its setup or I/O
     */
    @Test
    @DisplayName("TC-CF-05 on MySQL the warning module stores warnings in "
            + "the database, the import module is active and permissions "
            + "come from the access matrix")
    void modulesUseMySql() throws Exception {
        String incidentId = openIncident();
        String token = ServerTestSupport.login(
                this.bootstrap.getDispatcher(), "coordinator");
        Warning draft = new Warning(incidentId, WarningLevel.WATCH_AND_ACT,
                "Laverton", "Smoke from a warehouse fire.",
                LocalDateTime.now().plusHours(4));
        Response issued = this.bootstrap.getDispatcher().dispatch(
                new Request(Command.ISSUE_WARNING, token)
                        .put(WarningKeys.WARNING, draft));
        String admin = ServerTestSupport.login(
                this.bootstrap.getDispatcher(), "admin");
        Response emptyImport = this.bootstrap.getDispatcher().dispatch(
                new Request(Command.IMPORT_REPORT, admin));

        assertTrue(issued.isSuccess(), issued.toString());
        assertEquals(1, countRows("warnings"));
        assertNotEquals(ImportModule.NEEDS_MYSQL, emptyImport.getMessage());
        List<String> log = this.bootstrap.getLog().recentLines();
        assertTrue(log.stream().anyMatch(line -> line.contains(
                "au.edu.cqu.drs.security.AccessMatrix")), log.toString());
    }

    /**
     * Starts a server on drs_test with a keystore in the test folder.
     *
     * @return the started server
     * @throws Exception if the settings cannot be read or the port opened
     */
    private ServerBootstrap startServer() throws Exception {
        Properties properties = ServerConfig.defaults();
        properties.setProperty("repository", "mysql");
        properties.setProperty("port", "0");
        properties.setProperty("threads", "4");
        properties.setProperty("mysql.host",
                env("DRS_TEST_MYSQL_HOST", "localhost"));
        properties.setProperty("mysql.port",
                env("DRS_TEST_MYSQL_PORT", "3306"));
        properties.setProperty("mysql.database", "drs_test");
        properties.setProperty("mysql.user",
                env("DRS_TEST_MYSQL_USER", "drs_test"));
        properties.setProperty("mysql.password",
                env("DRS_TEST_MYSQL_PASSWORD", "drs_test"));
        properties.setProperty("mysql.pool.size", "4");
        properties.setProperty("keystore.file",
                this.folder.resolve("test-keystore.p12").toString());
        ServerConfig config = new ServerConfig(properties, Map.of(
                ServerConfig.KEYSTORE_PASSWORD_VARIABLE, KEYSTORE_PASSWORD));
        ServerBootstrap started = new ServerBootstrap(config,
                new ServerLog(), Clock.systemDefaultZone());
        started.start();
        return started;
    }

    /**
     * Creates an open incident from a validated report.
     *
     * @return the incident reference
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

    /**
     * Reads the reporter name exactly as it is stored.
     *
     * @param context  the server context
     * @param reportId the report
     * @return the stored value, encrypted or not
     * @throws Exception if the query fails
     */
    private static String storedReporterName(ServerContext context,
                                             String reportId)
            throws Exception {
        return context.getMySqlDatabase().withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT reporter_name FROM reports WHERE report_id = ?")) {
                statement.setString(1, reportId);
                try (ResultSet rows = statement.executeQuery()) {
                    rows.next();
                    return rows.getString(1);
                }
            }
        });
    }

    /**
     * Counts the rows of a table.
     *
     * @param table the table name
     * @return the number of rows
     * @throws Exception if the query fails
     */
    private int countRows(String table) throws Exception {
        return this.bootstrap.getContext().getMySqlDatabase()
                .withConnection(connection -> {
                    try (PreparedStatement statement = connection
                            .prepareStatement("SELECT COUNT(*) FROM "
                                    + table);
                         ResultSet rows = statement.executeQuery()) {
                        rows.next();
                        return rows.getInt(1);
                    }
                });
    }

    /**
     * Reads an environment variable with a fallback.
     *
     * @param name     the variable
     * @param fallback the value used when it is unset
     * @return the value
     */
    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    /**
     * Describes this test class.
     *
     * @return a short description of this object
     */
    @Override
    public String toString() {
        return "ServerOnMySqlTest: tests for the server running on MySQL";
    }
}
