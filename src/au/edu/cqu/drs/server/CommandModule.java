package au.edu.cqu.drs.server;

import au.edu.cqu.drs.protocol.Command;

import java.util.Map;

/**
 * A feature that adds commands to the server without changing it.
 *
 * <p>A module is listed in {@code server/modules/modules.properties} and
 * created by the {@link ModuleRegistry} through reflection. The class needs a
 * public constructor that takes no arguments, or one that takes the server
 * context object the registry is given. It then returns a handler for each
 * command it serves.</p>
 *
 * <p>See {@code docs/design/hooks.md} for how to plug a module in.</p>
 */
public interface CommandModule {

    /**
     * Returns the handler for each command this module serves.
     *
     * @return the handlers, keyed by command
     */
    Map<Command, CommandHandler> handlers();

    /**
     * Names the module in logs.
     *
     * @return the simple class name unless a module overrides it
     */
    default String name() {
        return getClass().getSimpleName();
    }
}
