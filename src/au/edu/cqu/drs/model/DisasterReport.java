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

    /**
     * Creates an empty disaster report.
     */
    public DisasterReport() {
        this.status = ReportStatus.DRAFT;
        this.location = new Location();
    }

    /**
     * Creates a disaster report.
     *
     * @param reportId     the report id
     * @param disasterType the disaster type
     * @param location     the location
     * @param observedAt   the observed at
     * @param description  the description
     */
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

    /**
     * Returns the report id.
     *
     * @return the report id
     */
    public String getReportId() {
        return this.reportId;
    }

    /**
     * Sets the report id.
     *
     * @param reportId the report id
     */
    public void setReportId(String reportId) {
        this.reportId = reportId;
    }

    /**
     * Returns the disaster type.
     *
     * @return the disaster type
     */
    public DisasterType getDisasterType() {
        return this.disasterType;
    }

    /**
     * Sets the disaster type.
     *
     * @param disasterType the disaster type
     */
    public void setDisasterType(DisasterType disasterType) {
        this.disasterType = disasterType;
    }

    /**
     * Returns the other type description.
     *
     * @return the other type description
     */
    public String getOtherTypeDescription() {
        return this.otherTypeDescription;
    }

    /**
     * Sets the other type description.
     *
     * @param otherTypeDescription the other type description
     */
    public void setOtherTypeDescription(String otherTypeDescription) {
        this.otherTypeDescription = otherTypeDescription;
    }

    /**
     * Returns the location.
     *
     * @return the location
     */
    public Location getLocation() {
        return this.location;
    }

    /**
     * Sets the location.
     *
     * @param location the location
     */
    public void setLocation(Location location) {
        this.location = location;
    }

    /**
     * Returns the observed at.
     *
     * @return the observed at
     */
    public LocalDateTime getObservedAt() {
        return this.observedAt;
    }

    /**
     * Sets the observed at.
     *
     * @param observedAt the observed at
     */
    public void setObservedAt(LocalDateTime observedAt) {
        this.observedAt = observedAt;
    }

    /**
     * Returns the submitted at.
     *
     * @return the submitted at
     */
    public LocalDateTime getSubmittedAt() {
        return this.submittedAt;
    }

    /**
     * Sets the submitted at.
     *
     * @param submittedAt the submitted at
     */
    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    /**
     * Returns the description.
     *
     * @return the description
     */
    public String getDescription() {
        return this.description;
    }

    /**
     * Sets the description.
     *
     * @param description the description
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Returns the immediate risks.
     *
     * @return the immediate risks
     */
    public String getImmediateRisks() {
        return this.immediateRisks;
    }

    /**
     * Sets the immediate risks.
     *
     * @param immediateRisks the immediate risks
     */
    public void setImmediateRisks(String immediateRisks) {
        this.immediateRisks = immediateRisks;
    }

    /**
     * Returns the reporter name.
     *
     * @return the reporter name
     */
    public String getReporterName() {
        return this.reporterName;
    }

    /**
     * Sets the reporter name.
     *
     * @param reporterName the reporter name
     */
    public void setReporterName(String reporterName) {
        this.reporterName = reporterName;
    }

    /**
     * Returns the reporter contact.
     *
     * @return the reporter contact
     */
    public String getReporterContact() {
        return this.reporterContact;
    }

    /**
     * Sets the reporter contact.
     *
     * @param reporterContact the reporter contact
     */
    public void setReporterContact(String reporterContact) {
        this.reporterContact = reporterContact;
    }

    /**
     * Returns the status.
     *
     * @return the status
     */
    public ReportStatus getStatus() {
        return this.status;
    }

    /**
     * Sets the status.
     *
     * @param status the status
     */
    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    /**
     * Returns the decision reason.
     *
     * @return the decision reason
     */
    public String getDecisionReason() {
        return this.decisionReason;
    }

    /**
     * Sets the decision reason.
     *
     * @param decisionReason the decision reason
     */
    public void setDecisionReason(String decisionReason) {
        this.decisionReason = decisionReason;
    }

    /**
     * Returns the linked incident id.
     *
     * @return the linked incident id
     */
    public String getLinkedIncidentId() {
        return this.linkedIncidentId;
    }

    /**
     * Sets the linked incident id.
     *
     * @param linkedIncidentId the linked incident id
     */
    public void setLinkedIncidentId(String linkedIncidentId) {
        this.linkedIncidentId = linkedIncidentId;
    }

    /**
     * Returns the label used on screen, expanding the free-text category
     * when OTHER is chosen.
     *
     * @return the disaster type label
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
     * Summarises the report. Reporter contact is restricted data (NFR-05),
     * so it is deliberately absent from this summary; only an authorised
     * coordinator screen may display it.
     *
     * @return the reference, type, suburb, observed time and status
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
