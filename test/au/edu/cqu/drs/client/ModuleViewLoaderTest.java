package au.edu.cqu.drs.client;

import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.service.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ModuleViewLoader} and {@link UnconnectedModuleContext}
 * that need no JavaFX window.
 */
class ModuleViewLoaderTest {

    private final ModuleContext context = new UnconnectedModuleContext(
            new User("USR-1", "coordinator", "Casey", UserRole.COORDINATOR));

    @Test
    @DisplayName("TC-MV-01 core views are found and module views are "
            + "reported missing until installed")
    void detectsInstalledViews() {
        assertTrue(ModuleViewLoader.isInstalled("main.fxml"));
        assertTrue(ModuleViewLoader.isInstalled("login.fxml"));
        // The warning module (Feature 1) is installed now, so a made-up
        // file name stands in for a module that is not.
        assertTrue(ModuleViewLoader.isInstalled("warning-main.fxml"));
        assertTrue(ModuleViewLoader.isInstalled("warning-board.fxml"));
        assertFalse(ModuleViewLoader.isInstalled("no-such-module.fxml"));
    }

    @Test
    @DisplayName("TC-MV-02 a module controller receives the context, or is "
            + "built with no arguments")
    void createsControllers() {
        Object withContext = ModuleViewLoader.createController(
                ContextController.class, this.context);
        Object plain = ModuleViewLoader.createController(
                PlainController.class, this.context);

        assertSame(this.context,
                ((ContextController) withContext).getContext());
        assertInstanceOf(PlainController.class, plain);
    }

    @Test
    @DisplayName("TC-MV-03 a controller without a usable constructor, or "
            + "one that fails, is reported")
    void reportsBadControllers() {
        IllegalStateException missing = assertThrows(
                IllegalStateException.class,
                () -> ModuleViewLoader.createController(
                        NoUsableConstructor.class, this.context));
        IllegalStateException failing = assertThrows(
                IllegalStateException.class,
                () -> ModuleViewLoader.createController(
                        FailingController.class, this.context));

        assertTrue(missing.getMessage().contains("public constructor"));
        assertTrue(failing.getMessage().contains("failed to start"));
    }

    @Test
    @DisplayName("TC-MV-04 without a connection every request is refused "
            + "as unavailable")
    void unconnectedContextRefuses() {
        Request request = this.context.newRequest(Command.LIST_WARNINGS);

        Response response = this.context.send(request);

        assertNull(request.getSessionToken());
        assertFalse(response.isSuccess());
        assertEquals(ErrorCode.ADAPTER_UNAVAILABLE, response.getErrorCode());
        assertEquals(request.getRequestId(), response.getRequestId());
        assertEquals("coordinator",
                this.context.getCurrentUser().getUsername());
        assertTrue(this.context.toString().contains("coordinator"));
    }

    /**
     * A controller taking the module context.
     */
    public static class ContextController {

        private final ModuleContext context;

        /**
         * Creates the controller.
         *
         * @param context the module context
         */
        public ContextController(ModuleContext context) {
            this.context = context;
        }

        /**
         * Returns the context received.
         *
         * @return the context
         */
        public ModuleContext getContext() {
            return this.context;
        }

        /**
         * Describes the controller.
         *
         * @return its name
         */
        @Override
        public String toString() {
            return "ContextController";
        }
    }

    /**
     * A controller with only a no-argument constructor.
     */
    public static class PlainController {

        /**
         * Describes the controller.
         *
         * @return its name
         */
        @Override
        public String toString() {
            return "PlainController";
        }
    }

    /**
     * A controller whose only constructor takes something else.
     */
    public static class NoUsableConstructor {

        /**
         * Creates the controller.
         *
         * @param value an unrelated value
         */
        public NoUsableConstructor(int value) {
        }

        /**
         * Describes the controller.
         *
         * @return its name
         */
        @Override
        public String toString() {
            return "NoUsableConstructor";
        }
    }

    /**
     * A controller whose constructor fails.
     */
    public static class FailingController {

        /**
         * Always fails.
         *
         * @param context the module context
         */
        public FailingController(ModuleContext context) {
            throw new IllegalStateException("cannot start");
        }

        /**
         * Describes the controller.
         *
         * @return its name
         */
        @Override
        public String toString() {
            return "FailingController";
        }
    }
}
