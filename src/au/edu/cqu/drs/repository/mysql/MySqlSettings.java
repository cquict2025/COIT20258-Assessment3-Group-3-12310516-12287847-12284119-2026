package au.edu.cqu.drs.repository.mysql;

import java.util.Properties;
import java.util.regex.Pattern;

/**
 * Connection settings for the MySQL tier.
 *
 * <p>The server reads them from {@code drs-server.properties} with
 * {@link #fromProperties(Properties)}. Every key is optional:</p>
 *
 * <pre>
 * mysql.host=localhost
 * mysql.port=3306
 * mysql.database=drs
 * mysql.user=drs
 * mysql.password=
 * mysql.pool.size=8
 * mysql.allow.public.key.retrieval=false
 * </pre>
 */
public final class MySqlSettings {

    /** Database name used when none is configured. */
    public static final String DEFAULT_DATABASE = "drs";

    /** Connections kept open when no pool size is configured. */
    public static final int DEFAULT_POOL_SIZE = 8;

    /**
     * Database names are written into SQL, where a parameter cannot be used,
     * so only plain identifiers are accepted.
     */
    private static final Pattern IDENTIFIER =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,63}");

    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;
    private final int poolSize;
    private final boolean allowPublicKeyRetrieval;

    /**
     * Creates settings.
     *
     * @param host                    the MySQL server host
     * @param port                    the MySQL server port
     * @param database                the database name
     * @param user                    the account name
     * @param password                the account password, may be empty
     * @param poolSize                how many connections to keep open
     * @param allowPublicKeyRetrieval whether the driver may fetch the
     *                                server's RSA key when TLS is not in use
     * @throws IllegalArgumentException if a value is invalid
     */
    public MySqlSettings(String host, int port, String database, String user,
            String password, int poolSize, boolean allowPublicKeyRetrieval) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("mysql.host is empty.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("mysql.port must be between "
                    + "1 and 65535.");
        }
        if (database == null || !IDENTIFIER.matcher(database).matches()) {
            throw new IllegalArgumentException("mysql.database must be "
                    + "letters, digits and underscores only.");
        }
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("mysql.user is empty.");
        }
        if (poolSize < 1 || poolSize > 100) {
            throw new IllegalArgumentException("mysql.pool.size must be "
                    + "between 1 and 100.");
        }
        this.host = host.trim();
        this.port = port;
        this.database = database;
        this.user = user.trim();
        this.password = password == null ? "" : password;
        this.poolSize = poolSize;
        this.allowPublicKeyRetrieval = allowPublicKeyRetrieval;
    }

    /**
     * Reads the {@code mysql.*} keys, using defaults for missing ones.
     *
     * @param properties the server settings
     * @return the MySQL settings
     * @throws IllegalArgumentException if a value is invalid
     */
    public static MySqlSettings fromProperties(Properties properties) {
        return new MySqlSettings(
                properties.getProperty("mysql.host", "localhost"),
                number(properties, "mysql.port", 3306),
                properties.getProperty("mysql.database", DEFAULT_DATABASE)
                        .trim(),
                properties.getProperty("mysql.user", "drs"),
                properties.getProperty("mysql.password", ""),
                number(properties, "mysql.pool.size", DEFAULT_POOL_SIZE),
                Boolean.parseBoolean(properties.getProperty(
                        "mysql.allow.public.key.retrieval", "false").trim()));
    }

    /**
     * Returns the JDBC URL of the server, without a database, used to create
     * the database before it exists.
     *
     * @return the server URL
     */
    public String serverUrl() {
        return "jdbc:mysql://" + this.host + ":" + this.port + "/"
                + options();
    }

    /**
     * Returns the JDBC URL of the database.
     *
     * @return the database URL
     */
    public String databaseUrl() {
        return "jdbc:mysql://" + this.host + ":" + this.port + "/"
                + this.database + options();
    }

    /**
     * Returns the database name.
     *
     * @return the name, a plain identifier
     */
    public String getDatabase() {
        return this.database;
    }

    /**
     * Returns the account name.
     *
     * @return the user
     */
    public String getUser() {
        return this.user;
    }

    /**
     * Returns the account password.
     *
     * @return the password, possibly empty
     */
    public String getPassword() {
        return this.password;
    }

    /**
     * Returns how many connections the pool keeps.
     *
     * @return the pool size
     */
    public int getPoolSize() {
        return this.poolSize;
    }

    /**
     * Describes the settings without the password.
     *
     * @return a description safe to log
     */
    @Override
    public String toString() {
        return "MySqlSettings: " + this.user + "@" + this.host + ":"
                + this.port + "/" + this.database + ", pool of "
                + this.poolSize;
    }

    private String options() {
        return "?sslMode=PREFERRED&characterEncoding=UTF-8"
                + "&allowPublicKeyRetrieval=" + this.allowPublicKeyRetrieval;
    }

    private static int number(Properties properties, String key,
            int fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a whole "
                    + "number, not '" + value + "'.");
        }
    }
}
