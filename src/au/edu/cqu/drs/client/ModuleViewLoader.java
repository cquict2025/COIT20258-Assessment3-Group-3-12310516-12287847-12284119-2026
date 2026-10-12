package au.edu.cqu.drs.client;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Loads a screen supplied by a feature module, if the module is installed.
 *
 * <p>The core screens are loaded by {@code util.ViewLoader} with a
 * controller built beforehand. A module's FXML instead names its own
 * controller with {@code fx:controller}; this loader creates that controller
 * and passes it the {@link ModuleContext}. If the FXML file is missing, or
 * fails to load, a short message is shown in its place, so the core client
 * works with or without the module.</p>
 */
public final class ModuleViewLoader {

    /** Classpath folder holding every FXML view. */
    public static final String VIEW_FOLDER = "/au/edu/cqu/drs/view/";

    private static final Logger LOGGER =
            Logger.getLogger(ModuleViewLoader.class.getName());

    /**
     * Prevents instances; every method is static.
     */
    private ModuleViewLoader() {
    }

    /**
     * Tells whether a module's FXML file is on the classpath.
     *
     * @param fxmlFileName the file name, such as {@code warning-main.fxml}
     * @return {@code true} if the module's view is installed
     */
    public static boolean isInstalled(String fxmlFileName) {
        return locate(fxmlFileName) != null;
    }

    /**
     * Loads a module's view, creating its controller with the context.
     *
     * @param fxmlFileName the file name in the view folder
     * @param context      the context passed to the controller
     * @return the root of the view
     * @throws IOException if the file is missing or cannot be loaded
     */
    public static Parent load(String fxmlFileName, ModuleContext context)
            throws IOException {
        URL location = locate(fxmlFileName);
        if (location == null) {
            throw new IOException(fxmlFileName + " is not installed.");
        }
        FXMLLoader loader = new FXMLLoader(location);
        loader.setControllerFactory(type -> createController(type, context));
        return loader.load();
    }

    /**
     * Loads a module's view, or explains why it cannot be shown.
     *
     * @param fxmlFileName   the file name in the view folder
     * @param context        the context passed to the controller
     * @param missingMessage text shown when the module is not installed
     * @return the module's view, or a panel holding a message
     */
    public static Parent loadOrExplain(String fxmlFileName,
                                       ModuleContext context,
                                       String missingMessage) {
        if (!isInstalled(fxmlFileName)) {
            return messagePanel(missingMessage);
        }
        try {
            return load(fxmlFileName, context);
        } catch (IOException | RuntimeException e) {
            LOGGER.log(Level.WARNING, "Could not open " + fxmlFileName, e);
            return messagePanel("This module could not be opened. "
                    + "See the client log for details.");
        }
    }

    /**
     * Creates a controller, passing the context to a constructor that takes
     * a {@link ModuleContext}, or using the no-argument constructor.
     *
     * @param type    the controller class named in the FXML
     * @param context the context to pass
     * @return the controller
     * @throws IllegalStateException if neither constructor exists or the
     *                               constructor fails
     */
    public static Object createController(Class<?> type,
                                          ModuleContext context) {
        try {
            try {
                return type.getConstructor(ModuleContext.class)
                        .newInstance(context);
            } catch (NoSuchMethodException e) {
                return type.getConstructor().newInstance();
            }
        } catch (InvocationTargetException e) {
            throw new IllegalStateException("Controller " + type.getName()
                    + " failed to start.", e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Controller " + type.getName()
                    + " needs a public constructor taking a ModuleContext or "
                    + "no arguments.", e);
        }
    }

    /**
     * Finds a view file on the classpath.
     *
     * @param fxmlFileName the file name in the view folder
     * @return its location, or null if it is missing
     */
    private static URL locate(String fxmlFileName) {
        return ModuleViewLoader.class.getResource(VIEW_FOLDER + fxmlFileName);
    }

    /**
     * Builds a panel holding a single message.
     *
     * @param message the text to show
     * @return the panel
     */
    private static Parent messagePanel(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("hint-label");
        label.setWrapText(true);
        VBox panel = new VBox(label);
        panel.getStyleClass().add("content-area");
        return panel;
    }

    /**
     * Describes this helper class.
     *
     * @return a short description
     */
    @Override
    public String toString() {
        return "ModuleViewLoader: loads module views from " + VIEW_FOLDER;
    }
}
