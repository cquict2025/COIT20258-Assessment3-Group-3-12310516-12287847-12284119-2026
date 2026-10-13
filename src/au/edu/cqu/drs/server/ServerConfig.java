package au.edu.cqu.drs.server;

import au.edu.cqu.drs.repository.mysql.MySqlSettings;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Settings for the DRS server, read from {@code drs-server.properties}.
 *
 * <p>The defaults ship inside the server package. A file of the same name in
 * the working folder, or one named on the command line, overrides any of
 * them.</p>
 *
 * <p>Passwords should not be kept in a file that is shared or submitted.
 * The environment variables {@value #MYSQL_PASSWORD_VARIABLE} and
 * {@value #KEYSTORE_PASSWORD_VARIABLE}, when set, override the
 * {@code mysql.password} and {@code keystore.password} settings, and
 * {@link #toString()} never shows either password.</p>
 */
public class ServerConfig {

    /** Classpath location of the default settings. */
    public static final String DEFAULTS_RESOURCE =
            "/au/edu/cqu/drs/server/drs-server.properties";

    /** File name looked for in the working folder. */
    public static final String LOCAL_FILE = "drs-server.properties";

    /** Environment variable holding the MySQL password. */
    public static final String MYSQL_PASSWORD_VARIABLE = "DRS_MYSQL_PASSWORD";

    /** Environment variable holding the keystore password. */
    public static final String KEYSTORE_PASSWORD_VARIABLE =
            "DRS_KEYSTORE_PASSWORD";

    private final int port;
    private final int threads;
    private final Backend backend;
    private final String databaseFile;
    private final int clientTimeoutMinutes;
    private final int sessionIdleMinutes;
    private final Properties mysqlProperties;
    private final String keystoreFile;
    private final String keystorePassword;

    /**
     * Creates settings from properties already loaded, taking passwords
     * from the environment where they are set.
     *
     * @param properties the merged settings
     * @throws IllegalArgumentException if a value is missing or invalid
     */
    public ServerConfig(Properties properties) {
        this(properties, System.getenv());
    }

    /**
     * Creates settings from properties and a given environment.
     *
     * @param properties  the merged settings
     * @param environment the environment variables to read passwords from
     * @throws IllegalArgumentException if a value is missing or invalid
     */
    public ServerConfig(Properties properties,
                        Map<String, String> environment) {
        this.port = number(properties, "port", 0, 65535);
        this.threads = number(properties, "threads", 1, 1000);
        this.backend = Backend.parse(properties.getProperty("repository"));
        this.databaseFile = text(properties, "database.file");
        this.clientTimeoutMinutes =
                number(properties, "client.timeout.minutes", 1, 1440);
        this.sessionIdleMinutes =
                number(properties, "session.idle.minutes", 1, 1440);
        this.mysqlProperties = new Properties();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("mysql.")) {
                this.mysqlProperties.setProperty(key,
                        properties.getProperty(key));
            }
        }
        String mysqlPassword = environment.get(MYSQL_PASSWORD_VARIABLE);
        if (mysqlPassword != null && !mysqlPassword.isEmpty()) {
            this.mysqlProperties.setProperty("mysql.password",
                    mysqlPassword);
        }
        this.keystoreFile = properties.getProperty("keystore.file",
                "drs-keystore.p12").trim();
        String keystore = environment.get(KEYSTORE_PASSWORD_VARIABLE);
        if (keystore == null || keystore.isEmpty()) {
            keystore = properties.getProperty("keystore.password", "");
        }
        this.keystorePassword = keystore;
        if (this.backend == Backend.MYSQL) {
            getMySqlSettings();
        }
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
    /**
     * Returns the MySQL connection settings.
     *
     * @return the settings from the {@code mysql.*} keys
     * @throws IllegalArgumentException if a MySQL setting is invalid
     */
    public MySqlSettings getMySqlSettings() {
        return MySqlSettings.fromProperties(this.mysqlProperties);
    }

    /**
     * Returns the keystore that holds the field encryption key.
     *
     * @return the keystore file, created on first start
     */
    public Path getKeystoreFile() {
        return Path.of(this.keystoreFile);
    }

    /**
     * Tells whether a keystore password is set, so personal details can
     * be encrypted.
     *
     * @return {@code true} if there is a password
     */
    public boolean hasKeystorePassword() {
        return !this.keystorePassword.isEmpty();
    }

    /**
     * Returns the keystore password.
     *
     * @return a new copy of the password, empty if none is set
     */
    public char[] getKeystorePassword() {
        return this.keystorePassword.toCharArray();
    }

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
     * Describes the settings, without any password.
     *
     * @return every setting
     */
    @Override
    public String toString() {
        String storage = this.databaseFile;
        if (this.backend == Backend.MYSQL) {
            storage = getMySqlSettings().toString();
        }
        return String.format("ServerConfig: port %d, %d threads, %s storage"
                + " (%s), connection timeout %d min, session idle %d min",
                this.port, this.threads, this.backend, storage,
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
