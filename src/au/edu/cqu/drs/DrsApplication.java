package au.edu.cqu.drs;

import au.edu.cqu.drs.controller.LoginController;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Entry point of the DRS-Initial prototype (COIT20258 Assessment 2).
 *
 * <p>The class does nothing except create the application context and show the
 * first view. Keeping it this thin is deliberate: the View is FXML, the
 * Controllers handle events, and every rule lives in the service layer.</p>
 */
public class DrsApplication extends Application {

    /** Name of the SQLite file created beside the project on first run. */
    public static final String DATABASE_FILE = "drs.db";

    private AppContext context;

    @Override
    public void start(Stage stage) {
        this.context = new AppContext(DATABASE_FILE);

        LoginController loginController = new LoginController(this.context);
        Scene scene = new Scene(loginController.getView(), 520, 420);
        scene.getStylesheets().add(
                getClass().getResource("/au/edu/cqu/drs/view/app.css")
                        .toExternalForm());

        stage.setTitle("Disaster Response System (DRS-Initial)");
        stage.setScene(scene);
        stage.show();
    }

    @Override
    public void stop() {
        if (this.context != null) {
            this.context.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
