package au.edu.cqu.drs.controller;

import au.edu.cqu.drs.AppContext;
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
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Handles the sign-in screen (UC-01). The controller collects the form values,
 * asks the server to authenticate them and translates the typed result into a
 * message; it contains no authentication logic of its own.
 */
public class LoginController {

    /** View supplied by the warning module for the public. */
    public static final String WARNING_BOARD_VIEW = "warning-board.fxml";

    private final AppContext context;
    private final ClientSession session;
    private final Parent view;

    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private TextField hostField;
    @FXML
    private TextField portField;
    @FXML
    private Label messageLabel;

    /**
     * Creates the sign-in screen.
     *
     * @param context the in-process context still used by screens that have
     *                not moved to the server
     * @param session the session with the server
     */
    public LoginController(AppContext context, ClientSession session) {
        this.context = context;
        this.session = session;
        this.view = ViewLoader.load("login.fxml", this);
        this.hostField.setText(session.getConnection().getHost());
        this.portField.setText(
                String.valueOf(session.getConnection().getPort()));
    }

    /**
     * Returns the root of the view.
     *
     * @return the view
     */
    public Parent getView() {
        return this.view;
    }

    /**
     * Signs in through the server in the background, then opens the main
     * screen.
     */
    @FXML
    private void handleSignIn() {
        if (!applyServerAddress()) {
            return;
        }
        String username = this.usernameField.getText();
        String password = this.passwordField.getText();
        this.messageLabel.setText("Signing in...");
        BackgroundCall.run(this.view,
                () -> this.session.login(username, password),
                this::signedIn);
    }

    /**
     * Opens the main screen after a successful sign-in, or shows why it
     * failed.
     *
     * @param result the sign-in result
     */
    private void signedIn(ServiceResult<User> result) {
        if (result.isFailure()) {
            this.messageLabel.setText(result.getMessage());
            focusAffectedField(result.getAffectedField());
            return;
        }
        this.context.setCurrentUser(result.getValue());
        MainController mainController = new MainController(this.context,
                new RemoteServices(this.session));
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
     * without creating an account.
     */
    @FXML
    private void handlePublicReport() {
        if (!applyServerAddress()) {
            return;
        }
        ReportFormController controller = new ReportFormController(
                new RemoteServices(this.session));
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

    /**
     * Opens the public warnings board without signing in, or a message if
     * the warning module is not installed.
     */
    @FXML
    private void handlePublicWarnings() {
        if (!applyServerAddress()) {
            return;
        }
        Stage stage = new Stage();
        stage.setTitle("Public warnings");
        Scene scene = new Scene(ModuleViewLoader.loadOrExplain(
                WARNING_BOARD_VIEW, this.session,
                MainController.WARNINGS_MISSING), 860, 600);
        scene.getStylesheets().add(
                getClass().getResource("/au/edu/cqu/drs/view/app.css")
                        .toExternalForm());
        stage.setScene(scene);
        stage.show();
    }

    /**
     * Points the session at the server named on the form.
     *
     * @return {@code false} if the port is not a valid number
     */
    private boolean applyServerAddress() {
        String host = this.hostField.getText().trim();
        int port;
        try {
            port = Integer.parseInt(this.portField.getText().trim());
        } catch (NumberFormatException e) {
            port = -1;
        }
        if (host.isEmpty() || port < 1 || port > 65535) {
            this.messageLabel.setText("Enter the server host and a port "
                    + "between 1 and 65535.");
            this.hostField.requestFocus();
            return false;
        }
        this.session.connectTo(host, port);
        return true;
    }

    private void focusAffectedField(String affectedField) {
        if ("username".equals(affectedField)) {
            this.usernameField.requestFocus();
        } else if ("password".equals(affectedField)) {
            this.passwordField.requestFocus();
        }
    }
}
