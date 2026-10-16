package au.edu.cqu.drs.tools.migration;

import au.edu.cqu.drs.client.ServerConnection;
import au.edu.cqu.drs.model.AuditEvent;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.protocol.ArgumentKey;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.LoginResult;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.repository.sqlite.Database;
import au.edu.cqu.drs.repository.sqlite.SqliteAssessmentRepository;
import au.edu.cqu.drs.repository.sqlite.SqliteAssignmentRepository;
import au.edu.cqu.drs.repository.sqlite.SqliteAuditRepository;
import au.edu.cqu.drs.repository.sqlite.SqliteFieldUpdateRepository;
import au.edu.cqu.drs.repository.sqlite.SqliteIncidentRepository;
import au.edu.cqu.drs.repository.sqlite.SqliteReportRepository;
import au.edu.cqu.drs.repository.sqlite.SqliteUserRepository;
import au.edu.cqu.drs.server.modules.importer.ImportModule;

import java.io.Console;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Loads the Assessment 2 data into DRS-Enhanced through the server
 * (spec 2.1).
 *
 * <p>It reads a DRS-Initial SQLite database with DRS-Initial's own
 * adapters, signs in to the server as an administrator, and sends every
 * record as an {@code IMPORT_*} request. It never connects to MySQL: the
 * server decides what is stored. Records go in dependency order (users,
 * reports, incidents, assessments, assignments, field updates, then the
 * audit trail, oldest first), and the server skips any record it already
 * holds, so running the import again adds nothing.</p>
 *
 * <p>Usage: {@code A2DataImporter <drs.db> [host] [port] [admin-user]}.
 * The password is read from the console, or from the
 * {@code DRS_IMPORT_PASSWORD} environment variable when there is no
 * console.</p>
 */
public class A2DataImporter {

    private final ServerConnection connection;
    private final String sessionToken;

    /**
     * Creates an importer that sends over a signed-in connection.
     *
     * @param connection   the connection to the server
     * @param sessionToken an administrator's session token
     */
    public A2DataImporter(ServerConnection connection, String sessionToken) {
        this.connection = connection;
        this.sessionToken = sessionToken;
    }

    /**
     * Signs in an administrator.
     *
     * @param connection the connection to the server
     * @param username   the administrator's user name
     * @param password   the password
     * @return the session token
     * @throws IllegalStateException if the server refuses the sign-in
     */
    public static String signIn(ServerConnection connection, String username,
            char[] password) {
        Response response = connection.send(new Request(Command.LOGIN, null)
                .put(ArgumentKey.USERNAME, username)
                .put(ArgumentKey.PASSWORD, new String(password)));
        if (!response.isSuccess()) {
            throw new IllegalStateException("Sign-in refused: "
                    + response.getMessage());
        }
        return ((LoginResult) response.getValue()).getSessionToken();
    }

    /**
     * Reads a DRS-Initial database and sends every record to the server.
     *
     * @param sqliteFile the DRS-Initial drs.db
     * @return what was added, skipped and refused, per table
     * @throws IllegalArgumentException if the file does not exist
     */
    public ImportSummary importFrom(Path sqliteFile) {
        if (!Files.isRegularFile(sqliteFile)) {
            throw new IllegalArgumentException(sqliteFile
                    + " is not a DRS-Initial database file.");
        }
        ImportSummary summary = new ImportSummary();
        Database database = new Database(sqliteFile.toString());
        try {
            sendAll(summary, "users", Command.IMPORT_USER,
                    new SqliteUserRepository(database).findAll());
            sendAll(summary, "reports", Command.IMPORT_REPORT,
                    new SqliteReportRepository(database).findAll());
            List<Incident> incidents =
                    new SqliteIncidentRepository(database).findAll();
            sendAll(summary, "incidents", Command.IMPORT_INCIDENT,
                    incidents);
            SqliteAssessmentRepository assessments =
                    new SqliteAssessmentRepository(database);
            SqliteFieldUpdateRepository updates =
                    new SqliteFieldUpdateRepository(database);
            for (Incident incident : incidents) {
                sendAll(summary, "assessments", Command.IMPORT_ASSESSMENT,
                        assessments.findByIncidentId(
                                incident.getIncidentId()));
            }
            sendAll(summary, "assignments", Command.IMPORT_ASSIGNMENT,
                    new SqliteAssignmentRepository(database).findAll());
            for (Incident incident : incidents) {
                sendAll(summary, "field updates",
                        Command.IMPORT_FIELD_UPDATE,
                        updates.findByIncidentId(incident.getIncidentId()));
            }
            List<AuditEvent> trail = new ArrayList<>(
                    new SqliteAuditRepository(database).findAll());
            Collections.reverse(trail);
            sendAll(summary, "audit events", Command.IMPORT_AUDIT, trail);
        } finally {
            database.close();
        }
        return summary;
    }

    /**
     * Runs the import from the command line and prints a count per table.
     *
     * @param args the database file, then optional host, port and user
     */
    public static void main(String[] args) {
        if (args.length < 1 || args.length > 4) {
            System.err.println("Usage: A2DataImporter <drs.db> [host] "
                    + "[port] [admin-user]");
            System.exit(2);
        }
        String host = args.length > 1 ? args[1]
                : ServerConnection.DEFAULT_HOST;
        int port = args.length > 2 ? Integer.parseInt(args[2])
                : ServerConnection.DEFAULT_PORT;
        String username = args.length > 3 ? args[3] : "admin";
        try (ServerConnection connection = new ServerConnection(host, port)) {
            String token = signIn(connection, username, password(username));
            ImportSummary summary = new A2DataImporter(connection, token)
                    .importFrom(Path.of(args[0]));
            System.out.print(summary);
            System.exit(summary.getRefused() == 0 ? 0 : 1);
        } catch (RuntimeException e) {
            System.err.println("Import stopped: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Sends each record of one table to the server as an import command and
     * counts it as added, already there or refused.
     *
     * @param summary the summary to count into
     * @param table   the table name shown in the summary
     * @param command the import command for this kind of record
     * @param records the records to send
     */
    private void sendAll(ImportSummary summary, String table,
            Command command, List<? extends Serializable> records) {
        for (Serializable record : records) {
            Response response = this.connection.send(
                    new Request(command, this.sessionToken)
                            .put(ImportModule.RECORD, record));
            if (!response.isSuccess()) {
                summary.refused(table, record + ": " + response.getMessage());
            } else if (Boolean.TRUE.equals(response.getValue())) {
                summary.added(table);
            } else {
                summary.alreadyThere(table);
            }
        }
    }

    /**
     * Asks for the administrator's password on the console, or reads it from
     * the DRS_IMPORT_PASSWORD environment variable when there is no console.
     *
     * @param username the administrator's user name, shown in the prompt
     * @return the password
     * @throws IllegalStateException if there is no console and the variable
     *                               is not set
     */
    private static char[] password(String username) {
        Console console = System.console();
        if (console != null) {
            return console.readPassword("Password for %s: ", username);
        }
        String fromEnvironment = System.getenv("DRS_IMPORT_PASSWORD");
        if (fromEnvironment == null || fromEnvironment.isEmpty()) {
            throw new IllegalStateException("No console to type a password "
                    + "in; set DRS_IMPORT_PASSWORD.");
        }
        return fromEnvironment.toCharArray();
    }

    /**
     * Describes the importer.
     *
     * @return the server it sends to
     */
    @Override
    public String toString() {
        return "A2DataImporter: sending to " + this.connection;
    }
}
