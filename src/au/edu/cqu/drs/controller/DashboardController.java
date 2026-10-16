package au.edu.cqu.drs.controller;

import au.edu.cqu.drs.client.BackgroundCall;
import au.edu.cqu.drs.client.RemoteServices;
import au.edu.cqu.drs.client.event.EventListener;
import au.edu.cqu.drs.model.EscalationAlert;
import au.edu.cqu.drs.model.EscalationState;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.server.event.EventType;
import au.edu.cqu.drs.server.event.ServerEvent;
import au.edu.cqu.drs.service.ServiceResult;
import au.edu.cqu.drs.util.ViewLoader;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The prioritised operational dashboard (UC-12, FR-11) and the escalation alert
 * panel (creative feature 1).
 *
 * <p>Priority and escalation state are shown as text as well as colour, because
 * NFR-06 forbids colour as the only carrier of meaning.</p>
 *
 * <p>The dashboard is also the live operating picture (Feature 2). It
 * listens to the server's events: each change made by any user is added to
 * the activity feed and the tables reload by themselves, the number of
 * connected users is shown, and a red banner names every incident that has
 * Breached its target response time.</p>
 */
public class DashboardController {

    private static final String ALL_PRIORITIES = "All priorities";

    private final RemoteServices services;
    private final EventListener events;
    private final Parent view;
    private final Consumer<ServerEvent> eventHandler;
    private final Consumer<String> statusHandler;

    private Map<String, EscalationAlert> escalationByIncident = Map.of();
    private boolean refreshing;
    private boolean refreshQueued;

    @FXML
    private ComboBox<String> priorityFilterCombo;
    @FXML
    private Label countLabel;
    @FXML
    private Label messageLabel;
    @FXML
    private Label breachBanner;
    @FXML
    private Label liveLabel;
    @FXML
    private Label connectedLabel;
    @FXML
    private ListView<String> activityList;
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
     * Creates the dashboard without live updates and loads it from the
     * server.
     *
     * @param services the services on the server
     */
    public DashboardController(RemoteServices services) {
        this(services, null);
    }

    /**
     * Creates the dashboard, loads it from the server and starts following
     * the live events.
     *
     * @param services the services on the server
     * @param events   the live event listener, or null for none
     */
    public DashboardController(RemoteServices services,
                               EventListener events) {
        this.services = services;
        this.events = events;
        this.eventHandler = this::onEvent;
        this.statusHandler = this::showStatus;
        this.view = ViewLoader.load("dashboard.fxml", this);
        configureFilter();
        configureIncidentColumns();
        configureAlertColumns();
        configureLiveView();
        handleRefresh();
    }

    /**
     * Returns the dashboard screen.
     *
     * @return the view
     */
    public Parent getView() {
        return this.view;
    }

    /**
     * Stops following the live events; called when another screen
     * replaces the dashboard.
     */
    public void detach() {
        if (this.events != null) {
            this.events.removeListener(this.eventHandler);
            this.events.removeStatusListener(this.statusHandler);
        }
    }

    /**
     * Reloads the open incidents, then their escalation state, from the
     * server, showing the screen as busy.
     */
    @FXML
    private void handleRefresh() {
        refresh(this.view);
    }

    /**
     * Reloads the tables. If a reload is already running, one more runs
     * when it ends, so a burst of events causes at most two reloads.
     *
     * @param busyScreen the screen to show as busy, or null for a quiet
     *                   reload after a live event
     */
    private void refresh(Node busyScreen) {
        if (this.refreshing) {
            this.refreshQueued = true;
            return;
        }
        this.refreshing = true;
        String selected = this.priorityFilterCombo.getValue();
        Priority filter = null;
        if (selected != null && !ALL_PRIORITIES.equals(selected)) {
            filter = Priority.valueOf(selected);
        }
        Priority priority = filter;
        BackgroundCall.run(busyScreen, () -> priority == null
                ? this.services.findOpenIncidents()
                : this.services.findOpenIncidents(priority),
                this::showIncidents);
    }

    /**
     * Ends a reload, starting the queued one if events arrived meanwhile.
     */
    private void refreshDone() {
        this.refreshing = false;
        if (this.refreshQueued) {
            this.refreshQueued = false;
            refresh(null);
        }
    }

    /**
     * Shows a live event in the feed and reloads the tables; runs on the
     * JavaFX thread.
     *
     * @param event the event
     */
    private void onEvent(ServerEvent event) {
        if (event.getType() == EventType.CLIENTS) {
            showClientCount(event.getClientCount());
            return;
        }
        this.activityList.getItems().add(0, event.toFeedLine());
        int limit = EventListener.RECENT_LIMIT;
        if (this.activityList.getItems().size() > limit) {
            this.activityList.getItems().remove(limit,
                    this.activityList.getItems().size());
        }
        refresh(null);
    }

    /**
     * Shows the live connection status.
     *
     * @param status the status
     */
    private void showStatus(String status) {
        this.liveLabel.setText(status);
    }

    /**
     * Shows how many users are receiving live updates.
     *
     * @param count the number of clients
     */
    private void showClientCount(int count) {
        if (count <= 0) {
            this.connectedLabel.setText("");
            return;
        }
        this.connectedLabel.setText("Connected users: " + count);
    }

    /**
     * Shows the red banner naming every Breached incident, or hides it.
     *
     * @param alerts the at-risk and breached alerts
     */
    private void showBreachBanner(List<EscalationAlert> alerts) {
        List<String> breached = alerts.stream()
                .filter(alert -> alert.getState()
                        == EscalationState.BREACHED)
                .map(alert -> alert.getIncident().getIncidentId())
                .collect(Collectors.toList());
        boolean show = !breached.isEmpty();
        this.breachBanner.setVisible(show);
        this.breachBanner.setManaged(show);
        if (show) {
            this.breachBanner.setText("BREACHED: " + breached.size()
                    + " incident(s) past their target response time - "
                    + String.join(", ", breached));
        }
    }

    /**
     * Shows the incidents and asks for their escalation state.
     *
     * @param result the open incidents, or the failure
     */
    private void showIncidents(ServiceResult<List<Incident>> result) {
        if (result.isFailure()) {
            this.messageLabel.setText(result.getMessage());
            refreshDone();
            return;
        }
        List<Incident> incidents = result.getValue();
        this.incidentTable.setItems(FXCollections
                .observableArrayList(incidents));
        this.countLabel.setText(incidents.size() + " open incident(s)");
        BackgroundCall.run(null, this.services::evaluateEscalations,
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
            refreshDone();
            return;
        }
        Map<String, EscalationAlert> byIncident = new HashMap<>();
        for (EscalationAlert alert : result.getValue()) {
            byIncident.put(alert.getIncident().getIncidentId(), alert);
        }
        this.escalationByIncident = byIncident;
        this.incidentTable.refresh();
        BackgroundCall.run(null, this.services::findAlerts,
                this::showAlerts);
    }

    /**
     * Shows the incidents that need attention now.
     *
     * @param result the at-risk and breached alerts, or the failure
     */
    private void showAlerts(ServiceResult<List<EscalationAlert>> result) {
        refreshDone();
        if (result.isFailure()) {
            this.messageLabel.setText(result.getMessage());
            return;
        }
        this.alertTable.setItems(FXCollections.observableArrayList(
                result.getValue()));
        showBreachBanner(result.getValue());
        this.messageLabel.setText(this.alertTable.getItems().isEmpty()
                ? "No incident has passed 75% of its target response time."
                : this.alertTable.getItems()
                        .size() + " incident(s) need attention now.");
    }

    /**
     * Fills the feed with the events received before this screen opened
     * and starts following new ones.
     */
    private void configureLiveView() {
        if (this.events == null) {
            this.liveLabel.setText("Live updates off");
            return;
        }
        for (ServerEvent event : this.events.getRecentEvents()) {
            this.activityList.getItems().add(event.toFeedLine());
        }
        showStatus(this.events.getStatus());
        showClientCount(this.events.getClientCount());
        this.events.addListener(this.eventHandler);
        this.events.addStatusListener(this.statusHandler);
    }

    /**
     * Fills the priority filter and reloads when it changes.
     */
    private void configureFilter() {
        this.priorityFilterCombo.setItems(FXCollections.observableArrayList(
                ALL_PRIORITIES, Priority.P1.name(), Priority.P2.name(),
                Priority.P3.name(), Priority.P4.name()));
        this.priorityFilterCombo.getSelectionModel().select(ALL_PRIORITIES);
        this.priorityFilterCombo.setOnAction(event -> handleRefresh());
    }

    /**
     * Tells each incident column which value to show.
     */
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

    /**
     * Tells each escalation alert column which value to show.
     */
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

    /**
     * Describes the controller.
     *
     * @return what the controller shows
     */
    @Override
    public String toString() {
        return "DashboardController: operational dashboard, live updates "
                + (this.events == null ? "off" : "on");
    }
}
