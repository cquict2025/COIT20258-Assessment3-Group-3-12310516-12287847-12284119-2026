package au.edu.cqu.drs.server.modules.warning;

import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.warning.Warning;
import au.edu.cqu.drs.model.warning.WarningKeys;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.repository.WarningRepository;
import au.edu.cqu.drs.repository.memory.InMemoryWarningRepository;
import au.edu.cqu.drs.repository.mysql.MySqlDatabase;
import au.edu.cqu.drs.repository.mysql.MySqlWarningRepository;
import au.edu.cqu.drs.server.BadRequestException;
import au.edu.cqu.drs.server.CommandHandler;
import au.edu.cqu.drs.server.CommandModule;
import au.edu.cqu.drs.server.ServerContext;
import au.edu.cqu.drs.service.ServiceResult;
import au.edu.cqu.drs.service.WarningService;

import java.lang.reflect.Method;
import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * The server side of Feature 1: serves the six warning commands by calling
 * {@link WarningService}.
 *
 * <p>It is listed in {@code server/modules/modules.properties}, so the
 * server loads it on start-up without any change to the server's own
 * classes. The dispatcher has already checked the session and the access
 * matrix before a handler runs; the service checks the role again.</p>
 *
 * <p>Warnings are stored in MySQL when the server runs on MySQL and
 * {@code ServerContext} offers {@code getMySqlDatabase()}. Otherwise they
 * are kept in memory, which the log says.</p>
 */
public class WarningModule implements CommandModule {

    private static final Logger LOGGER =
            Logger.getLogger(WarningModule.class.getName());

    private final WarningService service;

    /**
     * Creates the module on the server's services and storage.
     *
     * @param context the server context
     */
    public WarningModule(ServerContext context) {
        this(new WarningService(chooseRepository(context),
                context.getIncidentService(),
                context.getAuthenticationService(),
                context.getAuditService(), context.getClock()));
    }

    /**
     * Creates the module around a service, for tests.
     *
     * @param service the warning service
     */
    public WarningModule(WarningService service) {
        this.service = service;
    }

    /**
     * Returns a handler for each warning command.
     *
     * @return the handlers
     */
    @Override
    public Map<Command, CommandHandler> handlers() {
        Map<Command, CommandHandler> handlers = new EnumMap<>(Command.class);
        handlers.put(Command.ISSUE_WARNING, (request, user) ->
                result(request, this.service.issue(user,
                        warning(request))));
        handlers.put(Command.UPDATE_WARNING, (request, user) ->
                result(request, this.service.update(user,
                        warning(request))));
        handlers.put(Command.CANCEL_WARNING, (request, user) ->
                result(request, this.service.cancel(user,
                        warningId(request),
                        request.get(WarningKeys.REASON, String.class))));
        handlers.put(Command.ACK_WARNING, (request, user) ->
                result(request, this.service.acknowledge(user,
                        warningId(request))));
        handlers.put(Command.LIST_WARNINGS, this::listForStaff);
        handlers.put(Command.LIST_PUBLIC_WARNINGS, (request, user) ->
                Response.success(request.getRequestId(),
                        this.service.listPublic()));
        return handlers;
    }

    /**
     * Describes the module.
     *
     * @return the service and its storage
     */
    @Override
    public String toString() {
        return "WarningModule: " + this.service;
    }

    /**
     * Lists every warning, or the versions of one when a code is given.
     */
    private Response listForStaff(Request request, User user) {
        String warningId = request.get(WarningKeys.WARNING_ID, String.class);
        if (warningId == null) {
            return Response.success(request.getRequestId(),
                    this.service.listForStaff());
        }
        return result(request, this.service.history(warningId));
    }

    private static Response result(Request request,
            ServiceResult<?> result) {
        return Response.fromServiceResult(request.getRequestId(), result);
    }

    private static Warning warning(Request request) {
        Warning warning = request.get(WarningKeys.WARNING, Warning.class);
        if (warning == null) {
            throw new BadRequestException(WarningKeys.WARNING,
                    "The request has no warning.");
        }
        return warning;
    }

    private static String warningId(Request request) {
        String warningId = request.get(WarningKeys.WARNING_ID, String.class);
        if (warningId == null || warningId.isBlank()) {
            throw new BadRequestException(WarningKeys.WARNING_ID,
                    "Choose a warning.");
        }
        return warningId;
    }

    /**
     * Uses MySQL when the context offers its database, otherwise memory.
     * The getter is looked up by name so this module compiles before the
     * MySQL wiring in ServerContext is merged.
     */
    private static WarningRepository chooseRepository(ServerContext context) {
        try {
            Method getter = context.getClass().getMethod("getMySqlDatabase");
            Object database = getter.invoke(context);
            if (database instanceof MySqlDatabase) {
                return new MySqlWarningRepository((MySqlDatabase) database);
            }
        } catch (ReflectiveOperationException e) {
            // No MySQL in this server yet; fall through to memory.
        }
        LOGGER.info("Warnings are kept in memory: the server is not running "
                + "on MySQL.");
        return new InMemoryWarningRepository();
    }
}
