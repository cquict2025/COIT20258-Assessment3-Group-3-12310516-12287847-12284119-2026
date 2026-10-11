package au.edu.cqu.drs.controller;

import au.edu.cqu.drs.AppContext;
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

    private final AppContext context;
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

    public FieldUpdateController(AppContext context) {
        this.context = context;
        this.view = ViewLoader.load("field-update.fxml", this);
        this.incidentCombo.setOnAction(event -> loadTimeline());
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
        loadTimeline();
    }

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

        ServiceResult<FieldUpdate> result =
                this.context.getIncidentService()
                        .addFieldUpdate(this.context.getCurrentUser(),
                                                            update);
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

    private void loadTimeline() {
        Incident incident = selectedIncident();
        if (incident == null) {
            this.timelineList.setItems(FXCollections.observableArrayList());
            return;
        }
        this.timelineList.setItems(FXCollections.observableArrayList(
                this.context.getIncidentService()
                        .findTimeline(incident.getIncidentId()).stream()
                        .map(FieldUpdate::toString)
                        .toList()));
    }

    private Incident selectedIncident() {
        int index = this.incidentCombo.getSelectionModel().getSelectedIndex();
        return index < 0 || index >= this.openIncidents.size() ? null :
                this.openIncidents
                .get(index);
    }

    private void showError(String message) {
        this.messageLabel.getStyleClass().setAll("error-label");
        this.messageLabel.setText(message);
    }

    private void showSuccess(String message) {
        this.messageLabel.getStyleClass().setAll("success-label");
        this.messageLabel.setText(message);
    }
}
