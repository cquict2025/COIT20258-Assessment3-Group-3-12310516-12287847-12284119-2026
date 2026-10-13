package au.edu.cqu.drs.server.modules.importer;

import au.edu.cqu.drs.model.Assessment;
import au.edu.cqu.drs.model.AuditEvent;
import au.edu.cqu.drs.model.DepartmentAssignment;
import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.FieldUpdate;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.repository.mysql.MySqlDatabase;
import au.edu.cqu.drs.repository.mysql.MySqlImportStore;
import au.edu.cqu.drs.security.FieldCipher;
import au.edu.cqu.drs.server.BadRequestException;
import au.edu.cqu.drs.server.CommandHandler;
import au.edu.cqu.drs.server.CommandModule;
import au.edu.cqu.drs.server.ServerContext;
import au.edu.cqu.drs.service.ErrorCode;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * The server side of the Assessment 2 data import (W7, spec 2.1): serves
 * the seven {@code IMPORT_*} commands, one record per request.
 *
 * <p>The import tool never writes to MySQL itself; it sends each record
 * here, and only an administrator may send them (the access matrix checks
 * that before a handler runs). Each answer is {@code true} if the record
 * was added and {@code false} if it was already there.</p>
 *
 * <p>The module needs the server to run on MySQL with field encryption.
 * It finds them through {@code ServerContext.getMySqlDatabase()} and
 * {@code getFieldCipher()}; without them every import command is refused
 * with a message saying why.</p>
 */
public class ImportModule implements CommandModule {

    /** Request argument holding the record to import. */
    public static final String RECORD = "record";

    /** Answer when the server cannot import. */
    public static final String NEEDS_MYSQL = "Importing needs the server to "
            + "run on MySQL (repository=mysql) with its keystore.";

    private static final Logger LOGGER =
            Logger.getLogger(ImportModule.class.getName());

    private final MySqlImportStore store;

    /**
     * Creates the module on the server's MySQL database, if it has one.
     *
     * @param context the server context
     */
    public ImportModule(ServerContext context) {
        this(findStore(context));
    }

    /**
     * Creates the module around a store, for tests; null refuses imports.
     *
     * @param store the import store, or null
     */
    public ImportModule(MySqlImportStore store) {
        this.store = store;
    }

    /**
     * Returns a handler for each import command.
     *
     * @return the handlers
     */
    @Override
    public Map<Command, CommandHandler> handlers() {
        Map<Command, CommandHandler> handlers = new EnumMap<>(Command.class);
        handlers.put(Command.IMPORT_USER,
                handler(User.class, this::importUser));
        handlers.put(Command.IMPORT_REPORT, handler(DisasterReport.class,
                record -> this.store.importReport(record)));
        handlers.put(Command.IMPORT_INCIDENT, handler(Incident.class,
                record -> this.store.importIncident(record)));
        handlers.put(Command.IMPORT_ASSESSMENT, handler(Assessment.class,
                record -> this.store.importAssessment(record)));
        handlers.put(Command.IMPORT_ASSIGNMENT,
                handler(DepartmentAssignment.class,
                        record -> this.store.importAssignment(record)));
        handlers.put(Command.IMPORT_FIELD_UPDATE, handler(FieldUpdate.class,
                record -> this.store.importFieldUpdate(record)));
        handlers.put(Command.IMPORT_AUDIT, handler(AuditEvent.class,
                record -> this.store.importAuditEvent(record)));
        return handlers;
    }

    /**
     * Describes the module.
     *
     * @return whether it can import
     */
    @Override
    public String toString() {
        return "ImportModule: " + (this.store == null ? "not available"
                : this.store.toString());
    }

    private boolean importUser(User user) {
        if (user.getPasswordHash() == null || user.getPasswordSalt() == null) {
            throw new BadRequestException(RECORD, "An imported account needs "
                    + "its password salt and hash.");
        }
        return this.store.importUser(user);
    }

    /**
     * Builds a handler that reads one record of a type and imports it.
     */
    private <T> CommandHandler handler(Class<T> type,
            Function<T, Boolean> importer) {
        return (request, user) -> {
            if (this.store == null) {
                return Response.failure(request.getRequestId(),
                        ErrorCode.ADAPTER_UNAVAILABLE, NEEDS_MYSQL);
            }
            return Response.success(request.getRequestId(),
                    importer.apply(record(request, type)));
        };
    }

    private static <T> T record(Request request, Class<T> type) {
        T record = request.get(RECORD, type);
        if (record == null) {
            throw new BadRequestException(RECORD, "The request has no "
                    + type.getSimpleName() + " to import.");
        }
        return record;
    }

    /**
     * Finds the MySQL database and field cipher through getters on the
     * server context. They are looked up by name so this module compiles
     * before the MySQL wiring in ServerContext is merged.
     */
    private static MySqlImportStore findStore(ServerContext context) {
        try {
            Object database = context.getClass()
                    .getMethod("getMySqlDatabase").invoke(context);
            Object cipher = context.getClass()
                    .getMethod("getFieldCipher").invoke(context);
            if (database instanceof MySqlDatabase
                    && cipher instanceof FieldCipher) {
                return new MySqlImportStore((MySqlDatabase) database,
                        (FieldCipher) cipher);
            }
        } catch (ReflectiveOperationException e) {
            // Not running on MySQL yet; fall through.
        }
        LOGGER.info("Import commands are refused: the server is not "
                + "running on MySQL with a keystore.");
        return null;
    }
}
