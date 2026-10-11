package au.edu.cqu.drs.controller;

import au.edu.cqu.drs.AppContext;
import au.edu.cqu.drs.model.EscalationAlert;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.Priority;
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
import java.util.List;

/**
 * The prioritised operational dashboard (UC-12, FR-11) and the escalation alert
 * panel (creative feature 1).
 *
 * <p>Priority and escalation state are shown as text as well as colour, because
 * NFR-06 forbids colour as the only carrier of meaning.</p>
 */
public class DashboardController {

    private static final String ALL_PRIORITIES = "All priorities";

    private final AppContext context;
    private final Parent view;

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

    public DashboardController(AppContext context) {
        this.context = context;
        this.view = ViewLoader.load("dashboard.fxml", this);
        configureFilter();
        configureIncidentColumns();
        configureAlertColumns();
        handleRefresh();
    }

    public Parent getView() {
        return this.view;
    }

    @FXML
    private void handleRefresh() {
        LocalDateTime now = LocalDateTime.now();
        String selected = this.priorityFilterCombo.getValue();

        List<Incident> incidents = ALL_PRIORITIES.equals(selected)
                || selected == null
                ? this.context.getIncidentService()
                        .findOpenIncidentsByPriority()
                : this.context.getIncidentService()
                        .findOpenIncidentsByPriority(Priority
                                .valueOf(selected));

        this.incidentTable.setItems(FXCollections
                .observableArrayList(incidents));
        this.alertTable.setItems(FXCollections.observableArrayList(
                this.context.getEscalationService().findAlerts(now)));

        this.countLabel.setText(incidents.size() + " open incident(s)");
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
            EscalationAlert alert = this.context.getEscalationService()
                    .evaluate(cell.getValue(), LocalDateTime.now());
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
