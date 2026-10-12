package au.edu.cqu.drs.server;

import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ModuleRegistry}: modules listed in a properties file are
 * created and their handlers collected, and anything wrong is skipped.
 */
class ModuleRegistryTest {

    private static final String TEST_LIST =
            "/au/edu/cqu/drs/server/test-modules.properties";

    @Test
    @DisplayName("TC-MR-01 a listed module is loaded and its handler runs")
    void loadsListedModule() throws Exception {
        ModuleRegistry registry = ModuleRegistry.load(TEST_LIST, "ctx");
        Request request = new Request(Command.LIST_WARNINGS, "t");

        Response response = registry.find(Command.LIST_WARNINGS)
                .handle(request, null);

        assertEquals("warnings", response.getValue());
        assertEquals(request.getRequestId(), response.getRequestId());
    }

    @Test
    @DisplayName("TC-MR-02 a module constructor taking the context receives "
            + "it")
    void passesContextToModule() throws Exception {
        ModuleRegistry registry = ModuleRegistry.load(TEST_LIST, "ctx");

        Response response = registry.find(Command.ISSUE_WARNING)
                .handle(new Request(Command.ISSUE_WARNING, "t"), null);

        assertEquals("ctx", response.getValue());
    }

    @Test
    @DisplayName("TC-MR-03 missing, broken and non-module classes are "
            + "skipped without stopping the others")
    void skipsBadModules() {
        ModuleRegistry registry = ModuleRegistry.load(TEST_LIST, "ctx");
        List<String> problems = registry.getProblems();

        assertEquals(3, registry.getModules().size());
        assertTrue(problems.stream().anyMatch(
                p -> p.contains("MissingModule is not installed")));
        assertTrue(problems.stream().anyMatch(
                p -> p.contains("java.lang.String is not a CommandModule")));
        assertTrue(problems.stream().anyMatch(
                p -> p.contains("failed to start")));
    }

    @Test
    @DisplayName("TC-MR-04 a command already served keeps its first handler")
    void firstHandlerWins() throws Exception {
        ModuleRegistry registry = ModuleRegistry.load(TEST_LIST, "ctx");

        Response list = registry.find(Command.LIST_WARNINGS)
                .handle(new Request(Command.LIST_WARNINGS, "t"), null);

        assertEquals("warnings", list.getValue());
        assertTrue(registry.getProblems().stream().anyMatch(
                p -> p.contains("LIST_WARNINGS, which already has")));
        assertEquals("ack", registry.find(Command.ACK_WARNING)
                .handle(new Request(Command.ACK_WARNING, "t"), null)
                .getValue());
    }

    @Test
    @DisplayName("TC-MR-05 the shipped list loads with no modules installed "
            + "and does not fail")
    void shippedListSkipsUninstalledModules() {
        ModuleRegistry registry =
                ModuleRegistry.load(ModuleRegistry.DEFAULT_RESOURCE, null);

        assertTrue(registry.getModules().isEmpty());
        assertEquals(2, registry.getProblems().size());
        assertNull(registry.find(Command.ISSUE_WARNING));
    }

    @Test
    @DisplayName("TC-MR-06 a missing module list gives an empty registry")
    void missingListGivesEmptyRegistry() {
        ModuleRegistry registry = ModuleRegistry.load("/no/such.properties",
                null);

        assertTrue(registry.getHandlers().isEmpty());
        assertEquals(1, registry.getProblems().size());
        assertTrue(registry.toString().contains("1 problem(s)"));
    }
}
