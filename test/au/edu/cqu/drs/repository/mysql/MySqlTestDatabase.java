package au.edu.cqu.drs.repository.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Opens the drs_test database for tests that need a real MySQL server.
 *
 * <p>The account is created once with:</p>
 * <pre>
 * CREATE USER 'drs_test'@'localhost' IDENTIFIED BY 'drs_test';
 * GRANT ALL PRIVILEGES ON drs_test.* TO 'drs_test'@'localhost';
 * </pre>
 * <p>The environment variables {@code DRS_TEST_MYSQL_HOST},
 * {@code DRS_TEST_MYSQL_PORT}, {@code DRS_TEST_MYSQL_USER} and
 * {@code DRS_TEST_MYSQL_PASSWORD} override those defaults.</p>
 */
public final class MySqlTestDatabase {

    private MySqlTestDatabase() {
        // Static helpers only.
    }

    /**
     * Drops every DRS table in drs_test and opens it again, so a test
     * starts on the schema in the code. Only drs_test is touched.
     *
     * @return the database
     * @throws IllegalStateException if MySQL or the account is missing;
     *                               the message says which
     */
    public static MySqlDatabase openFresh() {
        MySqlSettings settings = settings();
        try (Connection connection = DriverManager.getConnection(
                settings.databaseUrl(), settings.getUser(),
                settings.getPassword());
             Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            for (String table : MySqlSchema.TABLES) {
                statement.execute("DROP TABLE IF EXISTS " + table);
            }
        } catch (SQLException e) {
            // No drs_test yet, or no MySQL: MySqlDatabase reports which.
        }
        return new MySqlDatabase(settings);
    }

    /**
     * Returns the settings for drs_test.
     *
     * @return the settings
     */
    public static MySqlSettings settings() {
        Properties properties = new Properties();
        properties.setProperty("mysql.host",
                env("DRS_TEST_MYSQL_HOST", "localhost"));
        properties.setProperty("mysql.port",
                env("DRS_TEST_MYSQL_PORT", "3306"));
        properties.setProperty("mysql.database", "drs_test");
        properties.setProperty("mysql.user",
                env("DRS_TEST_MYSQL_USER", "drs_test"));
        properties.setProperty("mysql.password",
                env("DRS_TEST_MYSQL_PASSWORD", "drs_test"));
        properties.setProperty("mysql.pool.size", "4");
        properties.setProperty("mysql.allow.public.key.retrieval", "true");
        return MySqlSettings.fromProperties(properties);
    }

    /**
     * Describes the helper.
     *
     * @return the database it opens
     */
    @Override
    public String toString() {
        return "MySqlTestDatabase: drs_test";
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
