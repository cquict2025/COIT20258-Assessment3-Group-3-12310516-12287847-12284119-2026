package au.edu.cqu.drs.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * An attributable progress entry in an incident timeline (UC-10, FR-10).
 * Every update carries the actor and the time because Assessment 1 required
 * the timeline to record provenance rather than anonymous status changes.
 */
public class FieldUpdate implements Serializable {

    /** Version of the serialised form sent between client and server. */
    private static final long serialVersionUID = 1L;

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

    /**
     * Creates an empty field update.
     */
    public FieldUpdate() {
    }

    /**
     * Creates a field update.
     *
     * @param updateId     the update id
     * @param incidentId   the incident id
     * @param submittedBy  the submitted by
     * @param submittedAt  the submitted at
     * @param progressNote the progress note
     */
    public FieldUpdate(String updateId, String incidentId, String submittedBy,
                       LocalDateTime submittedAt, String progressNote) {
        this.updateId = updateId;
        this.incidentId = incidentId;
        this.submittedBy = submittedBy;
        this.submittedAt = submittedAt;
        this.progressNote = progressNote;
    }

    /**
     * Returns the update id.
     *
     * @return the update id
     */
    public String getUpdateId() {
        return this.updateId;
    }

    /**
     * Sets the update id.
     *
     * @param updateId the update id
     */
    public void setUpdateId(String updateId) {
        this.updateId = updateId;
    }

    /**
     * Returns the incident id.
     *
     * @return the incident id
     */
    public String getIncidentId() {
        return this.incidentId;
    }

    /**
     * Sets the incident id.
     *
     * @param incidentId the incident id
     */
    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    /**
     * Returns the submitted by.
     *
     * @return the submitted by
     */
    public String getSubmittedBy() {
        return this.submittedBy;
    }

    /**
     * Sets the submitted by.
     *
     * @param submittedBy the submitted by
     */
    public void setSubmittedBy(String submittedBy) {
        this.submittedBy = submittedBy;
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
     * Returns the progress note.
     *
     * @return the progress note
     */
    public String getProgressNote() {
        return this.progressNote;
    }

    /**
     * Sets the progress note.
     *
     * @param progressNote the progress note
     */
    public void setProgressNote(String progressNote) {
        this.progressNote = progressNote;
    }

    /**
     * Returns the observed impact.
     *
     * @return the observed impact
     */
    public String getObservedImpact() {
        return this.observedImpact;
    }

    /**
     * Sets the observed impact.
     *
     * @param observedImpact the observed impact
     */
    public void setObservedImpact(String observedImpact) {
        this.observedImpact = observedImpact;
    }

    /**
     * Returns the resource needs.
     *
     * @return the resource needs
     */
    public String getResourceNeeds() {
        return this.resourceNeeds;
    }

    /**
     * Sets the resource needs.
     *
     * @param resourceNeeds the resource needs
     */
    public void setResourceNeeds(String resourceNeeds) {
        this.resourceNeeds = resourceNeeds;
    }

    /**
     * Returns the next review at.
     *
     * @return the next review at
     */
    public LocalDateTime getNextReviewAt() {
        return this.nextReviewAt;
    }

    /**
     * Sets the next review at.
     *
     * @param nextReviewAt the next review at
     */
    public void setNextReviewAt(LocalDateTime nextReviewAt) {
        this.nextReviewAt = nextReviewAt;
    }

    /**
     * Returns the incident version seen.
     *
     * @return the incident version seen
     */
    public int getIncidentVersionSeen() {
        return this.incidentVersionSeen;
    }

    /**
     * Sets the incident version seen.
     *
     * @param incidentVersionSeen the incident version seen
     */
    public void setIncidentVersionSeen(int incidentVersionSeen) {
        this.incidentVersionSeen = incidentVersionSeen;
    }

    /**
     * Describes the update: its incident, author, time, progress note
     * and any resource needs.
     *
     * @return a one-line summary of this update
     */
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
