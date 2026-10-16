package au.edu.cqu.drs.controller.warning;

import au.edu.cqu.drs.client.BackgroundCall;
import au.edu.cqu.drs.client.ModuleContext;
import au.edu.cqu.drs.client.ModuleViewLoader;
import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.model.warning.EvacuationCentre;
import au.edu.cqu.drs.model.warning.Warning;
import au.edu.cqu.drs.model.warning.WarningAcknowledgement;
import au.edu.cqu.drs.model.warning.WarningKeys;
import au.edu.cqu.drs.model.warning.WarningLevel;
import au.edu.cqu.drs.model.warning.WarningStatus;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.service.ServiceResult;
import au.edu.cqu.drs.service.WarningService;
import au.edu.cqu.drs.util.TimeParser;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * The staff warnings screen (warning-main.fxml), opened from "6. Public
 * warnings". A coordinator issues, updates and cancels warnings here and
 * sees which departments have acknowledged each one. A liaison is shown the
 * acknowledgement screen instead; other roles see the list read-only.
 *
 * <p>Controllers in this package talk to the server only through
 * {@link WarningCalls}, never to a service or repository.</p>
 */
public class WarningMainController {

    private final ModuleContext context;
    private final WarningCalls calls;

    @FXML
    private VBox root;
    @FXML
    private Label hintLabel;
    @FXML
    private TableView<Warning> warningTable;
    @FXML
    private TableColumn<Warning, String> codeColumn;
    @FXML
    private TableColumn<Warning, String> versionColumn;
    @FXML
    private TableColumn<Warning, WarningLevel> levelColumn;
    @FXML
    private TableColumn<Warning, String> areaColumn;
    @FXML
    private TableColumn<Warning, String> statusColumn;
    @FXML
    private TableColumn<Warning, String> expiresColumn;
    @FXML
    private TableColumn<Warning, String> ackColumn;
    @FXML
    private ComboBox<Incident> incidentCombo;
    @FXML
    private ComboBox<WarningLevel> levelCombo;
    @FXML
    private TextField areaField;
    @FXML
    private TextArea messageArea;
    @FXML
    private TextArea actionArea;
    @FXML
    private TextField centreField;
    @FXML
    private TextField capacityField;
    @FXML
    private DatePicker expiryDate;
    @FXML
    private TextField expiryTime;
    @FXML
    private TextField reasonField;
    @FXML
    private Button issueButton;
    @FXML
    private Button updateButton;
    @FXML
    private Button clearButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Label messageLabel;
    @FXML
    private ListView<String> acknowledgedList;
    @FXML
    private ListView<String> pendingList;
    @FXML
    private ListView<String> historyList;

    /**
     * Creates the controller.
     *
     * @param context the signed-in user's connection
     */
    public WarningMainController(ModuleContext context) {
        this.context = context;
        this.calls = new WarningCalls(context);
    }

    /**
     * Sets up the screen for the user's role and loads the data.
     */
    @FXML
    private void initialize() {
        User user = this.context.getCurrentUser();
        if (user != null && user.getRole() == UserRole.LIAISON) {
            showAcknowledgementScreen();
            return;
        }
        setUpTable();
        setUpForm();
        if (user == null || user.getRole() != UserRole.COORDINATOR) {
            readOnly();
        }
        loadIncidents();
        handleRefresh();
    }

    /**
     * Reloads the warnings list.
     */
    @FXML
    private void handleRefresh() {
        BackgroundCall.run(this.root,
                () -> this.calls.warnings(Command.LIST_WARNINGS),
                this::showWarnings);
    }

    /**
     * Issues a new warning from the form.
     */
    @FXML
    private void handleIssue() {
        Optional<Warning> warning = readForm(null);
        if (warning.isEmpty()) {
            return;
        }
        BackgroundCall.run(this.root,
                () -> this.calls.send(Command.ISSUE_WARNING, Warning.class,
                        WarningKeys.WARNING, warning.get()),
                result -> saved(result, "Issued"));
    }

    /**
     * Stores the form as the next version of the selected warning.
     */
    @FXML
    private void handleUpdate() {
        Warning selected = this.warningTable.getSelectionModel()
                .getSelectedItem();
        if (selected == null) {
            showError("Select the warning to update in the list.");
            return;
        }
        Optional<Warning> warning = readForm(selected.getWarningId());
        if (warning.isEmpty()) {
            return;
        }
        BackgroundCall.run(this.root,
                () -> this.calls.send(Command.UPDATE_WARNING, Warning.class,
                        WarningKeys.WARNING, warning.get()),
                result -> saved(result, "Updated"));
    }

    /**
     * Cancels the selected warning.
     */
    @FXML
    private void handleCancel() {
        Warning selected = this.warningTable.getSelectionModel()
                .getSelectedItem();
        clearErrors();
        if (selected == null) {
            showError("Select the warning to cancel in the list.");
            return;
        }
        String reason = this.reasonField.getText().trim();
        if (reason.isEmpty()) {
            fieldError(this.reasonField, "Give a reason for cancelling.");
            return;
        }
        if (!confirmCancel(selected)) {
            return;
        }
        BackgroundCall.run(this.root,
                () -> this.calls.send(Command.CANCEL_WARNING, Warning.class,
                        WarningKeys.WARNING_ID, selected.getWarningId(),
                        WarningKeys.REASON, reason),
                result -> saved(result, "Cancelled"));
    }

    /**
     * Empties the form so a new warning can be written.
     */
    @FXML
    private void handleClear() {
        this.warningTable.getSelectionModel().clearSelection();
        this.incidentCombo.getSelectionModel().clearSelection();
        this.levelCombo.getSelectionModel().clearSelection();
        this.areaField.clear();
        this.messageArea.clear();
        this.actionArea.clear();
        this.centreField.clear();
        this.capacityField.clear();
        this.expiryDate.setValue(LocalDateTime.now().toLocalDate());
        this.expiryTime.clear();
        this.reasonField.clear();
        clearErrors();
        this.messageLabel.setText("");
    }

    /**
     * Builds the question asked before a warning is cancelled. It names the
     * warning, its level and its area, so a coordinator who has the wrong
     * row selected sees that before anything changes.
     *
     * @param warning the warning about to be cancelled
     * @return the question
     */
    static String cancelQuestion(Warning warning) {
        return String.format("Cancel %s, %s for %s?%n%nThe public will stop "
                + "seeing it. A cancelled warning cannot be changed or "
                + "reissued; issue a new one if it is still needed.",
                warning.getWarningId(), warning.getLevel(),
                warning.getAffectedArea());
    }

    /**
     * Asks the coordinator to confirm a cancellation.
     *
     * @param warning the selected warning
     * @return true only if they chose to cancel it
     */
    private boolean confirmCancel(Warning warning) {
        ButtonType cancelIt = new ButtonType("Cancel warning",
                ButtonBar.ButtonData.OK_DONE);
        ButtonType keepIt = new ButtonType("Keep it",
                ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                cancelQuestion(warning), keepIt, cancelIt);
        alert.setTitle("Cancel warning");
        alert.setHeaderText("Cancel " + warning.getWarningId() + "?");
        alert.initOwner(this.root.getScene().getWindow());
        // "Keep it" is the default, so Enter never cancels a warning.
        ((Button) alert.getDialogPane().lookupButton(keepIt))
                .setDefaultButton(true);
        ((Button) alert.getDialogPane().lookupButton(cancelIt))
                .setDefaultButton(false);
        return alert.showAndWait().filter(cancelIt::equals).isPresent();
    }

    /**
     * Replaces this screen with the liaison's acknowledgement screen, or with
     * an explanation if that screen cannot be loaded.
     */
    private void showAcknowledgementScreen() {
        try {
            this.root.getChildren().setAll(
                    ModuleViewLoader.load("warning-ack.fxml", this.context));
        } catch (IOException e) {
            this.root.getChildren().setAll(new Label("The acknowledgement "
                    + "screen could not be opened: " + e.getMessage()));
        }
    }

    /**
     * Connects the table columns to the warning fields and shows the selected
     * warning in the form when the selection changes.
     */
    private void setUpTable() {
        this.codeColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getWarningId()));
        this.versionColumn.setCellValueFactory(cell ->
                new SimpleStringProperty("v" + cell.getValue().getVersion()));
        this.levelColumn.setCellValueFactory(cell ->
                new SimpleObjectProperty<>(cell.getValue().getLevel()));
        this.levelColumn.setCellFactory(column -> new LevelCell());
        this.areaColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getAffectedArea()));
        this.statusColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(status(cell.getValue())));
        this.expiresColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getExpiresAt()
                        .format(WarningCalls.TIME_FORMAT)));
        this.ackColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue()
                        .getAcknowledgements().size() + " of "
                        + Department.values().length));
        this.warningTable.setPlaceholder(new Label("No warnings issued yet."));
        this.warningTable.getSelectionModel().selectedItemProperty()
                .addListener((observed, before, now) -> select(now));
    }

    /**
     * Fills the level list, sets how incidents are shown in the incident list
     * and defaults the expiry date to today.
     */
    private void setUpForm() {
        this.levelCombo.getItems().setAll(WarningLevel.values());
        this.incidentCombo.setConverter(new StringConverter<>() {
            /**
             * Shows an incident as its code, title and priority.
             *
             * @param incident the incident, or null
             * @return the text for the list, or an empty string for null
             */
            @Override
            public String toString(Incident incident) {
                return incident == null ? "" : incident.getIncidentId()
                        + "  " + incident.getTitle() + " ("
                        + incident.getPriority() + ")";
            }

            /**
             * Not used: the incident list cannot be typed into.
             *
             * @param text the typed text
             * @return always null
             */
            @Override
            public Incident fromString(String text) {
                return null;
            }
        });
        this.expiryDate.setValue(LocalDateTime.now().toLocalDate());
    }

    /**
     * Disables the form and its buttons for users who are not coordinators,
     * and explains why.
     */
    private void readOnly() {
        for (Node control : List.of(this.incidentCombo, this.levelCombo,
                this.areaField, this.messageArea, this.actionArea,
                this.centreField, this.capacityField, this.expiryDate,
                this.expiryTime, this.reasonField, this.issueButton,
                this.updateButton, this.clearButton, this.cancelButton)) {
            control.setDisable(true);
        }
        this.hintLabel.setText("Only a coordinator can issue, update or "
                + "cancel warnings. You can see every warning and who has "
                + "acknowledged it.");
    }

    /**
     * Loads the open incidents into the incident list in the background.
     */
    private void loadIncidents() {
        BackgroundCall.run(this.root,
                () -> this.calls.send(Command.LIST_OPEN_INCIDENTS,
                        ArrayList.class),
                result -> {
                    if (result.isFailure()) {
                        showError(result.getMessage());
                        return;
                    }
                    List<Incident> incidents = new ArrayList<>();
                    for (Object item : result.getValue()) {
                        incidents.add((Incident) item);
                    }
                    this.incidentCombo.getItems().setAll(incidents);
                });
    }

    /**
     * Shows the warnings in the table, keeping the previously selected warning
     * selected, or shows the error if the list could not be loaded.
     *
     * @param result the server's answer to the list request
     */
    private void showWarnings(ServiceResult<List<Warning>> result) {
        if (result.isFailure()) {
            showError(result.getMessage());
            return;
        }
        String selectedId = selectedId();
        this.warningTable.getItems().setAll(result.getValue());
        for (Warning warning : result.getValue()) {
            if (warning.getWarningId().equals(selectedId)) {
                this.warningTable.getSelectionModel().select(warning);
            }
        }
    }

    /**
     * Fills the form, acknowledgement lists and history for a warning.
     *
     * @param warning the selected warning, or null when nothing is selected
     */
    private void select(Warning warning) {
        this.acknowledgedList.getItems().clear();
        this.pendingList.getItems().clear();
        this.historyList.getItems().clear();
        if (warning == null) {
            return;
        }
        clearErrors();
        for (Incident incident : this.incidentCombo.getItems()) {
            if (incident.getIncidentId().equals(warning.getIncidentId())) {
                this.incidentCombo.getSelectionModel().select(incident);
            }
        }
        this.levelCombo.getSelectionModel().select(warning.getLevel());
        this.areaField.setText(warning.getAffectedArea());
        this.messageArea.setText(warning.getMessage());
        this.actionArea.setText(warning.getRecommendedAction());
        EvacuationCentre centre = warning.getEvacuationCentre();
        this.centreField.setText(centre == null ? "" : centre.getName());
        this.capacityField.setText(centre == null ? ""
                : String.valueOf(centre.getCapacity()));
        this.expiryDate.setValue(warning.getExpiresAt().toLocalDate());
        this.expiryTime.setText(warning.getExpiresAt().toLocalTime()
                .toString());
        for (WarningAcknowledgement ack : warning.getAcknowledgements()) {
            this.acknowledgedList.getItems().add(ack.getDepartment()
                    .getDisplayName() + ", " + ack.getAcknowledgedAt()
                            .format(WarningCalls.SHORT_TIME));
        }
        for (Department department
                : WarningService.pendingDepartments(warning)) {
            this.pendingList.getItems().add(department.getDisplayName());
        }
        String code = warning.getWarningId();
        BackgroundCall.run(this.root,
                () -> this.calls.warnings(Command.LIST_WARNINGS,
                        WarningKeys.WARNING_ID, code),
                this::showHistory);
    }

    /**
     * Lists every version of the selected warning with its level, status, who
     * issued it and when. Does nothing if the history could not be loaded.
     *
     * @param result the server's answer to the history request
     */
    private void showHistory(ServiceResult<List<Warning>> result) {
        if (result.isFailure()) {
            return;
        }
        this.historyList.getItems().clear();
        for (Warning version : result.getValue()) {
            this.historyList.getItems().add(String.format("v%d %s, %s",
                    version.getVersion(), version.getLevel(),
                    version.getStatus()));
            this.historyList.getItems().add("     " + version.getIssuedBy()
                    + ", " + version.getIssuedAt()
                            .format(WarningCalls.SHORT_TIME));
        }
    }

    /**
     * Reads and checks the form. Problems the client can see are shown on
     * the field at once; the server checks every rule again.
     *
     * @param warningId the code when updating, or null for a new warning
     * @return the warning, or empty if a field is wrong
     */
    private Optional<Warning> readForm(String warningId) {
        clearErrors();
        Incident incident = this.incidentCombo.getValue();
        if (incident == null && warningId == null) {
            return fail(this.incidentCombo, "Choose the incident.");
        }
        if (this.levelCombo.getValue() == null) {
            return fail(this.levelCombo, "Choose a warning level.");
        }
        if (this.areaField.getText().isBlank()) {
            return fail(this.areaField, "Enter the affected area.");
        }
        if (this.messageArea.getText().isBlank()) {
            return fail(this.messageArea, "Enter the warning message.");
        }
        if (this.expiryDate.getValue() == null) {
            return fail(this.expiryDate, "Choose the expiry date.");
        }
        Optional<LocalDateTime> expires = TimeParser.parse(
                this.expiryTime.getText(), this.expiryDate.getValue());
        if (expires.isEmpty()) {
            return fail(this.expiryTime, "Enter the expiry time as HH:mm, "
                    + "for example 18:00.");
        }
        EvacuationCentre centre = null;
        String centreName = this.centreField.getText().trim();
        String capacityText = this.capacityField.getText().trim();
        if (!centreName.isEmpty() || !capacityText.isEmpty()) {
            int capacity;
            try {
                capacity = Integer.parseInt(capacityText);
            } catch (NumberFormatException e) {
                return fail(this.capacityField, "Capacity must be a whole "
                        + "number of people.");
            }
            centre = new EvacuationCentre(centreName, capacity);
        }
        Warning warning = new Warning(incident == null ? null
                : incident.getIncidentId(), this.levelCombo.getValue(),
                this.areaField.getText().trim(),
                this.messageArea.getText().trim(), expires.get());
        warning.setWarningId(warningId);
        warning.setRecommendedAction(this.actionArea.getText().trim());
        warning.setEvacuationCentre(centre);
        return Optional.of(warning);
    }

    /**
     * Reports a successful issue, update or cancellation and reloads the
     * table, or marks the field the server named as wrong.
     *
     * @param result the server's answer
     * @param verb   what was done, for example "Issued"
     */
    private void saved(ServiceResult<Warning> result, String verb) {
        if (result.isFailure()) {
            Node field = fieldFor(result.getAffectedField());
            if (field != null) {
                fieldError(field, result.getMessage());
            } else {
                showError(result.getMessage());
            }
            return;
        }
        Warning warning = result.getValue();
        this.messageLabel.getStyleClass().setAll("success-label");
        this.messageLabel.setText(String.format("%s %s (version %d, %s).",
                verb, warning.getWarningId(), warning.getVersion(),
                warning.getLevel()));
        this.reasonField.clear();
        handleRefresh();
    }

    /**
     * Finds the form control for a field name the server reported.
     *
     * @param affectedField the field name from the server, or null
     * @return the control, or null if there is none for that name
     */
    private Node fieldFor(String affectedField) {
        if (affectedField == null) {
            return null;
        }
        Map<String, Node> fields = Map.of(
                "incidentId", this.incidentCombo,
                "level", this.levelCombo,
                "affectedArea", this.areaField,
                "message", this.messageArea,
                "expiresAt", this.expiryTime,
                "evacuationCentre", this.centreField,
                "evacuationCapacity", this.capacityField,
                "reason", this.reasonField);
        return fields.get(affectedField);
    }

    /**
     * Marks a field as wrong and gives up reading the form.
     *
     * @param field   the control that is wrong
     * @param message what is wrong with it
     * @return always empty
     */
    private Optional<Warning> fail(Node field, String message) {
        fieldError(field, message);
        return Optional.empty();
    }

    /**
     * Highlights a control, moves the focus to it and shows the message.
     *
     * @param field   the control that is wrong
     * @param message what is wrong with it
     */
    private void fieldError(Node field, String message) {
        field.getStyleClass().add("field-error");
        field.requestFocus();
        showError(message);
    }

    /**
     * Removes the error highlight from every form control.
     */
    private void clearErrors() {
        for (Node field : List.of(this.incidentCombo, this.levelCombo,
                this.areaField, this.messageArea, this.expiryDate,
                this.expiryTime, this.centreField, this.capacityField,
                this.reasonField)) {
            field.getStyleClass().remove("field-error");
        }
    }

    /**
     * Shows an error message under the form.
     *
     * @param message the message to show
     */
    private void showError(String message) {
        this.messageLabel.getStyleClass().setAll("error-label");
        this.messageLabel.setText(message);
    }

    /**
     * Gives the code of the warning selected in the table.
     *
     * @return the code, or null when nothing is selected
     */
    private String selectedId() {
        Warning selected = this.warningTable.getSelectionModel()
                .getSelectedItem();
        return selected == null ? null : selected.getWarningId();
    }

    /**
     * Describes a warning's state for the table: cancelled, in force or
     * expired.
     *
     * @param warning the warning
     * @return "Cancelled", "In force" or "Expired"
     */
    private static String status(Warning warning) {
        if (warning.getStatus() == WarningStatus.CANCELLED) {
            return "Cancelled";
        }
        return warning.isInForceAt(LocalDateTime.now()) ? "In force"
                : "Expired";
    }

    /**
     * Describes the controller.
     *
     * @return the screen it drives
     */
    @Override
    public String toString() {
        return "WarningMainController: warning-main.fxml";
    }
}
