package au.edu.cqu.drs;

import au.edu.cqu.drs.client.ClientSession;
import au.edu.cqu.drs.client.ServerConnection;
import au.edu.cqu.drs.controller.LoginController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.List;

/**
 * The DRS-Enhanced JavaFX client application.
 *
 * <p>The class does nothing except open the session with the server and show
 * the first view. Keeping it this thin is deliberate: the View is FXML, the
 * Controllers handle events, and every rule lives in the services on the
 * server. Start it through {@code client.ClientLauncher}.</p>
 */
public class DrsApplication extends Application {

    private ClientSession session;

    /**
     * Opens the session and shows the sign-in screen.
     *
     * @param stage the primary stage
     */
    @Override
    public void start(Stage stage) {
        List<String> args = getParameters().getRaw();
        String host = ServerConnection.DEFAULT_HOST;
        int port = ServerConnection.DEFAULT_PORT;
        if (!args.isEmpty()) {
            host = args.get(0);
        }
        if (args.size() > 1) {
            port = Integer.parseInt(args.get(1));
        }
        this.session = new ClientSession(new ServerConnection(host, port));

        LoginController loginController = new LoginController(this.session);
        Scene scene = new Scene(loginController.getView(), 560, 460);
        scene.getStylesheets().add(
                getClass().getResource("/au/edu/cqu/drs/view/app.css")
                        .toExternalForm());

        stage.setTitle("Disaster Response System (DRS-Enhanced)");
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Signs out and closes the connection when the window closes.
     */
    @Override
    public void stop() {
        if (this.session != null) {
            this.session.logout();
            this.session.close();
        }
    }

    /**
     * Describes the application.
     *
     * @return the session
     */
    @Override
    public String toString() {
        return "DrsApplication: " + this.session;
    }
}
