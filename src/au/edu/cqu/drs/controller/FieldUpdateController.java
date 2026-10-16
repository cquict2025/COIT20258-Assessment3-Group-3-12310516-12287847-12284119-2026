package au.edu.cqu.drs.controller;

import au.edu.cqu.drs.client.BackgroundCall;
import au.edu.cqu.drs.client.RemoteServices;
import au.edu.cqu.drs.model.FieldUpdate;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.service.ServiceResult;
import au.edu.cqu.drs.util.TimeParser;
import au.edu.cqu.drs.util.ViewLoader;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.List;

/**
 * Field update screen (UC-10). The version of the incident the responder was
 * shown is sent with the update so that the service can reject an update
 * written against stale data instead of overwriting a newer change.
 */
public class FieldUpdateController {

    private final RemoteServices services;
    private final Parent view;

    private List<Incident> openIncidents = List.of();

    @FXML
    private ComboBox<String> incidentCombo;
    @FXML
    private TextField nextReviewField;
    @FXML
    private TextArea progressArea;
    @FXML
    private TextArea impactArea;
    @FXML
    private TextArea needsArea;
    @FXML
    private Label messageLabel;
    @FXML
    private ListView<String> timelineList;

    /**
     * Creates the field update screen and loads it from the server.
     *
     * @param services the services on the server
     */
    public FieldUpdateController(RemoteServices services) {
        this.services = services;
        this.view = ViewLoader.load("field-update.fxml", this);
        this.incidentCombo.setOnAction(event -> loadTimeline());
        handleRefresh();
    }

    /**
     * Returns the field update screen.
     *
     * @return the view
     */
    public Parent getView() {
        return this.view;
    }

    /**
     * Reloads the open incidents from the server.
     */
    @FXML
    private void handleRefresh() {
        BackgroundCall.run(this.view, this.services::findOpenIncidents,
                this::showIncidents);
    }

    /**
     * Lists the open incidents and loads the first one's timeline.
     *
     * @param result the open incidents, or the failure
     */
    private void showIncidents(ServiceResult<List<Incident>> result) {
        if (result.isFailure()) {
            showError(result.getMessage());
            return;
        }
        this.openIncidents = result.getValue();
        this.incidentCombo.setItems(FXCollections.observableArrayList(
                this.openIncidents.stream().map(Incident::toString).toList()));
        if (!this.openIncidents.isEmpty()) {
            this.incidentCombo.getSelectionModel().selectFirst();
        }
        loadTimeline();
    }

    /**
     * Sends the field update for the selected incident.
     */
    @FXML
    private void handleSubmit() {
        Incident incident = selectedIncident();
        if (incident == null) {
            showError("Select an incident first.");
            return;
        }

        FieldUpdate update = new FieldUpdate();
        update.setIncidentId(incident.getIncidentId());
        update.setProgressNote(this.progressArea.getText());
        update.setObservedImpact(this.impactArea.getText());
        update.setResourceNeeds(this.needsArea.getText());
        update.setNextReviewAt(TimeParser
                .parseToday(this.nextReviewField.getText())
                .orElse(null));
        update.setIncidentVersionSeen(incident.getVersion());

        BackgroundCall.run(this.view,
                () -> this.services.addFieldUpdate(update), this::recorded);
    }

    /**
     * Confirms the update, or shows why it was refused.
     *
     * @param result the saved update, or the failure
     */
    private void recorded(ServiceResult<FieldUpdate> result) {
        if (result.isFailure()) {
            showError(result.getMessage());
            if ("progressNote".equals(result.getAffectedField())) {
                this.progressArea.requestFocus();
            }
            return;
        }

        showSuccess("Update " + result.getValue().getUpdateId()
                + " recorded at "
                + result.getValue().getSubmittedAt().toLocalTime().withSecond(0)
                        .withNano(0) + ".");
        this.progressArea.clear();
        this.impactArea.clear();
        this.needsArea.clear();
        this.nextReviewField.clear();
        handleRefresh();
    }

    /**
     * Loads the selected incident's field updates.
     */
    private void loadTimeline() {
        Incident incident = selectedIncident();
        if (incident == null) {
            this.timelineList.setItems(FXCollections.observableArrayList());
            return;
        }
        BackgroundCall.run(this.view,
                () -> this.services.findTimeline(incident.getIncidentId()),
                result -> {
                    if (result.isFailure()) {
                        showError(result.getMessage());
                        return;
                    }
                    this.timelineList.setItems(
                            FXCollections.observableArrayList(
                                    result.getValue().stream()
                                            .map(FieldUpdate::toString)
                                            .toList()));
                });
    }

    /**
     * Returns the open incident chosen in the list.
     *
     * @return the incident, or null if none is chosen
     */
    private Incident selectedIncident() {
        int index = this.incidentCombo.getSelectionModel().getSelectedIndex();
        return index < 0 || index >= this.openIncidents.size() ? null :
                this.openIncidents
                .get(index);
    }

    /**
     * Shows a message styled as an error.
     *
     * @param message the message
     */
    private void showError(String message) {
        this.messageLabel.getStyleClass().setAll("error-label");
        this.messageLabel.setText(message);
    }

    /**
     * Shows a message styled as a success.
     *
     * @param message the message
     */
    private void showSuccess(String message) {
        this.messageLabel.getStyleClass().setAll("success-label");
        this.messageLabel.setText(message);
    }

    /**
     * Describes the controller.
     *
     * @return what the controller shows
     */
    @Override
    public String toString() {
        return "FieldUpdateController: field update screen";
    }
}
