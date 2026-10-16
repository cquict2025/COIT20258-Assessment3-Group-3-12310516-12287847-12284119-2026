package au.edu.cqu.drs.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The authoritative operational record created from one or more validated
 * reports (UC-06, FR-06). The incident owns its assignments, field updates and
 * assessment history, which is why those collections are created here and
 * returned as unmodifiable views.
 */
public class Incident implements Serializable {

    /** Version of the serialised form sent between client and server. */
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private String incidentId;
    private String title;
    private DisasterType disasterType;
    private Location location;
    private IncidentStatus status;
    private Priority priority;
    private LocalDateTime createdAt;
    private LocalDateTime firstResponseAt;
    private LocalDateTime closedAt;
    private String closureOutcome;
    private String residualRisks;
    private int version;

    private final List<String> sourceReportIds = new ArrayList<>();
    private final List<DepartmentAssignment> assignments = new ArrayList<>();
    private final List<FieldUpdate> fieldUpdates = new ArrayList<>();

    /**
     * Creates an empty incident.
     */
    public Incident() {
        this.status = IncidentStatus.ACTIVE;
        this.priority = Priority.P4;
        this.version = 1;
        this.location = new Location();
    }

    /**
     * Creates an incident.
     *
     * @param incidentId   the incident id
     * @param title        the title
     * @param disasterType the disaster type
     * @param location     the location
     * @param createdAt    the created at
     */
    public Incident(String incidentId, String title, DisasterType disasterType,
                    Location location, LocalDateTime createdAt) {
        this();
        this.incidentId = incidentId;
        this.title = title;
        this.disasterType = disasterType;
        this.location = location;
        this.createdAt = createdAt;
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
     * Returns the title.
     *
     * @return the title
     */
    public String getTitle() {
        return this.title;
    }

    /**
     * Sets the title.
     *
     * @param title the title
     */
    public void setTitle(String title) {
        this.title = title;
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
     * Returns the status.
     *
     * @return the status
     */
    public IncidentStatus getStatus() {
        return this.status;
    }

    /**
     * Sets the status.
     *
     * @param status the status
     */
    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    /**
     * Returns the priority.
     *
     * @return the priority
     */
    public Priority getPriority() {
        return this.priority;
    }

    /**
     * Sets the priority.
     *
     * @param priority the priority
     */
    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    /**
     * Returns the created at.
     *
     * @return the created at
     */
    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    /**
     * Sets the created at.
     *
     * @param createdAt the created at
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * Returns the first response at.
     *
     * @return the first response at
     */
    public LocalDateTime getFirstResponseAt() {
        return this.firstResponseAt;
    }

    /**
     * Sets the first response at.
     *
     * @param firstResponseAt the first response at
     */
    public void setFirstResponseAt(LocalDateTime firstResponseAt) {
        this.firstResponseAt = firstResponseAt;
    }

    /**
     * Returns the closed at.
     *
     * @return the closed at
     */
    public LocalDateTime getClosedAt() {
        return this.closedAt;
    }

    /**
     * Sets the closed at.
     *
     * @param closedAt the closed at
     */
    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    /**
     * Returns the closure outcome.
     *
     * @return the closure outcome
     */
    public String getClosureOutcome() {
        return this.closureOutcome;
    }

    /**
     * Sets the closure outcome.
     *
     * @param closureOutcome the closure outcome
     */
    public void setClosureOutcome(String closureOutcome) {
        this.closureOutcome = closureOutcome;
    }

    /**
     * Returns the residual risks.
     *
     * @return the residual risks
     */
    public String getResidualRisks() {
        return this.residualRisks;
    }

    /**
     * Sets the residual risks.
     *
     * @param residualRisks the residual risks
     */
    public void setResidualRisks(String residualRisks) {
        this.residualRisks = residualRisks;
    }

    /**
     * Returns the version.
     *
     * @return the version
     */
    public int getVersion() {
        return this.version;
    }

    /**
     * Sets the version.
     *
     * @param version the version
     */
    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * Returns the source report ids.
     *
     * @return the source report ids
     */
    public List<String> getSourceReportIds() {
        return List.copyOf(this.sourceReportIds);
    }

    /**
     * Links a source report to this incident. A null or already linked
     * report ID is ignored.
     *
     * @param reportId the ID of the report to link
     */
    public void addSourceReportId(String reportId) {
        if (reportId != null && !this.sourceReportIds.contains(reportId)) {
            this.sourceReportIds.add(reportId);
        }
    }

    /**
     * Returns the assignments.
     *
     * @return the assignments
     */
    public List<DepartmentAssignment> getAssignments() {
        return List.copyOf(this.assignments);
    }

    /**
     * Adds a departmental assignment to this incident. A null assignment is
     * ignored.
     *
     * @param assignment the assignment to add
     */
    public void addAssignment(DepartmentAssignment assignment) {
        if (assignment != null) {
            this.assignments.add(assignment);
        }
    }

    /**
     * Returns the field updates.
     *
     * @return the field updates
     */
    public List<FieldUpdate> getFieldUpdates() {
        return List.copyOf(this.fieldUpdates);
    }

    /**
     * Adds a field update to this incident. A null update is ignored.
     *
     * @param fieldUpdate the update to add
     */
    public void addFieldUpdate(FieldUpdate fieldUpdate) {
        if (fieldUpdate != null) {
            this.fieldUpdates.add(fieldUpdate);
        }
    }

    /**
     * Tells whether any assignment has not yet reached a resolved state;
     * an incident can only be closed when none remain (UC-13).
     *
     * @return {@code true} if at least one assignment is unresolved
     */
    public boolean hasUnresolvedAssignments() {
        return this.assignments.stream().anyMatch(a ->
                !a.getStatus().isResolved());
    }

    /** Increments the optimistic-locking version after a committed change. */
    public void incrementVersion() {
        this.version++;
    }

    /**
     * Describes the incident: its title, type, priority, status,
     * creation time and number of assignments.
     *
     * @return a one-line summary of this incident
     */
    @Override
    public String toString() {
        return String.format("Incident %s | %s | %s | %s | %s | created %s | "
                + "%d assignment(s)",
                             
                             
                             
                             
                this.incidentId == null ? "unsaved" : this.incidentId,
                this.title == null ? "untitled" : this.title,
                this.disasterType == null ? "type unknown" : this.disasterType
                        .getDisplayName(),
                this.priority == null ? "unprioritised" :
                        this.priority.getDisplayName(),
                this.status == null ? "no status" :
                        this.status.getDisplayName(),
                this.createdAt == null ? "unknown" : this.createdAt
                        .format(TIMESTAMP_FORMAT),
                this.assignments.size());
    }
}
