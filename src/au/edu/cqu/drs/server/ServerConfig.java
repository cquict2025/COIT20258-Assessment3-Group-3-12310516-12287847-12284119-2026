package au.edu.cqu.drs.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Settings for the DRS server, read from {@code drs-server.properties}.
 *
 * <p>The defaults ship inside the server package. A file of the same name in
 * the working folder, or one named on the command line, overrides any of
 * them.</p>
 */
public class ServerConfig {

    /** Classpath location of the default settings. */
    public static final String DEFAULTS_RESOURCE =
            "/au/edu/cqu/drs/server/drs-server.properties";

    /** File name looked for in the working folder. */
    public static final String LOCAL_FILE = "drs-server.properties";

    private final int port;
    private final int threads;
    private final Backend backend;
    private final String databaseFile;
    private final int clientTimeoutMinutes;
    private final int sessionIdleMinutes;

    /**
     * Creates settings from properties already loaded.
     *
     * @param properties the merged settings
     * @throws IllegalArgumentException if a value is missing or invalid
     */
    public ServerConfig(Properties properties) {
        this.port = number(properties, "port", 0, 65535);
        this.threads = number(properties, "threads", 1, 1000);
        this.backend = Backend.parse(properties.getProperty("repository"));
        this.databaseFile = text(properties, "database.file");
        this.clientTimeoutMinutes =
                number(properties, "client.timeout.minutes", 1, 1440);
        this.sessionIdleMinutes =
                number(properties, "session.idle.minutes", 1, 1440);
    }

    /**
     * Loads the defaults, then the local file if there is one.
     *
     * @param overrideFile a settings file to apply last, or null to use
     *                     {@code drs-server.properties} in the working folder
     *                     when it exists
     * @return the settings
     * @throws IOException if a settings file cannot be read
     */
    public static ServerConfig load(Path overrideFile) throws IOException {
        return new ServerConfig(loadProperties(overrideFile));
    }

    /**
     * Merges the defaults with the local file, so a caller can adjust a
     * value before creating the settings.
     *
     * @param overrideFile a settings file to apply last, or null to use
     *                     {@code drs-server.properties} in the working folder
     *                     when it exists
     * @return the merged properties
     * @throws IOException if a settings file cannot be read
     */
    public static Properties loadProperties(Path overrideFile)
            throws IOException {
        Properties properties = defaults();
        Path file = overrideFile;
        if (file == null) {
            file = Path.of(LOCAL_FILE);
        }
        if (Files.isRegularFile(file)) {
            try (Reader reader =
                    Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
        } else if (overrideFile != null) {
            throw new IOException("Settings file not found: " + overrideFile);
        }
        return properties;
    }

    /**
     * Reads the default settings shipped with the server.
     *
     * @return a copy of the defaults
     * @throws IOException if the defaults cannot be read
     */
    public static Properties defaults() throws IOException {
        Properties properties = new Properties();
        try (InputStream in =
                ServerConfig.class.getResourceAsStream(DEFAULTS_RESOURCE)) {
            if (in == null) {
                throw new IOException(DEFAULTS_RESOURCE + " is missing.");
            }
            properties.load(in);
        }
        return properties;
    }

    /**
     * Returns the TCP port; 0 asks the system for any free port.
     *
     * @return the port
     */
    public int getPort() {
        return this.port;
    }

    /**
     * Returns the number of worker threads.
     *
     * @return the most clients served at once
     */
    public int getThreads() {
        return this.threads;
    }

    /**
     * Returns where the data is stored.
     *
     * @return the storage backend
     */
    public Backend getBackend() {
        return this.backend;
    }

    /**
     * Returns the SQLite database file.
     *
     * @return the file name
     */
    public String getDatabaseFile() {
        return this.databaseFile;
    }

    /**
     * Returns how long a connection may stay silent.
     *
     * @return minutes
     */
    public int getClientTimeoutMinutes() {
        return this.clientTimeoutMinutes;
    }

    /**
     * Returns how long a session may stay idle.
     *
     * @return minutes
     */
    public int getSessionIdleMinutes() {
        return this.sessionIdleMinutes;
    }

    /**
     * Reads a whole number within limits.
     *
     * @param properties the settings
     * @param key        the setting name
     * @param min        the smallest value accepted
     * @param max        the largest value accepted
     * @return the value
     */
    private static int number(Properties properties, String key, int min,
                              int max) {
        String value = text(properties, key);
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                throw new IllegalArgumentException(key + " must be between "
                        + min + " and " + max + ".");
            }
            return parsed;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number.");
        }
    }

    /**
     * Reads a setting that must not be blank.
     *
     * @param properties the settings
     * @param key        the setting name
     * @return the trimmed value
     */
    private static String text(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is not set.");
        }
        return value.trim();
    }

    /**
     * Describes the settings.
     *
     * @return every setting
     */
    @Override
    public String toString() {
        return String.format("ServerConfig: port %d, %d threads, %s storage"
                + " (%s), connection timeout %d min, session idle %d min",
                this.port, this.threads, this.backend, this.databaseFile,
                this.clientTimeoutMinutes, this.sessionIdleMinutes);
    }

    /**
     * Where the server keeps its data.
     */
    public enum Backend {

        /** The SQLite adapters from DRS-Initial. */
        SQLITE,
        /** In-memory adapters; data is lost when the server stops. */
        MEMORY,
        /** Milan's MySQL adapters, once merged. */
        MYSQL;

        /**
         * Reads a backend name, ignoring case.
         *
         * @param name sqlite, memory or mysql
         * @return the backend
         * @throws IllegalArgumentException if the name is unknown
         */
        public static Backend parse(String name) {
            if (name == null) {
                throw new IllegalArgumentException("repository is not set.");
            }
            try {
                return valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("repository must be "
                        + "sqlite, memory or mysql, not '" + name + "'.");
            }
        }

        /**
         * Gives the name used in the settings file.
         *
         * @return the lower-case name
         */
        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
