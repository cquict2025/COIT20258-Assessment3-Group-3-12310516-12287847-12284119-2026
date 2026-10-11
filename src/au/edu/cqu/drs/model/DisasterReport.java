package au.edu.cqu.drs.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A single observation submitted by a member of the public or entered by a
 * coordinator (UC-02, FR-02). Several reports may support one incident, so a
 * report keeps its own identity and never becomes the incident itself.
 */
public class DisasterReport implements Serializable {

    /** Version of the serialised form sent between client and server. */
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private String reportId;
    private DisasterType disasterType;
    private String otherTypeDescription;
    private Location location;
    private LocalDateTime observedAt;
    private LocalDateTime submittedAt;
    private String description;
    private String immediateRisks;
    private String reporterName;
    private String reporterContact;
    private ReportStatus status;
    private String decisionReason;
    private String linkedIncidentId;

    public DisasterReport() {
        this.status = ReportStatus.DRAFT;
        this.location = new Location();
    }

    public DisasterReport(String reportId, DisasterType disasterType,
                          Location location, 
                          LocalDateTime observedAt, String description) {
        this();
        this.reportId = reportId;
        this.disasterType = disasterType;
        this.location = location;
        this.observedAt = observedAt;
        this.description = description;
    }

    public String getReportId() {
        return this.reportId;
    }

    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    public DisasterType getDisasterType() {
        return this.disasterType;
    }

    public void setDisasterType(DisasterType disasterType) {
        this.disasterType = disasterType;
    }

    public String getOtherTypeDescription() {
        return this.otherTypeDescription;
    }

    public void setOtherTypeDescription(String otherTypeDescription) {
        this.otherTypeDescription = otherTypeDescription;
    }

    public Location getLocation() {
        return this.location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public LocalDateTime getObservedAt() {
        return this.observedAt;
    }

    public void setObservedAt(LocalDateTime observedAt) {
        this.observedAt = observedAt;
    }

    public LocalDateTime getSubmittedAt() {
        return this.submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImmediateRisks() {
        return this.immediateRisks;
    }

    public void setImmediateRisks(String immediateRisks) {
        this.immediateRisks = immediateRisks;
    }

    public String getReporterName() {
        return this.reporterName;
    }

    public void setReporterName(String reporterName) {
        this.reporterName = reporterName;
    }

    public String getReporterContact() {
        return this.reporterContact;
    }

    public void setReporterContact(String reporterContact) {
        this.reporterContact = reporterContact;
    }

    public ReportStatus getStatus() {
        return this.status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public String getDecisionReason() {
        return this.decisionReason;
    }

    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    public String getLinkedIncidentId() {
        return this.linkedIncidentId;
    }

    public void setLinkedIncidentId(String linkedIncidentId) {
        this.linkedIncidentId = linkedIncidentId;
    }

    /**
     * Label used on screen, expanding the free-text category when OTHER is
     * chosen.
     */
    public String getTypeLabel() {
        if (this.disasterType == DisasterType.OTHER && this.otherTypeDescription
                != null
                && !this.otherTypeDescription.isBlank()) {
            return this.otherTypeDescription;
        }
        return this.disasterType == null ? "Unspecified" : this.disasterType
                .getDisplayName();
    }

    /**
     * Reporter contact is restricted data (NFR-05), so it is deliberately
     * absent
     * from this summary; only an authorised coordinator screen may display it.
     */
    @Override
    public String toString() {
        return String.format("Report %s | %s | %s | observed %s | %s",
                this.reportId == null ? "unsaved" : this.reportId,
                getTypeLabel(),
                this.location == null ? "location unknown" :
                        this.location.getSuburb(),
                this.observedAt == null ? "time unknown" : this.observedAt
                        .format(TIMESTAMP_FORMAT),
                this.status == null ? "no status" :
                        this.status.getDisplayName());
    }
}
