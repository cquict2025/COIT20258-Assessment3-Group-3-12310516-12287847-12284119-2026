package au.edu.cqu.drs.server;

import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.service.ErrorCode;

import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Routes each request to the handler for its command, after checking the
 * session and the {@link AuthorisationPolicy}.
 *
 * <p>Handlers are held in an {@link EnumMap}, which looks up a command by its
 * ordinal. Core handlers come first; a module may add commands but never
 * replace a core one. Every exception is turned into a failed
 * {@link Response}, so the client never sees a stack trace.</p>
 *
 * <p>The SQLite and in-memory adapters were written for one user: IDs are
 * generated with {@code SELECT MAX} and the in-memory stores are plain maps.
 * A {@link ReadWriteLock} therefore lets reads run in parallel and gives each
 * state-changing command exclusive access.</p>
 */
public class RequestDispatcher {

    /** Commands that only read data and may run in parallel. */
    private static final Set<Command> READ_ONLY = EnumSet.of(
            Command.PING, Command.LOGOUT, Command.LIST_REPORTS,
            Command.LIST_REPORTS_BY_STATUS, Command.GET_REPORT,
            Command.FIND_DUPLICATE_CANDIDATES, Command.REPORT_SIMILARITY,
            Command.LIST_OPEN_INCIDENTS, Command.GET_INCIDENT_TIMELINE,
            Command.GET_INCIDENT, Command.LIST_INCIDENTS,
            Command.CALCULATE_SCORE, Command.RECOMMEND_PRIORITY,
            Command.GET_LATEST_ASSESSMENT, Command.RECOMMEND_DEPARTMENTS,
            Command.LIST_ASSIGNMENTS_FOR_INCIDENT, Command.LIST_ASSIGNMENTS,
            Command.GET_ESCALATIONS, Command.EVALUATE_ESCALATIONS,
            Command.EVALUATE_INCIDENT_ESCALATION, Command.LIST_AUDIT_EVENTS,
            Command.LIST_AUDIT_FOR_ENTITY, Command.LIST_WARNINGS,
            Command.LIST_PUBLIC_WARNINGS);

    private final EnumMap<Command, CommandHandler> handlers;
    private final SessionManager sessions;
    private final AuthorisationPolicy policy;
    private final ServerLog log;
    private final ReadWriteLock lock;

    /**
     * Creates the dispatcher.
     *
     * @param core     the core handlers
     * @param modules  the handlers from modules
     * @param sessions the session manager
     * @param policy   the authorisation policy
     * @param log      the server log
     */
    public RequestDispatcher(Map<Command, CommandHandler> core,
                             ModuleRegistry modules, SessionManager sessions,
                             AuthorisationPolicy policy, ServerLog log) {
        this.handlers = new EnumMap<>(Command.class);
        this.handlers.putAll(core);
        for (Map.Entry<Command, CommandHandler> entry
                : modules.getHandlers().entrySet()) {
            if (this.handlers.putIfAbsent(entry.getKey(), entry.getValue())
                    != null) {
                log.info("Module handler for " + entry.getKey().name()
                        + " ignored: the core server serves it.");
            }
        }
        this.sessions = sessions;
        this.policy = policy;
        this.log = log;
        this.lock = new ReentrantReadWriteLock(true);
    }

    /**
     * Tells whether a command only reads data.
     *
     * @param command the command
     * @return {@code true} for a read-only command
     */
    public static boolean isReadOnly(Command command) {
        return READ_ONLY.contains(command);
    }

    /**
     * Performs a request and returns the answer. Never throws.
     *
     * @param request the request, may be null if it could not be read
     * @return the response to send
     */
    public Response dispatch(Request request) {
        if (request == null || request.getCommand() == null) {
            return Response.failure(null, ErrorCode.VALIDATION_ERROR,
                    "The request could not be read.");
        }
        String requestId = request.getRequestId();
        Command command = request.getCommand();
        CommandHandler handler = this.handlers.get(command);
        if (handler == null) {
            return Response.failure(requestId, ErrorCode.ADAPTER_UNAVAILABLE,
                    "This feature is not installed on the server.");
        }
        User user = this.sessions.resolve(request.getSessionToken());
        if (command.isLoginRequired() && user == null) {
            return Response.failure(requestId, ErrorCode.PERMISSION_DENIED,
                    "Your session has ended. Please sign in again.",
                    "session");
        }
        if (!this.policy.isAllowed(user, command)) {
            return Response.failure(requestId, ErrorCode.PERMISSION_DENIED,
                    "You do not have permission to do this.");
        }
        return run(handler, request, user);
    }

    /**
     * Returns the commands this server can perform.
     *
     * @return a read-only set of commands
     */
    public Set<Command> getServedCommands() {
        return Collections.unmodifiableSet(this.handlers.keySet());
    }

    /**
     * Runs a handler under the right lock and converts any exception.
     *
     * @param handler the handler
     * @param request the request
     * @param user    the signed-in user, or null
     * @return the response
     */
    private Response run(CommandHandler handler, Request request, User user) {
        String requestId = request.getRequestId();
        Lock held;
        if (isReadOnly(request.getCommand())) {
            held = this.lock.readLock();
        } else {
            held = this.lock.writeLock();
        }
        held.lock();
        try {
            Response response = handler.handle(request, user);
            if (response == null) {
                throw new IllegalStateException("Handler returned null.");
            }
            return response;
        } catch (BadRequestException e) {
            return Response.failure(requestId, ErrorCode.VALIDATION_ERROR,
                    e.getMessage(), e.getField());
        } catch (IllegalArgumentException e) {
            return Response.failure(requestId, ErrorCode.VALIDATION_ERROR,
                    e.getMessage());
        } catch (Exception e) {
            this.log.error("Request " + requestId + " ("
                    + request.getCommand().name() + ") failed", e);
            return Response.failure(requestId, ErrorCode.ADAPTER_UNAVAILABLE,
                    "The server could not complete the request. "
                            + "Reference " + requestId + ".");
        } finally {
            held.unlock();
        }
    }

    /**
     * Describes the dispatcher.
     *
     * @return the number of commands served and the policy
     */
    @Override
    public String toString() {
        return "RequestDispatcher: " + this.handlers.size()
                + " command(s), " + this.policy;
    }
}
