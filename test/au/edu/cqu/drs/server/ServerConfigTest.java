package au.edu.cqu.drs.server;

import au.edu.cqu.drs.repository.mysql.MySqlSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the MySQL and keystore settings in {@link ServerConfig}.
 */
class ServerConfigTest {

    @Test
    @DisplayName("TC-CF-01 the MySQL settings come from the mysql.* keys")
    void readsMySqlSettings() throws Exception {
        Properties properties = ServerConfig.defaults();
        properties.setProperty("mysql.host", "db.example");
        properties.setProperty("mysql.port", "3307");
        properties.setProperty("mysql.database", "drs_demo");

        ServerConfig config = new ServerConfig(properties, Map.of());
        MySqlSettings settings = config.getMySqlSettings();

        assertEquals(ServerConfig.Backend.MYSQL, config.getBackend());
        assertEquals("drs_demo", settings.getDatabase());
        assertEquals("drs", settings.getUser());
        assertTrue(settings.databaseUrl()
                .startsWith("jdbc:mysql://db.example:3307/drs_demo"));
        assertEquals(Path.of("drs-keystore.p12"), config.getKeystoreFile());
        assertFalse(config.hasKeystorePassword());
    }

    @Test
    @DisplayName("TC-CF-02 passwords in the environment override the file "
            + "and never appear in the description")
    void environmentPasswordsWinAndStayHidden() throws Exception {
        Properties properties = ServerConfig.defaults();
        properties.setProperty("mysql.password", "file-password");
        properties.setProperty("keystore.password", "file-keystore");
        Map<String, String> environment = Map.of(
                ServerConfig.MYSQL_PASSWORD_VARIABLE, "env-password",
                ServerConfig.KEYSTORE_PASSWORD_VARIABLE, "env-keystore");

        ServerConfig fromFile = new ServerConfig(properties, Map.of());
        ServerConfig fromEnvironment =
                new ServerConfig(properties, environment);

        assertEquals("file-password",
                fromFile.getMySqlSettings().getPassword());
        assertArrayEquals("file-keystore".toCharArray(),
                fromFile.getKeystorePassword());
        assertEquals("env-password",
                fromEnvironment.getMySqlSettings().getPassword());
        assertArrayEquals("env-keystore".toCharArray(),
                fromEnvironment.getKeystorePassword());
        String described = fromEnvironment.toString();
        assertFalse(described.contains("password"), described);
        assertFalse(described.contains("keystore"), described);
        assertTrue(described.contains("mysql storage"), described);
    }

    @Test
    @DisplayName("TC-CF-03 an invalid MySQL setting is refused only when "
            + "MySQL is the storage")
    void invalidMySqlSettingNeedsMySql() throws Exception {
        Properties mysql = ServerConfig.defaults();
        mysql.setProperty("mysql.database", "drs; DROP DATABASE drs");
        Properties memory = ServerConfig.defaults();
        memory.setProperty("mysql.database", "drs; DROP DATABASE drs");
        memory.setProperty("repository", "memory");

        assertThrows(IllegalArgumentException.class,
                () -> new ServerConfig(mysql, Map.of()));
        assertEquals(ServerConfig.Backend.MEMORY,
                new ServerConfig(memory, Map.of()).getBackend());
    }
}
