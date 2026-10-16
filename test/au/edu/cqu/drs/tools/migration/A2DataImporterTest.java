package au.edu.cqu.drs.tools.migration;

import au.edu.cqu.drs.client.ServerConnection;
import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.repository.mysql.MySqlAuditRepository;
import au.edu.cqu.drs.repository.mysql.MySqlDatabase;
import au.edu.cqu.drs.repository.mysql.MySqlImportStore;
import au.edu.cqu.drs.repository.mysql.MySqlIncidentRepository;
import au.edu.cqu.drs.repository.mysql.MySqlReportRepository;
import au.edu.cqu.drs.repository.mysql.MySqlTestDatabase;
import au.edu.cqu.drs.security.FieldCipher;
import au.edu.cqu.drs.server.AuthorisationPolicy;
import au.edu.cqu.drs.server.CoreCommands;
import au.edu.cqu.drs.server.DrsServer;
import au.edu.cqu.drs.server.ModuleRegistry;
import au.edu.cqu.drs.server.RequestDispatcher;
import au.edu.cqu.drs.server.ServerContext;
import au.edu.cqu.drs.server.ServerLog;
import au.edu.cqu.drs.server.ServerTestSupport;
import au.edu.cqu.drs.server.SessionManager;
import au.edu.cqu.drs.server.modules.importer.ImportModule;
import au.edu.cqu.drs.service.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Assessment 2 import end to end: a DRS-Initial SQLite file written by
 * DRS-Initial's own services, a server on a free port whose import module
 * writes to drs_test, and the importer talking to it over a real socket.
 * Skipped, with the reason, when MySQL is not available.
 */
class A2DataImporterTest {

    @TempDir
    Path folder;

    private MySqlDatabase database;
    private DrsServer server;
    private ServerContext context;
    private ServerConnection connection;
    private FieldCipher cipher;

    /**
     * Opens a fresh drs_test database, skipping the test when MySQL
     * is not available, then starts a server on a free port with the
     * import module and connects the client to it.
     *
     * @throws Exception if the server cannot be started
     */
    @BeforeEach
    void startServer() throws Exception {
        try {
            this.database = MySqlTestDatabase.openFresh();
        } catch (IllegalStateException e) {
            Assumptions.abort("A2 import test skipped: " + e.getMessage());
        }
        this.cipher = new FieldCipher(FieldCipher.generateKey());
        this.context = new ServerContext(ServerTestSupport.memoryConfig(4),
                Clock.systemDefaultZone());
        SessionManager sessions = new SessionManager(
                Clock.systemDefaultZone(), Duration.ofMinutes(30));
        ModuleRegistry modules = new ModuleRegistry();
        modules.register(new ImportModule(
                new MySqlImportStore(this.database, this.cipher)));
        ServerLog log = new ServerLog();
        RequestDispatcher dispatcher = new RequestDispatcher(
                new CoreCommands(this.context, sessions).handlers(), modules,
                sessions, AuthorisationPolicy.createDefault(), log);
        this.server = new DrsServer(dispatcher, log, 0, 4, 10_000);
        this.server.start();
        this.connection = new ServerConnection("localhost",
                this.server.getPort());
    }

    /**
     * Closes the connection, the server, the context and the database,
     * skipping any that were never opened.
     */
    @AfterEach
    void stopServer() {
        if (this.connection != null) {
            this.connection.close();
        }
        if (this.server != null) {
            this.server.stop();
        }
        if (this.context != null) {
            this.context.close();
        }
        if (this.database != null) {
            this.database.close();
        }
    }

    /**
     * Checks that every Assessment 2 table is imported through the
     * server with the expected counts, and that a second run adds
     * nothing because every record is already there.
     */
    @Test
    @DisplayName("TC-IM-01 every A2 table is loaded through the server, and "
            + "a second run adds nothing")
    void importsEverythingOnceOnly() {
        Path a2 = this.folder.resolve("drs.db");
        SampleA2Data.write(a2);
        String token = A2DataImporter.signIn(this.connection, "admin",
                ServerTestSupport.PASSWORD.toCharArray());
        A2DataImporter importer = new A2DataImporter(this.connection, token);

        ImportSummary first = importer.importFrom(a2);
        ImportSummary second = importer.importFrom(a2);

        assertEquals(0, first.getRefused(), first.toString());
        assertEquals(4, first.getAdded("users"));
        assertEquals(4, first.getAdded("reports"));
        assertEquals(3, first.getAdded("incidents"));
        assertEquals(3, first.getAdded("assessments"));
        assertEquals(1, first.getAdded("assignments"));
        assertEquals(3, first.getAdded("field updates"));
        assertTrue(first.getAdded("audit events") > 0);

        assertEquals(0, second.getRefused(), second.toString());
        for (String table : new String[] {"users", "reports", "incidents",
            "assessments", "assignments", "field updates",
            "audit events"}) {
            assertEquals(0, second.getAdded(table), table);
        }
        assertEquals(first.getAdded("audit events"),
                second.getAlreadyThere("audit events"));
    }

    /**
     * Checks that imported data keeps its links, that the reporter
     * name is stored encrypted but read back in clear, and that the
     * audit chain is still intact.
     *
     * @throws Exception if the database cannot be read
     */
    @Test
    @DisplayName("TC-IM-02 imported data keeps its links, its reporter "
            + "details are encrypted, and the audit chain stays intact")
    void importedDataIsUsable() throws Exception {
        Path a2 = this.folder.resolve("drs.db");
        SampleA2Data.write(a2);
        String token = A2DataImporter.signIn(this.connection, "admin",
                ServerTestSupport.PASSWORD.toCharArray());

        new A2DataImporter(this.connection, token).importFrom(a2);

        MySqlReportRepository reports =
                new MySqlReportRepository(this.database, this.cipher);
        DisasterReport fire = reports.findById("RPT-0001").orElseThrow();
        assertEquals("Priya Shah", fire.getReporterName());
        String stored = this.database.withConnection(c -> {
            try (var statement = c.prepareStatement("SELECT reporter_name "
                    + "FROM reports WHERE report_id = 'RPT-0001'");
                 var rs = statement.executeQuery()) {
                rs.next();
                return rs.getString(1);
            }
        });
        assertFalse(stored.contains("Priya"));
        assertEquals("RPT-0001", new MySqlIncidentRepository(this.database)
                .findById("INC-0001").orElseThrow().getSourceReportIds()
                .get(0));
        assertTrue(new MySqlAuditRepository(this.database).verifyChain()
                .isIntact());
    }

    /**
     * Checks that a coordinator is refused permission to import a
     * record.
     */
    @Test
    @DisplayName("TC-IM-03 only an administrator may import")
    void onlyAdministratorsImport() {
        String token = A2DataImporter.signIn(this.connection, "coordinator",
                ServerTestSupport.PASSWORD.toCharArray());

        Response response = this.connection.send(
                new Request(Command.IMPORT_REPORT, token)
                        .put(ImportModule.RECORD, new DisasterReport()));

        assertEquals(ErrorCode.PERMISSION_DENIED, response.getErrorCode());
    }

    /**
     * Describes this test class and the fixture it uses.
     *
     * @return the class name and the fixture state
     */
    @Override
    public String toString() {
        return "A2DataImporterTest[folder=" + this.folder
                + ", database=" + this.database
                + ", server=" + this.server
                + ", connection=" + this.connection + "]";
    }
}
