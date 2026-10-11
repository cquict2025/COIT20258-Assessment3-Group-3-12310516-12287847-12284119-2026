package au.edu.cqu.drs.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;

/**
 * Loads an FXML view and binds it to a controller instance that has already
 * been constructed with its services.
 *
 * <p>The FXML files deliberately omit the {@code fx:controller} attribute. If
 * the loader created the controllers itself they would have to find their own
 * services, which is how business logic leaks into the View layer. Constructing
 * each controller with the services it needs keeps the dependency direction
 * one-way: View to Controller to Service to Repository.</p>
 */
public final class ViewLoader {

    private ViewLoader() {
        // Utility class - not instantiated.
    }

    /**
     * Loads a view from the view package and attaches the supplied controller.
     *
     * @param fxmlFileName the file name, for example dashboard.fxml
     * @param controller   the already-constructed controller
     * @return the root node of the loaded view
     */
    public static Parent load(String fxmlFileName, Object controller) {
        FXMLLoader loader = new FXMLLoader(
                ViewLoader.class.getResource("/au/edu/cqu/drs/view/"
                        + fxmlFileName));
        loader.setController(controller);
        try {
            return loader.load();
        } catch (IOException e) {
            throw new IllegalStateException("Could not load the view "
                    + fxmlFileName,
                                            
                                            
                                            
                                            e);
        }
    }
}
