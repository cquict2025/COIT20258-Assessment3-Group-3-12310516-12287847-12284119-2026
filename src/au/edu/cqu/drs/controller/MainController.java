package au.edu.cqu.drs.controller;

import au.edu.cqu.drs.AppContext;
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

    private final AppContext context;
    private final Parent view;

    @FXML
    private Label currentUserLabel;
    @FXML
    private VBox contentArea;

    public MainController(AppContext context) {
        this.context = context;
        this.view = ViewLoader.load("main.fxml", this);
        this.currentUserLabel.setText(context.getCurrentUser().getFullName()
                + " - " + context.getCurrentUser().getRole().getDisplayName());
        showDashboard();
    }

    public Parent getView() {
        return this.view;
    }

    @FXML
    private void showDashboard() {
        display(new DashboardController(this.context).getView());
    }

    @FXML
    private void showReportForm() {
        display(new ReportFormController(this.context).getView());
    }

    @FXML
    private void showAssessment() {
        display(new AssessmentController(this.context).getView());
    }

    @FXML
    private void showCoordination() {
        display(new CoordinationController(this.context).getView());
    }

    @FXML
    private void showFieldUpdate() {
        display(new FieldUpdateController(this.context).getView());
    }

    @FXML
    private void handleSignOut() {
        this.context.setCurrentUser(null);
        LoginController loginController = new LoginController(this.context);
        Stage stage = (Stage) this.view.getScene().getWindow();
        Scene scene = new Scene(loginController.getView(), 520, 420);
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
