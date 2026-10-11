package au.edu.cqu.drs.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * An attributable progress entry in an incident timeline (UC-10, FR-10).
 * Every update carries the actor and the time because Assessment 1 required
 * the timeline to record provenance rather than anonymous status changes.
 */
public class FieldUpdate {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private String updateId;
    private String incidentId;
    private String submittedBy;
    private LocalDateTime submittedAt;
    private String progressNote;
    private String observedImpact;
    private String resourceNeeds;
    private LocalDateTime nextReviewAt;
    private int incidentVersionSeen;

    public FieldUpdate() {
    }

    public FieldUpdate(String updateId, String incidentId, String submittedBy,
                       LocalDateTime submittedAt, String progressNote) {
        this.updateId = updateId;
        this.incidentId = incidentId;
        this.submittedBy = submittedBy;
        this.submittedAt = submittedAt;
        this.progressNote = progressNote;
    }

    public String getUpdateId() {
        return this.updateId;
    }

    public void setUpdateId(String updateId) {
        this.updateId = updateId;
    }

    public String getIncidentId() {
        return this.incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public String getSubmittedBy() {
        return this.submittedBy;
    }

    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
    }

    public LocalDateTime getSubmittedAt() {
        return this.submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getProgressNote() {
        return this.progressNote;
    }

    public void setProgressNote(String progressNote) {
        this.progressNote = progressNote;
    }

    public String getObservedImpact() {
        return this.observedImpact;
    }

    public void setObservedImpact(String observedImpact) {
        this.observedImpact = observedImpact;
    }

    public String getResourceNeeds() {
        return this.resourceNeeds;
    }

    public void setResourceNeeds(String resourceNeeds) {
        this.resourceNeeds = resourceNeeds;
    }

    public LocalDateTime getNextReviewAt() {
        return this.nextReviewAt;
    }

    public void setNextReviewAt(LocalDateTime nextReviewAt) {
        this.nextReviewAt = nextReviewAt;
    }

    public int getIncidentVersionSeen() {
        return this.incidentVersionSeen;
    }

    public void setIncidentVersionSeen(int incidentVersionSeen) {
        this.incidentVersionSeen = incidentVersionSeen;
    }

    @Override
    public String toString() {
        return String.format("Update %s | incident %s | by %s at %s | %s%s",
                this.updateId == null ? "unsaved" : this.updateId,
                this.incidentId,
                this.submittedBy,
                this.submittedAt == null ? "unknown time" : this.submittedAt
                        .format(TIMESTAMP_FORMAT),
                this.progressNote,
                this.resourceNeeds == null || this.resourceNeeds.isBlank()
                        ? "" : " | needs: " + this.resourceNeeds);
    }
}
