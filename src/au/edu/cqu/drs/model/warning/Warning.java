package au.edu.cqu.drs.model.warning;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * One version of a public warning about an incident.
 *
 * <p>A warning is never edited in place. Issuing it stores version 1;
 * every update or cancellation stores a new version with the same code, so
 * the history shows exactly what the public was told and when.</p>
 */
public class Warning implements Serializable {

    /** Version of the serialised form sent between client and server. */
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private String warningId;
    private int version;
    private String incidentId;
    private WarningLevel level;
    private String affectedArea;
    private String message;
    private String recommendedAction;
    private EvacuationCentre evacuationCentre;
    private WarningStatus status;
    private String issuedBy;
    private LocalDateTime issuedAt;
    private LocalDateTime expiresAt;
    private ArrayList<WarningAcknowledgement> acknowledgements;

    /**
     * Creates an empty warning, for forms and the repositories.
     */
    public Warning() {
        this.acknowledgements = new ArrayList<>();
    }

    /**
     * Creates the content of a new warning, before it is issued.
     *
     * @param incidentId   the incident it is about
     * @param level        how serious it is
     * @param affectedArea the suburbs or area affected
     * @param message      what is happening
     * @param expiresAt    when it stops being in force
     */
    public Warning(String incidentId, WarningLevel level, String affectedArea,
            String message, LocalDateTime expiresAt) {
        this();
        this.incidentId = incidentId;
        this.level = level;
        this.affectedArea = affectedArea;
        this.message = message;
        this.expiresAt = expiresAt;
    }

    /**
     * Copies a warning, used to make its next version.
     *
     * @return a copy without the acknowledgements
     */
    public Warning copy() {
        Warning copy = new Warning(this.incidentId, this.level,
                this.affectedArea, this.message, this.expiresAt);
        copy.warningId = this.warningId;
        copy.version = this.version;
        copy.recommendedAction = this.recommendedAction;
        copy.evacuationCentre = this.evacuationCentre == null ? null
                : new EvacuationCentre(this.evacuationCentre.getName(),
                        this.evacuationCentre.getCapacity());
        copy.status = this.status;
        copy.issuedBy = this.issuedBy;
        copy.issuedAt = this.issuedAt;
        return copy;
    }

    /**
     * Tells whether the warning is in force at a time: active and not yet
     * expired.
     *
     * @param now the time to check
     * @return true if the public should see it
     */
    public boolean isInForceAt(LocalDateTime now) {
        return this.status == WarningStatus.ACTIVE
                && (this.expiresAt == null || this.expiresAt.isAfter(now));
    }

    /**
     * Returns the warning's code.
     *
     * @return the code, for example WRN-0001, or null before it is issued
     */
    public String getWarningId() {
        return this.warningId;
    }

    /**
     * Sets the warning's code.
     *
     * @param warningId the code
     */
    public void setWarningId(String warningId) {
        this.warningId = warningId;
    }

    /**
     * Returns the version number, starting at 1.
     *
     * @return the version
     */
    public int getVersion() {
        return this.version;
    }

    /**
     * Sets the version number.
     *
     * @param version the version
     */
    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * Returns the incident the warning is about.
     *
     * @return the incident code
     */
    public String getIncidentId() {
        return this.incidentId;
    }

    /**
     * Sets the incident the warning is about.
     *
     * @param incidentId the incident code
     */
    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    /**
     * Returns the warning level.
     *
     * @return the level
     */
    public WarningLevel getLevel() {
        return this.level;
    }

    /**
     * Sets the warning level.
     *
     * @param level the level
     */
    public void setLevel(WarningLevel level) {
        this.level = level;
    }

    /**
     * Returns the area affected.
     *
     * @return the area
     */
    public String getAffectedArea() {
        return this.affectedArea;
    }

    /**
     * Sets the area affected.
     *
     * @param affectedArea the area
     */
    public void setAffectedArea(String affectedArea) {
        this.affectedArea = affectedArea;
    }

    /**
     * Returns what the public is told is happening.
     *
     * @return the message
     */
    public String getMessage() {
        return this.message;
    }

    /**
     * Sets what the public is told is happening.
     *
     * @param message the message
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Returns what the public should do.
     *
     * @return the recommended action, may be null
     */
    public String getRecommendedAction() {
        return this.recommendedAction;
    }

    /**
     * Sets what the public should do.
     *
     * @param recommendedAction the recommended action
     */
    public void setRecommendedAction(String recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    /**
     * Returns the evacuation centre, if there is one.
     *
     * @return the centre, or null
     */
    public EvacuationCentre getEvacuationCentre() {
        return this.evacuationCentre;
    }

    /**
     * Sets the evacuation centre.
     *
     * @param evacuationCentre the centre, or null for none
     */
    public void setEvacuationCentre(EvacuationCentre evacuationCentre) {
        this.evacuationCentre = evacuationCentre;
    }

    /**
     * Returns whether the warning is active or cancelled.
     *
     * @return the status
     */
    public WarningStatus getStatus() {
        return this.status;
    }

    /**
     * Sets whether the warning is active or cancelled.
     *
     * @param status the status
     */
    public void setStatus(WarningStatus status) {
        this.status = status;
    }

    /**
     * Returns the coordinator who issued this version.
     *
     * @return the user name
     */
    public String getIssuedBy() {
        return this.issuedBy;
    }

    /**
     * Sets the coordinator who issued this version.
     *
     * @param issuedBy the user name
     */
    public void setIssuedBy(String issuedBy) {
        this.issuedBy = issuedBy;
    }

    /**
     * Returns when this version was issued.
     *
     * @return the time
     */
    public LocalDateTime getIssuedAt() {
        return this.issuedAt;
    }

    /**
     * Sets when this version was issued.
     *
     * @param issuedAt the time
     */
    public void setIssuedAt(LocalDateTime issuedAt) {
        this.issuedAt = issuedAt;
    }

    /**
     * Returns when the warning stops being in force.
     *
     * @return the expiry time
     */
    public LocalDateTime getExpiresAt() {
        return this.expiresAt;
    }

    /**
     * Sets when the warning stops being in force.
     *
     * @param expiresAt the expiry time
     */
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    /**
     * Returns the departments' acknowledgements, filled in for staff lists.
     *
     * @return the acknowledgements
     */
    public List<WarningAcknowledgement> getAcknowledgements() {
        return this.acknowledgements;
    }

    /**
     * Replaces the acknowledgements.
     *
     * @param acknowledgements the acknowledgements
     */
    public void setAcknowledgements(
            List<WarningAcknowledgement> acknowledgements) {
        this.acknowledgements = acknowledgements == null ? new ArrayList<>()
                : new ArrayList<>(acknowledgements);
    }

    /**
     * Describes the warning.
     *
     * @return code, version, level, area, status and expiry
     */
    @Override
    public String toString() {
        return String.format("%s v%d | %s | %s | %s | expires %s",
                this.warningId == null ? "unissued" : this.warningId,
                this.version,
                this.level == null ? "no level" : this.level.getDisplayName(),
                this.affectedArea,
                this.status == null ? "draft" : this.status.getDisplayName(),
                this.expiresAt == null ? "never"
                        : this.expiresAt.format(TIME_FORMAT));
    }
}
