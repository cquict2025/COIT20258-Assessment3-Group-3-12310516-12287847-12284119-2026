package au.edu.cqu.drs.controller;

import au.edu.cqu.drs.AppContext;
import au.edu.cqu.drs.model.AssignmentStatus;
import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.DepartmentAssignment;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.ResourceAllocation;
import au.edu.cqu.drs.service.ServiceResult;
import au.edu.cqu.drs.util.TimeParser;
import au.edu.cqu.drs.util.ViewLoader;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Department coordination screen (UC-07, UC-08, UC-09).
 *
 * <p>The outage checkbox exists so that the "delivery failed, retry required"
 * path from UR-10 can be demonstrated on request rather than only
 * described.</p>
 */
public class CoordinationController {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM HH:mm");

    private final AppContext context;
    private final Parent view;

    private List<Incident> openIncidents = List.of();

    @FXML
    private ComboBox<String> incidentCombo;
    @FXML
    private CheckBox simulateOutageCheck;
    @FXML
    private Label recommendationLabel;
    @FXML
    private ComboBox<Department> departmentCombo;
    @FXML
    private ComboBox<String> urgencyCombo;
    @FXML
    private TextField actionField;
    @FXML
    private TableView<DepartmentAssignment> assignmentTable;
    @FXML
    private TableColumn<DepartmentAssignment, String> assignmentIdColumn;
    @FXML
    private TableColumn<DepartmentAssignment, String> departmentColumn;
    @FXML
    private TableColumn<DepartmentAssignment, String> actionColumn;
    @FXML
    private TableColumn<DepartmentAssignment, String> urgencyColumn;
    @FXML
    private TableColumn<DepartmentAssignment, String> assignmentStatusColumn;
    @FXML
    private TableColumn<DepartmentAssignment, String> etaColumn;
    @FXML
    private TableColumn<DepartmentAssignment, String> attemptsColumn;
    @FXML
    private ComboBox<AssignmentStatus> responseCombo;
    @FXML
    private TextField reasonField;
    @FXML
    private TextField etaField;
    @FXML
    private TextField resourceField;
    @FXML
    private TextField quantityField;
    @FXML
    private TextField fromField;
    @FXML
    private TextField toField;
    @FXML
    private Label messageLabel;

    public CoordinationController(AppContext context) {
        this.context = context;
        this.view = ViewLoader.load("coordination.fxml", this);
        this.departmentCombo.setItems(FXCollections
                .observableArrayList(Department.values()));
        this.urgencyCombo.setItems(FXCollections.observableArrayList(
                "Immediate", "Within the hour", "Same day", "Planned"));
        this.urgencyCombo.getSelectionModel().select("Immediate");
        this.responseCombo.setItems(FXCollections.observableArrayList(
                AssignmentStatus.ACCEPTED, AssignmentStatus.REJECTED,
                AssignmentStatus.CLARIFICATION_REQUESTED));
        this.responseCombo.getSelectionModel().select(AssignmentStatus
                .ACCEPTED);
        this.incidentCombo.setOnAction(event -> loadAssignments());
        configureColumns();
        handleRefresh();
    }

    public Parent getView() {
        return this.view;
    }

    @FXML
    private void handleRefresh() {
        this.openIncidents = this.context.getIncidentService()
                .findOpenIncidentsByPriority();
        this.incidentCombo.setItems(FXCollections.observableArrayList(
                this.openIncidents.stream().map(Incident::toString).toList()));
        if (!this.openIncidents.isEmpty()) {
            this.incidentCombo.getSelectionModel().selectFirst();
        }
        loadAssignments();
    }

    @FXML
    private void handleToggleOutage() {
        this.context.getPartnerGateway()
                .setAvailable(!this.simulateOutageCheck.isSelected());
        showHint(this.simulateOutageCheck.isSelected()
                ? "Partner interfaces are now simulated as unavailable. New "
                        + "requests will be saved as Delivery failed."
                : "Partner interfaces are available again.");
    }

    @FXML
    private void handleCreateAssignment() {
        Incident incident = selectedIncident();
        if (incident == null) {
            showError("Select an incident first.");
            return;
        }
        ServiceResult<DepartmentAssignment> result =
                this.context.getCoordinationService()
                .createAssignment(this.context.getCurrentUser(),
                                  incident.getIncidentId(), 
                        this.departmentCombo.getValue(), this.actionField
                                .getText(), this.urgencyCombo.getValue());

        if (result.isFailure()) {
            showError(result.getMessage());
            loadAssignments();
            return;
        }
        showSuccess("Request " + result.getValue().getAssignmentId()
                + " sent to "
                + result.getValue().getDepartment().getDisplayName() + ".");
        this.actionField.clear();
        loadAssignments();
    }

    @FXML
    private void handleRetry() {
        DepartmentAssignment assignment = selectedAssignment();
        if (assignment == null) {
            showError("Select a request in the table first.");
            return;
        }
        ServiceResult<DepartmentAssignment> result =
                this.context.getCoordinationService()
                .retryDelivery(this.context.getCurrentUser(),
                               assignment.getAssignmentId());
        if (result.isFailure()) {
            showError(result.getMessage());
        } else {
            showSuccess("Request " + assignment.getAssignmentId()
                    + " delivered on attempt "
                    + result.getValue().getDeliveryAttempts() + ".");
        }
        loadAssignments();
    }

    @FXML
    private void handleRespond() {
        DepartmentAssignment assignment = selectedAssignment();
        if (assignment == null) {
            showError("Select a request in the table first.");
            return;
        }
        LocalDateTime eta = TimeParser.parseToday(this.etaField.getText())
                .orElse(null);
        ServiceResult<DepartmentAssignment> result =
                this.context.getCoordinationService()
                .respondToAssignment(this.context.getCurrentUser(),
                                     assignment.getAssignmentId(), 
                        this.responseCombo.getValue(),
                                this.reasonField.getText(), eta);

        if (result.isFailure()) {
            showError(result.getMessage());
            if ("estimatedArrival".equals(result.getAffectedField())) {
                this.etaField.requestFocus();
            } else if ("reason".equals(result.getAffectedField())) {
                this.reasonField.requestFocus();
            }
            return;
        }
        showSuccess("Response recorded for " + assignment.getAssignmentId()
                + ".");
        this.reasonField.clear();
        this.etaField.clear();
        loadAssignments();
    }

    @FXML
    private void handleAllocate() {
        DepartmentAssignment assignment = selectedAssignment();
        if (assignment == null) {
            showError("Select a request in the table first.");
            return;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(this.quantityField.getText().trim());
        } catch (NumberFormatException e) {
            showError("Quantity must be a whole number.");
            this.quantityField.requestFocus();
            return;
        }

        ResourceAllocation allocation = new ResourceAllocation(null,
                assignment.getAssignmentId(), 
                this.resourceField.getText(), assignment.getDepartment(),
                        quantity,
                TimeParser.parseToday(this.fromField.getText()).orElse(null),
                TimeParser.parseToday(this.toField.getText()).orElse(null));

        ServiceResult<ResourceAllocation> result =
                this.context.getCoordinationService()
                .allocateResource(this.context.getCurrentUser(), allocation);
        if (result.isFailure()) {
            showError(result.getMessage());
            return;
        }
        showSuccess(result.getValue().getResourceName() + " allocated to "
                + assignment.getAssignmentId() + ".");
        loadAssignments();
    }

    private void loadAssignments() {
        Incident incident = selectedIncident();
        if (incident == null) {
            this.assignmentTable.setItems(FXCollections.observableArrayList());
            this.recommendationLabel.setText("");
            return;
        }
        this.assignmentTable.setItems(FXCollections.observableArrayList(
                this.context.getCoordinationService()
                        .findByIncident(incident.getIncidentId())));

        String recommended = this.context.getCoordinationService()
                .recommendDepartments(incident.getDisasterType()).stream()
                .map(Department::getDisplayName)
                .collect(Collectors.joining(", "));
        this.recommendationLabel.setText("Recommended departments for a "
                + incident.getDisasterType().getDisplayName()
                        .toLowerCase() + ": " + recommended);
    }

    private void configureColumns() {
        this.assignmentIdColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getAssignmentId()));
        this.departmentColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getDepartment()
                        .getDisplayName()));
        this.actionColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getRequestedAction()));
        this.urgencyColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getUrgency()));
        this.assignmentStatusColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getStatus()
                        .getDisplayName()));
        this.etaColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getEstimatedArrival() == null
                        ? "-" : cell.getValue().getEstimatedArrival()
                                .format(TIME_FORMAT)));
        this.attemptsColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(String
                        .valueOf(cell.getValue().getDeliveryAttempts())));
    }

    private Incident selectedIncident() {
        int index = this.incidentCombo.getSelectionModel().getSelectedIndex();
        return index < 0 || index >= this.openIncidents.size() ? null :
                this.openIncidents
                .get(index);
    }

    private DepartmentAssignment selectedAssignment() {
        return this.assignmentTable.getSelectionModel().getSelectedItem();
    }

    private void showError(String message) {
        this.messageLabel.getStyleClass().setAll("error-label");
        this.messageLabel.setText(message);
    }

    private void showSuccess(String message) {
        this.messageLabel.getStyleClass().setAll("success-label");
        this.messageLabel.setText(message);
    }

    private void showHint(String message) {
        this.messageLabel.getStyleClass().setAll("hint-label");
        this.messageLabel.setText(message);
    }
}
