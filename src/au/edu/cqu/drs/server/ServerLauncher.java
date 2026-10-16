package au.edu.cqu.drs.server;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;

/**
 * Entry point of the DRS server.
 *
 * <p>Usage: {@code ServerLauncher [--headless] [--config file] [--port n]}.
 * With {@code --headless} the log goes to the console and the server runs
 * until the process is stopped; otherwise a status window opens and closing
 * it stops the server. Settings come from {@code drs-server.properties}
 * (see {@link ServerConfig}).</p>
 */
public final class ServerLauncher {

    /** Exit status when the server cannot start. */
    private static final int START_FAILED = 1;

    /**
     * Prevents instances; this class only holds {@link #main}.
     */
    private ServerLauncher() {
    }

    /**
     * Starts the server.
     *
     * @param args command-line options
     */
    public static void main(String[] args) {
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println("Usage: ServerLauncher [--headless] "
                    + "[--config file] [--port n]");
            System.exit(START_FAILED);
            return;
        }
        ServerLog log = new ServerLog();
        log.addListener(System.out::println);
        ServerBootstrap bootstrap;
        try {
            ServerConfig config = loadConfig(options);
            log.info("Starting with " + config);
            bootstrap = new ServerBootstrap(config, log,
                    Clock.systemDefaultZone());
            bootstrap.start();
        } catch (IOException | RuntimeException e) {
            System.err.println("The DRS server could not start: "
                    + e.getMessage());
            System.exit(START_FAILED);
            return;
        }
        if (options.headless) {
            runHeadless(bootstrap);
        } else {
            ServerStatusWindow.open(bootstrap);
        }
    }

    /**
     * Reads the settings and applies a port given on the command line.
     *
     * @param options the parsed options
     * @return the settings
     * @throws IOException if a settings file cannot be read
     */
    private static ServerConfig loadConfig(Options options)
            throws IOException {
        Properties properties =
                ServerConfig.loadProperties(options.configFile);
        if (options.port != null) {
            properties.setProperty("port", options.port);
        }
        return new ServerConfig(properties);
    }

    /**
     * Runs without a window until the process is stopped, closing the
     * server from a shutdown hook.
     *
     * @param bootstrap the started server
     */
    private static void runHeadless(ServerBootstrap bootstrap) {
        CountDownLatch stopped = new CountDownLatch(1);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            bootstrap.close();
            stopped.countDown();
        }, "drs-shutdown"));
        bootstrap.getLog().info("Running headless; press Ctrl+C to stop.");
        try {
            stopped.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * The command-line options.
     */
    private static final class Options {

        private boolean headless;
        private Path configFile;
        private String port;

        /**
         * Reads the options.
         *
         * @param args the command-line arguments
         * @return the options
         * @throws IllegalArgumentException if an option is unknown or lacks
         *                                  its value
         */
        private static Options parse(String[] args) {
            Options options = new Options();
            for (int i = 0; i < args.length; i++) {
                String arg = args[i];
                if (arg.equals("--headless")) {
                    options.headless = true;
                } else if (arg.equals("--config") && i + 1 < args.length) {
                    i++;
                    options.configFile = Path.of(args[i]);
                } else if (arg.equals("--port") && i + 1 < args.length) {
                    i++;
                    options.port = args[i];
                } else {
                    throw new IllegalArgumentException("Unknown option: "
                            + arg);
                }
            }
            return options;
        }

        /**
         * Describes the options.
         *
         * @return the options given
         */
        @Override
        public String toString() {
            return "Options: headless " + this.headless + ", config "
                    + this.configFile + ", port " + this.port;
        }
    }

    /**
     * Describes the launcher.
     *
     * @return a short description
     */
    @Override
    public String toString() {
        return "ServerLauncher: starts the DRS server";
    }
}
