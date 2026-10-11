package au.edu.cqu.drs.controller;

import au.edu.cqu.drs.AppContext;
import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.DuplicateCandidate;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.service.ServiceResult;
import au.edu.cqu.drs.util.TimeParser;
import au.edu.cqu.drs.util.ViewLoader;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Handles the public report form (UC-02, FR-02) and shows possible duplicates
 * after a successful submission (UC-04, creative feature 2).
 *
 * <p>Invalid input is never discarded: the form keeps every value the user
 * typed and points at the field that needs attention, which is the behaviour
 * Assessment 1 promised for reporters working under stress.</p>
 */
public class ReportFormController {

    private final AppContext context;
    private final Parent view;

    @FXML
    private ComboBox<DisasterType> typeCombo;
    @FXML
    private TextField otherTypeField;
    @FXML
    private TextField addressField;
    @FXML
    private TextField suburbField;
    @FXML
    private TextField latitudeField;
    @FXML
    private TextField longitudeField;
    @FXML
    private DatePicker observedDatePicker;
    @FXML
    private TextField observedTimeField;
    @FXML
    private TextField reporterNameField;
    @FXML
    private TextField reporterContactField;
    @FXML
    private TextArea descriptionArea;
    @FXML
    private TextArea risksArea;
    @FXML
    private Label messageLabel;
    @FXML
    private TitledPane duplicatePane;
    @FXML
    private ListView<String> duplicateList;

    public ReportFormController(AppContext context) {
        this.context = context;
        this.view = ViewLoader.load("report-form.fxml", this);
        this.typeCombo.setItems(FXCollections
                .observableArrayList(DisasterType.values()));
        this.typeCombo.getSelectionModel().select(DisasterType.FIRE);
        this.observedDatePicker.setValue(LocalDate.now());
    }

    public Parent getView() {
        return this.view;
    }

    @FXML
    private void handleSubmit() {
        this.messageLabel.getStyleClass().setAll("error-label");
        hideDuplicates();

        Optional<LocalDateTime> observedAt =
                TimeParser.parse(this.observedTimeField.getText(),
                                 this.observedDatePicker.getValue());
        if (observedAt.isEmpty()) {
            this.messageLabel.setText("Enter the observed time as HH:mm, for "
                    + "example 14:30.");
            this.observedTimeField.requestFocus();
            return;
        }

        Double latitude = parseCoordinate(this.latitudeField.getText());
        Double longitude = parseCoordinate(this.longitudeField.getText());
        if (latitude == null || longitude == null) {
            this.messageLabel
                    .setText("Latitude and longitude must be numbers, for "
                    + "example -37.8136.");
            (latitude == null ? this.latitudeField :
                    this.longitudeField).requestFocus();
            return;
        }

        DisasterReport report = buildReport(observedAt.get(), latitude,
                                            longitude);
        ServiceResult<DisasterReport> result = this.context.getReportService()
                .submitReport(report);

        if (result.isFailure()) {
            this.messageLabel.setText(result.getMessage());
            focusField(result.getAffectedField());
            return;
        }

        DisasterReport saved = result.getValue();
        this.messageLabel.getStyleClass().setAll("success-label");
        this.messageLabel.setText("Report " + saved.getReportId()
                + " submitted. Thank you - response staff have been notified.");
        showDuplicates(saved);
        clearForm();
    }

    @FXML
    private void handleSaveDraft() {
        this.messageLabel.getStyleClass().setAll("hint-label");
        DisasterReport report = buildReport(
                TimeParser.parse(this.observedTimeField.getText(),
                                 this.observedDatePicker.getValue())
                        .orElse(null),
                parseCoordinate(this.latitudeField.getText()) == null
                        ? 0 : parseCoordinate(this.latitudeField.getText()),
                parseCoordinate(this.longitudeField.getText()) == null
                        ? 0 : parseCoordinate(this.longitudeField.getText()));
        ServiceResult<DisasterReport> result = this.context.getReportService()
                .saveDraft(report);
        this.messageLabel.setText(result.isSuccess()
                ? "Draft " + result.getValue()
                        .getReportId() + " saved. You can complete it later."
                : result.getMessage());
    }

    @FXML
    private void handleClear() {
        clearForm();
        this.messageLabel.getStyleClass().setAll("hint-label");
        this.messageLabel.setText("Form cleared.");
        hideDuplicates();
    }

    private DisasterReport buildReport(LocalDateTime observedAt,
                                       double latitude, double longitude) {
        DisasterReport report = new DisasterReport();
        report.setDisasterType(this.typeCombo.getValue());
        report.setOtherTypeDescription(this.otherTypeField.getText());
        report.setLocation(new Location(this.addressField.getText(),
                this.suburbField.getText(),
                latitude, longitude));
        report.setObservedAt(observedAt);
        report.setDescription(this.descriptionArea.getText());
        report.setImmediateRisks(this.risksArea.getText());
        report.setReporterName(this.reporterNameField.getText());
        report.setReporterContact(this.reporterContactField.getText());
        return report;
    }

    private void showDuplicates(DisasterReport saved) {
        List<DuplicateCandidate> candidates =
                this.context.getDuplicateDetectionService()
                        .findCandidates(saved);
        if (candidates.isEmpty()) {
            return;
        }
        this.duplicateList.setItems(FXCollections.observableArrayList(
                candidates.stream()
                        .map(c -> String.format("%s - similarity %.0f%% (%s)",
                                c.getCandidateReport().getReportId(),
                                c.getSimilarityScore() * 100,
                                c.getMatchReason()))
                        .toList()));
        this.duplicatePane.setVisible(true);
        this.duplicatePane.setManaged(true);
        this.duplicatePane.setExpanded(true);
    }

    private void hideDuplicates() {
        this.duplicatePane.setVisible(false);
        this.duplicatePane.setManaged(false);
    }

    private Double parseCoordinate(String text) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }
        try {
            return Double.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void focusField(String affectedField) {
        if (affectedField == null) {
            return;
        }
        switch (affectedField) {
            case "disasterType" -> this.typeCombo.requestFocus();
            case "otherTypeDescription" -> this.otherTypeField.requestFocus();
            case "suburb" -> this.suburbField.requestFocus();
            case "latitude" -> this.latitudeField.requestFocus();
            case "longitude" -> this.longitudeField.requestFocus();
            case "observedAt" -> this.observedTimeField.requestFocus();
            case "description" -> this.descriptionArea.requestFocus();
            default -> { /* No specific field to focus. */ }
        }
    }

    private void clearForm() {
        this.otherTypeField.clear();
        this.addressField.clear();
        this.suburbField.clear();
        this.latitudeField.clear();
        this.longitudeField.clear();
        this.observedTimeField.clear();
        this.reporterNameField.clear();
        this.reporterContactField.clear();
        this.descriptionArea.clear();
        this.risksArea.clear();
        this.observedDatePicker.setValue(LocalDate.now());
        this.typeCombo.getSelectionModel().select(DisasterType.FIRE);
    }
}
