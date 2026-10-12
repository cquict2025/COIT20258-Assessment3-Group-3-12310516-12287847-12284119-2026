package au.edu.cqu.drs.server;

import au.edu.cqu.drs.protocol.Command;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Finds the {@link CommandModule}s listed in a properties file, creates them
 * and collects their command handlers.
 *
 * <p>Modules are created by reflection, so the server compiles and runs
 * whether or not a module's code is present. A module whose class is
 * missing, cannot be created, or offers a command that is already taken is
 * logged and skipped; nothing here stops the server from starting.</p>
 */
public class ModuleRegistry {

    /** Classpath location of the module list shipped with the server. */
    public static final String DEFAULT_RESOURCE =
            "/au/edu/cqu/drs/server/modules/modules.properties";

    private static final Logger LOGGER =
            Logger.getLogger(ModuleRegistry.class.getName());

    private final List<CommandModule> modules;
    private final EnumMap<Command, CommandHandler> handlers;
    private final List<String> problems;

    /**
     * Creates an empty registry.
     */
    public ModuleRegistry() {
        this.modules = new ArrayList<>();
        this.handlers = new EnumMap<>(Command.class);
        this.problems = new ArrayList<>();
    }

    /**
     * Creates a registry holding every module listed in a properties file.
     *
     * @param resource classpath location of the list, such as
     *                 {@link #DEFAULT_RESOURCE}
     * @param context  object offered to module constructors that take one
     *                 argument, or null
     * @return the registry; empty if the list cannot be read
     */
    public static ModuleRegistry load(String resource, Object context) {
        ModuleRegistry registry = new ModuleRegistry();
        Properties list = new Properties();
        try (InputStream in = ModuleRegistry.class
                .getResourceAsStream(resource)) {
            if (in == null) {
                registry.skip("Module list " + resource + " not found.");
                return registry;
            }
            list.load(in);
        } catch (IOException e) {
            registry.skip("Module list " + resource + " could not be read: "
                    + e.getMessage());
            return registry;
        }
        for (String key : new TreeSet<>(list.stringPropertyNames())) {
            String className = list.getProperty(key).trim();
            CommandModule module = registry.create(className, context);
            if (module != null) {
                registry.register(module);
            }
        }
        return registry;
    }

    /**
     * Adds a module's handlers. A command that already has a handler keeps
     * it, and the clash is recorded as a problem.
     *
     * @param module the module to add
     */
    public void register(CommandModule module) {
        Map<Command, CommandHandler> offered;
        try {
            offered = module.handlers();
        } catch (RuntimeException e) {
            skip("Module " + module.name() + " failed to list its handlers: "
                    + e);
            return;
        }
        if (offered == null) {
            skip("Module " + module.name() + " offered no handlers.");
            return;
        }
        for (Map.Entry<Command, CommandHandler> entry : offered.entrySet()) {
            Command command = entry.getKey();
            if (command == null || entry.getValue() == null) {
                skip("Module " + module.name()
                        + " offered an empty handler entry.");
            } else if (this.handlers.containsKey(command)) {
                skip("Module " + module.name() + " offered " + command.name()
                        + ", which already has a handler.");
            } else {
                this.handlers.put(command, entry.getValue());
            }
        }
        this.modules.add(module);
        LOGGER.info("Loaded module " + module.name() + " with "
                + offered.size() + " command(s).");
    }

    /**
     * Returns the handler for a command.
     *
     * @param command the command
     * @return the handler, or null if no module serves the command
     */
    public CommandHandler find(Command command) {
        return this.handlers.get(command);
    }

    /**
     * Returns every handler collected from the modules.
     *
     * @return a read-only map of handlers by command
     */
    public Map<Command, CommandHandler> getHandlers() {
        return Collections.unmodifiableMap(this.handlers);
    }

    /**
     * Returns the modules that were loaded.
     *
     * @return a read-only list, in load order
     */
    public List<CommandModule> getModules() {
        return Collections.unmodifiableList(this.modules);
    }

    /**
     * Returns a description of each module or handler that was skipped.
     *
     * @return a read-only list of messages
     */
    public List<String> getProblems() {
        return Collections.unmodifiableList(this.problems);
    }

    /**
     * Creates one module by its class name.
     *
     * @param className fully qualified class name
     * @param context   object offered to a one-argument constructor, or null
     * @return the module, or null if it was skipped
     */
    private CommandModule create(String className, Object context) {
        Class<?> type;
        try {
            type = Class.forName(className);
        } catch (ClassNotFoundException | LinkageError e) {
            skip("Module " + className + " is not installed; skipped.");
            return null;
        }
        if (!CommandModule.class.isAssignableFrom(type)) {
            skip(className + " is not a CommandModule; skipped.");
            return null;
        }
        try {
            return (CommandModule) construct(type, context);
        } catch (InvocationTargetException e) {
            skip("Module " + className + " failed to start: "
                    + e.getCause());
        } catch (ReflectiveOperationException | LinkageError e) {
            skip("Module " + className + " could not be created: " + e);
        }
        return null;
    }

    /**
     * Calls the constructor that takes the context if there is one,
     * otherwise the one with no arguments.
     *
     * @param type    the module class
     * @param context the context object, or null
     * @return the new instance
     * @throws ReflectiveOperationException if no usable constructor exists
     *                                      or the constructor fails
     */
    private Object construct(Class<?> type, Object context)
            throws ReflectiveOperationException {
        if (context != null) {
            for (Constructor<?> constructor : type.getConstructors()) {
                Class<?>[] parameters = constructor.getParameterTypes();
                if (parameters.length == 1
                        && parameters[0].isInstance(context)) {
                    return constructor.newInstance(context);
                }
            }
        }
        return type.getConstructor().newInstance();
    }

    /**
     * Records and logs a skipped module or handler.
     *
     * @param message what was skipped and why
     */
    private void skip(String message) {
        this.problems.add(message);
        LOGGER.log(Level.WARNING, message);
    }

    /**
     * Describes the registry.
     *
     * @return the loaded modules, the commands served and problems found
     */
    @Override
    public String toString() {
        List<String> names = new ArrayList<>();
        for (CommandModule module : this.modules) {
            names.add(module.name());
        }
        return String.format("ModuleRegistry: modules %s, commands %s, "
                + "%d problem(s)", names, this.handlers.keySet(),
                this.problems.size());
    }
}
