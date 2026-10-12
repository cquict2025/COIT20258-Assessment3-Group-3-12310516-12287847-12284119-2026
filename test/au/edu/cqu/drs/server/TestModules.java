package au.edu.cqu.drs.server;

import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.Response;

import java.util.Map;

/**
 * Command modules used by {@link ModuleRegistryTest}; they are listed in
 * {@code test-modules.properties} and created by reflection.
 */
public final class TestModules {

    /**
     * Prevents instances; the modules are nested classes.
     */
    private TestModules() {
    }

    /**
     * A module with a no-argument constructor serving LIST_WARNINGS.
     */
    public static class Plain implements CommandModule {

        /**
         * Serves LIST_WARNINGS with the text "warnings".
         *
         * @return one handler
         */
        @Override
        public Map<Command, CommandHandler> handlers() {
            return Map.of(Command.LIST_WARNINGS, (request, user) ->
                    Response.success(request.getRequestId(), "warnings"));
        }

        /**
         * Describes the module.
         *
         * @return its name
         */
        @Override
        public String toString() {
            return "Plain test module";
        }
    }

    /**
     * A module whose constructor takes the context the registry is given.
     */
    public static class WithContext implements CommandModule {

        private final String context;

        /**
         * Creates the module.
         *
         * @param context the context passed by the registry
         */
        public WithContext(String context) {
            this.context = context;
        }

        /**
         * Serves ISSUE_WARNING, answering with the context it was given.
         *
         * @return one handler
         */
        @Override
        public Map<Command, CommandHandler> handlers() {
            return Map.of(Command.ISSUE_WARNING, (request, user) ->
                    Response.success(request.getRequestId(), this.context));
        }

        /**
         * Describes the module.
         *
         * @return its name and context
         */
        @Override
        public String toString() {
            return "WithContext test module: " + this.context;
        }
    }

    /**
     * A module offering a command another module already serves.
     */
    public static class Duplicate implements CommandModule {

        /**
         * Offers LIST_WARNINGS again, and ACK_WARNING.
         *
         * @return two handlers
         */
        @Override
        public Map<Command, CommandHandler> handlers() {
            return Map.of(
                    Command.LIST_WARNINGS, (request, user) ->
                            Response.success(request.getRequestId(), "dup"),
                    Command.ACK_WARNING, (request, user) ->
                            Response.success(request.getRequestId(), "ack"));
        }

        /**
         * Describes the module.
         *
         * @return its name
         */
        @Override
        public String toString() {
            return "Duplicate test module";
        }
    }

    /**
     * A module whose constructor fails.
     */
    public static class Broken implements CommandModule {

        /**
         * Always fails, as a module with a startup fault would.
         */
        public Broken() {
            throw new IllegalStateException("cannot start");
        }

        /**
         * Never reached.
         *
         * @return nothing
         */
        @Override
        public Map<Command, CommandHandler> handlers() {
            return Map.of();
        }

        /**
         * Describes the module.
         *
         * @return its name
         */
        @Override
        public String toString() {
            return "Broken test module";
        }
    }
}
