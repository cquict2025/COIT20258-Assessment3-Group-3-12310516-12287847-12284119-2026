package au.edu.cqu.drs.controller;

import au.edu.cqu.drs.client.BackgroundCall;
import au.edu.cqu.drs.client.RemoteServices;
import au.edu.cqu.drs.model.Assessment;
import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.model.Priority;
import au.edu.cqu.drs.model.ReportStatus;
import au.edu.cqu.drs.service.ServiceResult;
import au.edu.cqu.drs.util.ViewLoader;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.util.List;

/**
 * Triage screen covering report decisions (UC-03), incident creation (UC-06)
 * and explainable prioritisation (UC-05).
 *
 * <p>The screen always shows the factor values and the resulting score before a
 * priority can be confirmed, and an override cannot be submitted without a
 * reason. That is the transparency commitment made in Assessment 1.</p>
 */
public class AssessmentController {

    private final RemoteServices services;
    private final Parent view;

    private List<DisasterReport> submittedReports = List.of();
    private List<Incident> openIncidents = List.of();
    private Assessment currentAssessment;

    @FXML
    private ListView<String> reportList;
    @FXML
    private TextField decisionReasonField;
    @FXML
    private ComboBox<String> incidentCombo;
    @FXML
    private Spinner<Integer> threatSpinner;
    @FXML
    private Spinner<Integer> scaleSpinner;
    @FXML
    private Spinner<Integer> infrastructureSpinner;
    @FXML
    private Spinner<Integer> confidenceSpinner;
    @FXML
    private Label scoreLabel;
    @FXML
    private ComboBox<Priority> confirmPriorityCombo;
    @FXML
    private TextField overrideReasonField;
    @FXML
    private TextArea assessmentDetailArea;
    @FXML
    private Label messageLabel;

    /**
     * Creates the triage screen and loads it from the server.
     *
     * @param services the services on the server
     */
    public AssessmentController(RemoteServices services) {
        this.services = services;
        this.view = ViewLoader.load("assessment.fxml", this);
        configureSpinners();
        this.confirmPriorityCombo.setItems(FXCollections
                .observableArrayList(Priority.values()));
        handleRefresh();
    }

    public Parent getView() {
        return this.view;
    }

    /**
     * Reloads the reports awaiting a decision, then the open incidents.
     */
    @FXML
    private void handleRefresh() {
        BackgroundCall.run(this.view,
                () -> this.services.findReportsByStatus(
                        ReportStatus.SUBMITTED),
                this::showReports);
    }

    /**
     * Lists the reports awaiting a decision and asks for the incidents.
     *
     * @param result the submitted reports, or the failure
     */
    private void showReports(ServiceResult<List<DisasterReport>> result) {
        if (result.isFailure()) {
            showError(result.getMessage());
            return;
        }
        this.submittedReports = result.getValue();
        this.reportList.setItems(FXCollections.observableArrayList(
                this.submittedReports.stream().map(DisasterReport::toString)
                        .toList()));
        BackgroundCall.run(this.view, this.services::findOpenIncidents,
                this::showIncidents);
    }

    /**
     * Lists the open incidents and summarises the work waiting.
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

        this.messageLabel.getStyleClass().setAll("hint-label");
        this.messageLabel.setText(this.submittedReports.size() +
                " report(s) awaiting a "
                + "decision, "
                + this.openIncidents.size() + " open incident(s).");
    }

    /**
     * Validates the selected report and creates an incident from it.
     */
    @FXML
    private void handleValidate() {
        DisasterReport report = selectedReport();
        if (report == null) {
            showError("Select a report first.");
            return;
        }
        String reason = this.decisionReasonField.getText();
        BackgroundCall.run(this.view,
                () -> this.services.decideReport(report.getReportId(),
                        ReportStatus.VALIDATED, reason),
                decision -> validated(report, decision));
    }

    /**
     * Creates the incident once the report is validated.
     *
     * @param report   the report decided
     * @param decision the validated report, or the failure
     */
    private void validated(DisasterReport report,
                           ServiceResult<DisasterReport> decision) {
        if (decision.isFailure()) {
            showError(decision.getMessage());
            return;
        }
        BackgroundCall.run(this.view,
                () -> this.services.createIncidentFromReport(
                        report.getReportId()),
                created -> incidentCreated(report, created));
    }

    /**
     * Confirms the new incident, or shows why it was not created.
     *
     * @param report  the validated report
     * @param created the incident, or the failure
     */
    private void incidentCreated(DisasterReport report,
                                 ServiceResult<Incident> created) {
        if (created.isFailure()) {
            showError(created.getMessage());
            return;
        }

        showSuccess("Report " + report.getReportId() + " validated. Incident "
                + created.getValue().getIncidentId() + " created at priority "
                + created.getValue().getPriority().getDisplayName() + ".");
        this.decisionReasonField.clear();
        handleRefresh();
    }

    /**
     * Rejects the selected report with the reason given.
     */
    @FXML
    private void handleReject() {
        DisasterReport report = selectedReport();
        if (report == null) {
            showError("Select a report first.");
            return;
        }
        String reason = this.decisionReasonField.getText();
        BackgroundCall.run(this.view,
                () -> this.services.decideReport(report.getReportId(),
                        ReportStatus.REJECTED, reason),
                result -> rejected(report, result));
    }

    /**
     * Confirms a rejection, or shows why it was refused.
     *
     * @param report the report decided
     * @param result the rejected report, or the failure
     */
    private void rejected(DisasterReport report,
                          ServiceResult<DisasterReport> result) {
        if (result.isFailure()) {
            showError(result.getMessage());
            if ("reason".equals(result.getAffectedField())) {
                this.decisionReasonField.requestFocus();
            }
            return;
        }
        showSuccess("Report " + report.getReportId()
                + " rejected and audited.");
        this.decisionReasonField.clear();
        handleRefresh();
    }

    /**
     * Asks the server to score the selected incident.
     */
    @FXML
    private void handleScore() {
        Incident incident = selectedIncident();
        if (incident == null) {
            showError("Select an incident first.");
            return;
        }

        Assessment assessment = new Assessment(incident.getIncidentId(),
                this.threatSpinner.getValue(), this.scaleSpinner.getValue(),
                this.infrastructureSpinner.getValue(),
                        this.confidenceSpinner.getValue());
        BackgroundCall.run(this.view,
                () -> this.services.scoreAssessment(assessment),
                this::scored);
    }

    /**
     * Shows the score and recommended priority.
     *
     * @param result the scored assessment, or the failure
     */
    private void scored(ServiceResult<Assessment> result) {
        if (result.isFailure()) {
            this.scoreLabel.setText("");
            showError(result.getMessage());
            return;
        }

        this.currentAssessment = result.getValue();
        this.scoreLabel.setText(String
                .format("Score %d of 100 - recommended %s",
                this.currentAssessment.getCalculatedScore(),
                this.currentAssessment.getRecommendedPriority()
                        .getDisplayName()));
        this.confirmPriorityCombo.getSelectionModel()
                .select(this.currentAssessment.getRecommendedPriority());
        this.assessmentDetailArea.setText(this.currentAssessment.toString());
        showSuccess("Recommendation calculated. Confirm it, or override it "
                + "with a reason.");
    }

    /**
     * Confirms the recommended priority, or overrides it with a reason.
     */
    @FXML
    private void handleConfirm() {
        if (this.currentAssessment == null) {
            showError("Calculate a recommendation before confirming a "
                    + "priority.");
            return;
        }
        Assessment assessment = this.currentAssessment;
        Priority priority = this.confirmPriorityCombo.getValue();
        String reason = this.overrideReasonField.getText();
        BackgroundCall.run(this.view,
                () -> this.services.confirmPriority(assessment, priority,
                        reason),
                this::confirmed);
    }

    /**
     * Shows the confirmed priority, or why it was refused.
     *
     * @param result the confirmed assessment, or the failure
     */
    private void confirmed(ServiceResult<Assessment> result) {
        if (result.isFailure()) {
            showError(result.getMessage());
            if ("overrideReason".equals(result.getAffectedField())) {
                this.overrideReasonField.requestFocus();
            }
            return;
        }

        this.currentAssessment = result.getValue();
        this.assessmentDetailArea.setText(this.currentAssessment.toString());
        showSuccess("Priority confirmed as "
                + this.currentAssessment.getConfirmedPriority().getDisplayName()
                + (this.currentAssessment.isOverridden()
                        ? " (override recorded in the audit log)." : "."));
        this.overrideReasonField.clear();
        handleRefresh();
    }

    private void configureSpinners() {
        this.threatSpinner.setValueFactory(factory(3));
        this.scaleSpinner.setValueFactory(factory(2));
        this.infrastructureSpinner.setValueFactory(factory(2));
        this.confidenceSpinner.setValueFactory(factory(4));
    }

    private SpinnerValueFactory<Integer> factory(int initial) {
        return new SpinnerValueFactory.IntegerSpinnerValueFactory(
                Assessment.MIN_FACTOR, Assessment.MAX_FACTOR, initial);
    }

    private DisasterReport selectedReport() {
        int index = this.reportList.getSelectionModel().getSelectedIndex();
        return index < 0 || index >= this.submittedReports
                .size() ? null : this.submittedReports.get(index);
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
