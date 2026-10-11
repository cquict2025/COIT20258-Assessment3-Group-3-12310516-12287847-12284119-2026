package au.edu.cqu.drs.controller;

import au.edu.cqu.drs.AppContext;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.service.ServiceResult;
import au.edu.cqu.drs.util.ViewLoader;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Handles the sign-in screen (UC-01). The controller collects the form values,
 * calls the authentication service and translates the typed result into a
 * message; it contains no authentication logic of its own.
 */
public class LoginController {

    private final AppContext context;
    private final Parent view;

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label messageLabel;

    public LoginController(AppContext context) {
        this.context = context;
        this.view = ViewLoader.load("login.fxml", this);
    }

    public Parent getView() {
        return this.view;
    }

    @FXML
    private void handleSignIn() {
        ServiceResult<User> result = this.context.getAuthenticationService()
                .login(this.usernameField.getText(),
                        this.passwordField.getText());

        if (result.isFailure()) {
            this.messageLabel.setText(result.getMessage());
            focusAffectedField(result.getAffectedField());
            return;
        }

        this.context.setCurrentUser(result.getValue());
        MainController mainController = new MainController(this.context);
        Stage stage = (Stage) this.view.getScene().getWindow();
        Scene scene = new Scene(mainController.getView(), 1180, 760);
        scene.getStylesheets().add(
                getClass().getResource("/au/edu/cqu/drs/view/app.css")
                        .toExternalForm());
        stage.setScene(scene);
    }

    /**
     * Opens the public report form without authentication, which is what UR-01
     * requires: a member of the public must be able to report a disaster
     * without
     * creating an account.
     */
    @FXML
    private void handlePublicReport() {
        ReportFormController controller = new
                ReportFormController(this.context);
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Report a disaster");
        Scene scene = new Scene(controller.getView(), 860, 720);
        scene.getStylesheets().add(
                getClass().getResource("/au/edu/cqu/drs/view/app.css")
                        .toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    private void focusAffectedField(String affectedField) {
        if ("username".equals(affectedField)) {
            this.usernameField.requestFocus();
        } else if ("password".equals(affectedField)) {
            this.passwordField.requestFocus();
        }
    }
}
