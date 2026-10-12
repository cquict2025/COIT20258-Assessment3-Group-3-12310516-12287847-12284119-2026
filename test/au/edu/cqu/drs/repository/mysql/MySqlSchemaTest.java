package au.edu.cqu.drs.repository.mysql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks of the MySQL schema and settings that need no MySQL server, so they
 * run on every build.
 */
class MySqlSchemaTest {

    @Test
    @DisplayName("TC-MY-01 sql/schema.sql matches the schema the server "
            + "creates")
    void schemaScriptMatchesCode() throws IOException {
        Path script = Path.of("sql", "schema.sql");
        assertTrue(Files.exists(script), "Run the tests from the project "
                + "folder; sql/schema.sql was not found in "
                + Path.of("").toAbsolutePath());

        String committed = Files.readString(script, StandardCharsets.UTF_8);

        assertEquals(MySqlSchema.script(MySqlSettings.DEFAULT_DATABASE),
                committed, "sql/schema.sql is out of date. Regenerate it "
                        + "with: java -cp build/classes "
                        + "au.edu.cqu.drs.repository.mysql.MySqlSchema "
                        + "> sql/schema.sql");
    }

    @Test
    @DisplayName("TC-MY-02 every table has an AUTO_INCREMENT id (spec 2.4)")
    void everyTableHasAnId() {
        assertEquals(MySqlSchema.TABLES.size(),
                MySqlSchema.statements().size());
        for (String statement : MySqlSchema.statements()) {
            assertTrue(statement.contains(
                    "id BIGINT NOT NULL AUTO_INCREMENT"), statement);
            assertTrue(statement.contains("PRIMARY KEY (id)"), statement);
        }
    }

    @Test
    @DisplayName("TC-MY-03 codes are formatted as DRS-Initial formatted them")
    void codesKeepTheirFormat() {
        assertEquals("INC-0007", MySqlSupport.CodeKey.INCIDENT.format(7));
        assertEquals("USR-012", MySqlSupport.CodeKey.USER.format(12));
        assertEquals("AUD-00003",
                MySqlSupport.CodeKey.AUDIT_EVENT.format(3));
    }

    @Test
    @DisplayName("TC-MY-04 settings default sensibly and hide the password")
    void settingsDefaultsAndPasswordHidden() {
        Properties properties = new Properties();
        properties.setProperty("mysql.password", "s3cret-value");

        MySqlSettings settings = MySqlSettings.fromProperties(properties);

        assertEquals("drs", settings.getDatabase());
        assertEquals(MySqlSettings.DEFAULT_POOL_SIZE, settings.getPoolSize());
        assertTrue(settings.databaseUrl()
                .startsWith("jdbc:mysql://localhost:3306/drs?"));
        assertFalse(settings.toString().contains("s3cret-value"));
    }

    @Test
    @DisplayName("TC-MY-05 a database name that could inject SQL is "
            + "refused")
    void unsafeDatabaseNameIsRefused() {
        Properties properties = new Properties();
        properties.setProperty("mysql.database", "drs; DROP DATABASE drs");

        assertThrows(IllegalArgumentException.class,
                () -> MySqlSettings.fromProperties(properties));
    }

    @Test
    @DisplayName("TC-MY-06 a non-numeric port gives a clear message")
    void badPortIsExplained() {
        Properties properties = new Properties();
        properties.setProperty("mysql.port", "three");

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> MySqlSettings.fromProperties(properties));

        assertTrue(error.getMessage().contains("mysql.port"));
    }
}
