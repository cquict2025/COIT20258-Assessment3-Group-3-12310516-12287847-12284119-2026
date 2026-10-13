package au.edu.cqu.drs.server;

import au.edu.cqu.drs.server.event.EventBus;
import au.edu.cqu.drs.server.event.EscalationMonitor;
import au.edu.cqu.drs.server.event.EventChannel;

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
    private final EventBus events;
    private final RequestDispatcher dispatcher;
    private final EventChannel channel;
    private final EscalationMonitor monitor;
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
        this.events = new EventBus();
        this.dispatcher = new RequestDispatcher(
                new CoreCommands(this.context, this.sessions).handlers(),
                this.modules, this.sessions, this.policy, log, this.events,
                clock);
        int timeoutMillis = (int) Duration
                .ofMinutes(config.getClientTimeoutMinutes()).toMillis();
        this.server = new DrsServer(this.dispatcher, log, config.getPort(),
                config.getThreads(), timeoutMillis);
        this.channel = new EventChannel(this.events, this.sessions, log,
                clock);
        this.server.setEventChannel(this.channel);
        this.monitor = new EscalationMonitor(
                this.context.getEscalationService(), this.events, clock, log,
                this.dispatcher);
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
        this.monitor.start();
        this.housekeeping.scheduleAtFixedRate(this::purgeSessions,
                PURGE_MINUTES, PURGE_MINUTES, TimeUnit.MINUTES);
    }

    /**
     * Stops the server and closes the storage.
     */
    @Override
    public void close() {
        this.housekeeping.shutdownNow();
        this.monitor.close();
        this.channel.close();
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
     * Returns the bus that live events are published on.
     *
     * @return the event bus
     */
    public EventBus getEvents() {
        return this.events;
    }

    /**
     * Returns the channel that pushes live events to clients.
     *
     * @return the event channel
     */
    public EventChannel getEventChannel() {
        return this.channel;
    }

    /**
     * Returns the monitor that announces changes of escalation state.
     *
     * @return the escalation monitor
     */
    public EscalationMonitor getMonitor() {
        return this.monitor;
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
