package au.edu.cqu.drs.server;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Assembles a complete server from its settings: the services, the session
 * manager, the modules, the authorisation policy, the dispatcher and the
 * {@link DrsServer} itself.
 *
 * <p>The launcher uses it to start the real server, and the tests use it to
 * start one on a free port with in-memory storage.</p>
 */
public class ServerBootstrap implements AutoCloseable {

    /** Minutes between sweeps that remove expired sessions. */
    private static final int PURGE_MINUTES = 1;

    private final ServerConfig config;
    private final ServerLog log;
    private final ServerContext context;
    private final SessionManager sessions;
    private final ModuleRegistry modules;
    private final AuthorisationPolicy policy;
    private final RequestDispatcher dispatcher;
    private final DrsServer server;
    private final ScheduledExecutorService housekeeping;

    /**
     * Builds every part of the server without starting it.
     *
     * @param config the settings
     * @param log    the server log
     * @param clock  the server clock
     */
    public ServerBootstrap(ServerConfig config, ServerLog log, Clock clock) {
        this.config = config;
        this.log = log;
        this.context = new ServerContext(config, clock);
        this.sessions = new SessionManager(clock,
                Duration.ofMinutes(config.getSessionIdleMinutes()));
        this.modules = ModuleRegistry.load(ModuleRegistry.DEFAULT_RESOURCE,
                this.context);
        for (String problem : this.modules.getProblems()) {
            log.info(problem);
        }
        this.policy = AuthorisationPolicy.createDefault();
        log.info("Authorisation: " + this.policy);
        this.dispatcher = new RequestDispatcher(
                new CoreCommands(this.context, this.sessions).handlers(),
                this.modules, this.sessions, this.policy, log);
        int timeoutMillis = (int) Duration
                .ofMinutes(config.getClientTimeoutMinutes()).toMillis();
        this.server = new DrsServer(this.dispatcher, log, config.getPort(),
                config.getThreads(), timeoutMillis);
        this.housekeeping = Executors.newSingleThreadScheduledExecutor(
                task -> {
                    Thread thread = new Thread(task, "drs-housekeeping");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    /**
     * Starts accepting clients.
     *
     * @throws IOException if the port cannot be opened
     */
    public void start() throws IOException {
        this.log.info("Storage: " + this.context);
        this.server.start();
        this.housekeeping.scheduleAtFixedRate(this::purgeSessions,
                PURGE_MINUTES, PURGE_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Stops the server and closes the storage.
     */
    @Override
    public void close() {
        this.housekeeping.shutdownNow();
        this.server.stop();
        this.context.close();
    }

    /**
     * Returns the settings.
     *
     * @return the settings
     */
    public ServerConfig getConfig() {
        return this.config;
    }

    /**
     * Returns the server log.
     *
     * @return the log
     */
    public ServerLog getLog() {
        return this.log;
    }

    /**
     * Returns the services.
     *
     * @return the context
     */
    public ServerContext getContext() {
        return this.context;
    }

    /**
     * Returns the session manager.
     *
     * @return the sessions
     */
    public SessionManager getSessions() {
        return this.sessions;
    }

    /**
     * Returns the dispatcher.
     *
     * @return the dispatcher
     */
    public RequestDispatcher getDispatcher() {
        return this.dispatcher;
    }

    /**
     * Returns the socket server.
     *
     * @return the server
     */
    public DrsServer getServer() {
        return this.server;
    }

    /**
     * Removes expired sessions and logs how many went.
     */
    private void purgeSessions() {
        int removed = this.sessions.purgeExpired();
        if (removed > 0) {
            this.log.info("Expired " + removed + " idle session(s).");
        }
    }

    /**
     * Describes the assembled server.
     *
     * @return the server and storage
     */
    @Override
    public String toString() {
        return "ServerBootstrap: " + this.server + ", " + this.context;
    }
}
