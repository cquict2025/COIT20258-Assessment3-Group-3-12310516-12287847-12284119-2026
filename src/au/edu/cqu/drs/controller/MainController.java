package au.edu.cqu.drs.controller;

import au.edu.cqu.drs.client.BackgroundCall;
import au.edu.cqu.drs.client.ClientSession;
import au.edu.cqu.drs.client.ModuleViewLoader;
import au.edu.cqu.drs.client.RemoteServices;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.service.ServiceResult;
import au.edu.cqu.drs.util.ViewLoader;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * The application shell. It owns navigation only: each screen is a separate
 * controller with its own FXML view, and this class simply places the selected
 * view in the content area.
 */
public class MainController {

    /** View supplied by the warning module for staff. */
    public static final String WARNINGS_VIEW = "warning-main.fxml";

    /** Shown in place of a module screen that is not installed. */
    public static final String WARNINGS_MISSING =
            "Warnings module not installed";

    private final RemoteServices services;
    private final Parent view;

    @FXML
    private Label currentUserLabel;
    @FXML
    private VBox contentArea;

    /**
     * Creates the application shell for the signed-in user.
     *
     * @param services the services on the server
     */
    public MainController(RemoteServices services) {
        this.services = services;
        this.view = ViewLoader.load("main.fxml", this);
        User user = services.getSession().getCurrentUser();
        this.currentUserLabel.setText(user.getFullName() + " - "
                + user.getRole().getDisplayName());
        showDashboard();
    }

    public Parent getView() {
        return this.view;
    }

    @FXML
    private void showDashboard() {
        display(new DashboardController(this.services).getView());
    }

    @FXML
    private void showReportForm() {
        display(new ReportFormController(this.services).getView());
    }

    @FXML
    private void showAssessment() {
        display(new AssessmentController(this.services).getView());
    }

    @FXML
    private void showCoordination() {
        display(new CoordinationController(this.services).getView());
    }

    @FXML
    private void showFieldUpdate() {
        display(new FieldUpdateController(this.services).getView());
    }

    /**
     * Shows the warning module's staff screen, or a message if the module
     * is not installed.
     */
    @FXML
    private void showWarnings() {
        display(ModuleViewLoader.loadOrExplain(WARNINGS_VIEW,
                this.services.getSession(), WARNINGS_MISSING));
    }

    /**
     * Ends the session on the server and returns to the sign-in screen.
     */
    @FXML
    private void handleSignOut() {
        ClientSession session = this.services.getSession();
        BackgroundCall.run(this.view, () -> {
            session.logout();
            return ServiceResult.success(Boolean.TRUE);
        }, result -> showSignIn(session));
    }

    /**
     * Replaces the main screen with the sign-in screen.
     *
     * @param session the session, now signed out
     */
    private void showSignIn(ClientSession session) {
        LoginController loginController = new LoginController(session);
        Stage stage = (Stage) this.view.getScene().getWindow();
        Scene scene = new Scene(loginController.getView(), 560, 460);
        scene.getStylesheets().add(
                getClass().getResource("/au/edu/cqu/drs/view/app.css")
                        .toExternalForm());
        stage.setScene(scene);
    }

    private void display(Parent screen) {
        this.contentArea.getChildren().setAll(screen);
        VBox.setVgrow(screen, Priority.ALWAYS);
    }
}
