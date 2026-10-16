package au.edu.cqu.drs.controller.warning;

import au.edu.cqu.drs.client.BackgroundCall;
import au.edu.cqu.drs.client.ModuleContext;
import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.warning.Warning;
import au.edu.cqu.drs.model.warning.WarningAcknowledgement;
import au.edu.cqu.drs.model.warning.WarningKeys;
import au.edu.cqu.drs.model.warning.WarningLevel;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.service.ServiceResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

/**
 * The department liaison's screen (warning-ack.fxml): the warnings in
 * force, whether the liaison's department has acknowledged each one, and a
 * button to acknowledge. Opened in place of the staff warnings screen when
 * a liaison signs in.
 */
public class WarningAckController {

    private final ModuleContext context;
    private final WarningCalls calls;

    @FXML
    private VBox root;
    @FXML
    private Label departmentLabel;
    @FXML
    private TableView<Warning> warningTable;
    @FXML
    private TableColumn<Warning, String> codeColumn;
    @FXML
    private TableColumn<Warning, WarningLevel> levelColumn;
    @FXML
    private TableColumn<Warning, String> areaColumn;
    @FXML
    private TableColumn<Warning, String> expiresColumn;
    @FXML
    private TableColumn<Warning, String> ackColumn;
    @FXML
    private Label detailLabel;
    @FXML
    private Button acknowledgeButton;
    @FXML
    private Label messageLabel;

    /**
     * Creates the controller.
     *
     * @param context the signed-in liaison's connection
     */
    public WarningAckController(ModuleContext context) {
        this.context = context;
        this.calls = new WarningCalls(context);
    }

    /**
     * Sets up the table and loads the warnings.
     */
    @FXML
    private void initialize() {
        Department department = department();
        this.departmentLabel.setText(department == null
                ? "Your account has no department, so you cannot "
                        + "acknowledge warnings. Ask an administrator to set "
                        + "one."
                : "Acknowledge each warning once you have passed it on "
                        + "within " + department.getDisplayName() + ". The "
                        + "coordinator sees which departments have not.");
        this.acknowledgeButton.setDisable(department == null);
        this.codeColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getWarningId()));
        this.levelColumn.setCellValueFactory(cell ->
                new SimpleObjectProperty<>(cell.getValue().getLevel()));
        this.levelColumn.setCellFactory(column -> new LevelCell());
        this.areaColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getAffectedArea()));
        this.expiresColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getExpiresAt()
                        .format(WarningCalls.TIME_FORMAT)));
        this.ackColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(ourAcknowledgement(cell.getValue())
                        .map(ack -> "Yes, " + ack.getAcknowledgedAt()
                                .format(WarningCalls.SHORT_TIME))
                        .orElse("Not yet")));
        this.warningTable.setPlaceholder(
                new Label("No warnings are in force."));
        this.warningTable.getSelectionModel().selectedItemProperty()
                .addListener((observed, before, now) -> describe(now));
        handleRefresh();
    }

    /**
     * Reloads the warnings in force.
     */
    @FXML
    private void handleRefresh() {
        BackgroundCall.run(this.root,
                () -> this.calls.warnings(Command.LIST_WARNINGS),
                this::show);
    }

    /**
     * Acknowledges the selected warning for the liaison's department.
     */
    @FXML
    private void handleAcknowledge() {
        Warning selected = this.warningTable.getSelectionModel()
                .getSelectedItem();
        if (selected == null) {
            showError("Select a warning in the list first.");
            return;
        }
        if (ourAcknowledgement(selected).isPresent()) {
            showError("Your department has already acknowledged "
                    + selected.getWarningId() + ".");
            return;
        }
        BackgroundCall.run(this.root,
                () -> this.calls.send(Command.ACK_WARNING,
                        WarningAcknowledgement.class,
                        WarningKeys.WARNING_ID, selected.getWarningId()),
                this::acknowledged);
    }

    /**
     * Shows the warnings that are in force now, or the error if the list could
     * not be loaded.
     *
     * @param result the server's answer to the list request
     */
    private void show(ServiceResult<List<Warning>> result) {
        if (result.isFailure()) {
            showError(result.getMessage());
            return;
        }
        List<Warning> inForce = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Warning warning : result.getValue()) {
            if (warning.isInForceAt(now)) {
                inForce.add(warning);
            }
        }
        this.warningTable.getItems().setAll(inForce);
    }

    /**
     * Reports a successful acknowledgement and reloads the list, or shows the
     * error if the server refused it.
     *
     * @param result the server's answer to the acknowledgement
     */
    private void acknowledged(ServiceResult<WarningAcknowledgement> result) {
        if (result.isFailure()) {
            showError(result.getMessage());
            return;
        }
        this.messageLabel.getStyleClass().setAll("success-label");
        this.messageLabel.setText(result.getValue().getDepartment()
                .getDisplayName() + " acknowledged "
                + result.getValue().getWarningId() + ".");
        handleRefresh();
    }

    /**
     * Shows the full message of the selected warning, with its recommended
     * action and evacuation centre when it has them.
     *
     * @param warning the selected warning, or null when nothing is selected
     */
    private void describe(Warning warning) {
        if (warning == null) {
            this.detailLabel.setText("Select a warning to read it in full.");
            return;
        }
        String action = warning.getRecommendedAction() == null
                || warning.getRecommendedAction().isBlank() ? ""
                : " What to do: " + warning.getRecommendedAction();
        String centre = warning.getEvacuationCentre() == null ? ""
                : " Evacuation centre: " + warning.getEvacuationCentre() + ".";
        this.detailLabel.setText(warning.getMessage() + action + centre);
    }

    /**
     * Finds the acknowledgement the liaison's department has given for a
     * warning.
     *
     * @param warning the warning to look in
     * @return the department's acknowledgement, or empty if it has not
     *         acknowledged the warning yet
     */
    private Optional<WarningAcknowledgement> ourAcknowledgement(
            Warning warning) {
        Department department = department();
        return warning.getAcknowledgements().stream()
                .filter(ack -> ack.getDepartment() == department)
                .findFirst();
    }

    /**
     * Gives the department of the signed-in liaison.
     *
     * @return the department, or null if no one is signed in or the account
     *         has no department
     */
    private Department department() {
        User user = this.context.getCurrentUser();
        return user == null ? null : user.getDepartment();
    }

    /**
     * Shows an error message under the table.
     *
     * @param message the message to show
     */
    private void showError(String message) {
        this.messageLabel.getStyleClass().setAll("error-label");
        this.messageLabel.setText(message);
    }

    /**
     * Describes the controller.
     *
     * @return the screen it drives
     */
    @Override
    public String toString() {
        return "WarningAckController: warning-ack.fxml";
    }
}
