package au.edu.cqu.drs.controller;

import au.edu.cqu.drs.client.BackgroundCall;
import au.edu.cqu.drs.client.RemoteServices;
import au.edu.cqu.drs.model.EscalationAlert;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.service.ServiceResult;
import au.edu.cqu.drs.util.ViewLoader;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The prioritised operational dashboard (UC-12, FR-11) and the escalation alert
 * panel (creative feature 1).
 *
 * <p>Priority and escalation state are shown as text as well as colour, because
 * NFR-06 forbids colour as the only carrier of meaning.</p>
 */
public class DashboardController {

    private static final String ALL_PRIORITIES = "All priorities";

    private final RemoteServices services;
    private final Parent view;

    private Map<String, EscalationAlert> escalationByIncident = Map.of();

    @FXML
    private ComboBox<String> priorityFilterCombo;
    @FXML
    private Label countLabel;
    @FXML
    private Label messageLabel;
    @FXML
    private TableView<Incident> incidentTable;
    @FXML
    private TableColumn<Incident, String> idColumn;
    @FXML
    private TableColumn<Incident, String> titleColumn;
    @FXML
    private TableColumn<Incident, String> priorityColumn;
    @FXML
    private TableColumn<Incident, String> statusColumn;
    @FXML
    private TableColumn<Incident, String> ageColumn;
    @FXML
    private TableColumn<Incident, String> escalationColumn;
    @FXML
    private TableColumn<Incident, String> assignmentColumn;
    @FXML
    private TableView<EscalationAlert> alertTable;
    @FXML
    private TableColumn<EscalationAlert, String> alertIncidentColumn;
    @FXML
    private TableColumn<EscalationAlert, String> alertPriorityColumn;
    @FXML
    private TableColumn<EscalationAlert, String> alertStateColumn;
    @FXML
    private TableColumn<EscalationAlert, String> alertElapsedColumn;
    @FXML
    private TableColumn<EscalationAlert, String> alertRemainingColumn;
    @FXML
    private TableColumn<EscalationAlert, String> alertPercentColumn;

    /**
     * Creates the dashboard and loads it from the server.
     *
     * @param services the services on the server
     */
    public DashboardController(RemoteServices services) {
        this.services = services;
        this.view = ViewLoader.load("dashboard.fxml", this);
        configureFilter();
        configureIncidentColumns();
        configureAlertColumns();
        handleRefresh();
    }

    public Parent getView() {
        return this.view;
    }

    /**
     * Reloads the open incidents, then their escalation state, from the
     * server.
     */
    @FXML
    private void handleRefresh() {
        String selected = this.priorityFilterCombo.getValue();
        Priority filter = null;
        if (selected != null && !ALL_PRIORITIES.equals(selected)) {
            filter = Priority.valueOf(selected);
        }
        Priority priority = filter;
        BackgroundCall.run(this.view, () -> priority == null
                ? this.services.findOpenIncidents()
                : this.services.findOpenIncidents(priority),
                this::showIncidents);
    }

    /**
     * Shows the incidents and asks for their escalation state.
     *
     * @param result the open incidents, or the failure
     */
    private void showIncidents(ServiceResult<List<Incident>> result) {
        if (result.isFailure()) {
            this.messageLabel.setText(result.getMessage());
            return;
        }
        List<Incident> incidents = result.getValue();
        this.incidentTable.setItems(FXCollections
                .observableArrayList(incidents));
        this.countLabel.setText(incidents.size() + " open incident(s)");
        BackgroundCall.run(this.view, this.services::evaluateEscalations,
                this::showEscalationColumn);
    }

    /**
     * Fills the escalation column and asks for the alerts.
     *
     * @param result one alert per open incident, or the failure
     */
    private void showEscalationColumn(
            ServiceResult<List<EscalationAlert>> result) {
        if (result.isFailure()) {
            this.messageLabel.setText(result.getMessage());
            return;
        }
        Map<String, EscalationAlert> byIncident = new HashMap<>();
        for (EscalationAlert alert : result.getValue()) {
            byIncident.put(alert.getIncident().getIncidentId(), alert);
        }
        this.escalationByIncident = byIncident;
        this.incidentTable.refresh();
        BackgroundCall.run(this.view, this.services::findAlerts,
                this::showAlerts);
    }

    /**
     * Shows the incidents that need attention now.
     *
     * @param result the at-risk and breached alerts, or the failure
     */
    private void showAlerts(ServiceResult<List<EscalationAlert>> result) {
        if (result.isFailure()) {
            this.messageLabel.setText(result.getMessage());
            return;
        }
        this.alertTable.setItems(FXCollections.observableArrayList(
                result.getValue()));
        this.messageLabel.setText(this.alertTable.getItems().isEmpty()
                ? "No incident has passed 75% of its target response time."
                : this.alertTable.getItems()
                        .size() + " incident(s) need attention now.");
    }

    private void configureFilter() {
        this.priorityFilterCombo.setItems(FXCollections.observableArrayList(
                ALL_PRIORITIES, Priority.P1.name(), Priority.P2.name(),
                Priority.P3.name(), Priority.P4.name()));
        this.priorityFilterCombo.getSelectionModel().select(ALL_PRIORITIES);
        this.priorityFilterCombo.setOnAction(event -> handleRefresh());
    }

    private void configureIncidentColumns() {
        this.idColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getIncidentId()));
        this.titleColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getTitle()));
        this.priorityColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getPriority()
                        .getDisplayName()));
        this.statusColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getStatus()
                        .getDisplayName()));
        this.ageColumn.setCellValueFactory(cell -> new SimpleStringProperty(
                String.valueOf(Duration.between(cell.getValue().getCreatedAt(),
                        LocalDateTime.now()).toMinutes())));
        this.escalationColumn.setCellValueFactory(cell -> {
            EscalationAlert alert = this.escalationByIncident
                    .get(cell.getValue().getIncidentId());
            if (alert == null) {
                return new SimpleStringProperty("-");
            }
            return new SimpleStringProperty(String.format("%s (%d%% of target)",
                    alert.getState().getDisplayName(), alert
                            .getPercentageOfTargetUsed()));
        });
        this.assignmentColumn.setCellValueFactory(cell -> new
                SimpleStringProperty(
                String.valueOf(cell.getValue().getAssignments().size())));
    }

    private void configureAlertColumns() {
        this.alertIncidentColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getIncident()
                        .getIncidentId()));
        this.alertPriorityColumn
                .setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getIncident().getPriority().getDisplayName()));
        this.alertStateColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().getState()
                        .getDisplayName()));
        this.alertElapsedColumn.setCellValueFactory(cell ->
                new SimpleStringProperty(String
                        .valueOf(cell.getValue().getMinutesElapsed())));
        this.alertRemainingColumn
                .setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getMinutesRemaining() >= 0
                        ? String.valueOf(cell.getValue().getMinutesRemaining())
                        : Math.abs(cell.getValue().getMinutesRemaining())
                                + " overdue"));
        this.alertPercentColumn.setCellValueFactory(cell -> new
                SimpleStringProperty(
                cell.getValue().getPercentageOfTargetUsed() + "%"));
    }
}
