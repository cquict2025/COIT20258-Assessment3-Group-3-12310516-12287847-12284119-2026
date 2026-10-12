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
import javafx.scene.control.Button;
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
     * Describes the controller.
     *
     * @return the screen it drives
     */
    @Override
    public String toString() {
        return "WarningMainController: warning-main.fxml";
    }

    private void showAcknowledgementScreen() {
        try {
            this.root.getChildren().setAll(
                    ModuleViewLoader.load("warning-ack.fxml", this.context));
        } catch (IOException e) {
            this.root.getChildren().setAll(new Label("The acknowledgement "
                    + "screen could not be opened: " + e.getMessage()));
        }
    }

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

    private void setUpForm() {
        this.levelCombo.getItems().setAll(WarningLevel.values());
        this.incidentCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Incident incident) {
                return incident == null ? "" : incident.getIncidentId()
                        + "  " + incident.getTitle() + " ("
                        + incident.getPriority() + ")";
            }

            @Override
            public Incident fromString(String text) {
                return null;
            }
        });
        this.expiryDate.setValue(LocalDateTime.now().toLocalDate());
    }

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

    private Optional<Warning> fail(Node field, String message) {
        fieldError(field, message);
        return Optional.empty();
    }

    private void fieldError(Node field, String message) {
        field.getStyleClass().add("field-error");
        field.requestFocus();
        showError(message);
    }

    private void clearErrors() {
        for (Node field : List.of(this.incidentCombo, this.levelCombo,
                this.areaField, this.messageArea, this.expiryDate,
                this.expiryTime, this.centreField, this.capacityField,
                this.reasonField)) {
            field.getStyleClass().remove("field-error");
        }
    }

    private void showError(String message) {
        this.messageLabel.getStyleClass().setAll("error-label");
        this.messageLabel.setText(message);
    }

    private String selectedId() {
        Warning selected = this.warningTable.getSelectionModel()
                .getSelectedItem();
        return selected == null ? null : selected.getWarningId();
    }

    private static String status(Warning warning) {
        if (warning.getStatus() == WarningStatus.CANCELLED) {
            return "Cancelled";
        }
        return warning.isInForceAt(LocalDateTime.now()) ? "In force"
                : "Expired";
    }
}
