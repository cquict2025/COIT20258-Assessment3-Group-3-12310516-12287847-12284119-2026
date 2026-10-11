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

    public Incident() {
        this.status = IncidentStatus.ACTIVE;
        this.priority = Priority.P4;
        this.version = 1;
        this.location = new Location();
    }

    public Incident(String incidentId, String title, DisasterType disasterType,
                    Location location, LocalDateTime createdAt) {
        this();
        this.incidentId = incidentId;
        this.title = title;
        this.disasterType = disasterType;
        this.location = location;
        this.createdAt = createdAt;
    }

    public String getIncidentId() {
        return this.incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public DisasterType getDisasterType() {
        return this.disasterType;
    }

    public void setDisasterType(DisasterType disasterType) {
        this.disasterType = disasterType;
    }

    public Location getLocation() {
        return this.location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public IncidentStatus getStatus() {
        return this.status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public Priority getPriority() {
        return this.priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public LocalDateTime getCreatedAt() {
        return this.createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getFirstResponseAt() {
        return this.firstResponseAt;
    }

    public void setFirstResponseAt(LocalDateTime firstResponseAt) {
        this.firstResponseAt = firstResponseAt;
    }

    public LocalDateTime getClosedAt() {
        return this.closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public String getClosureOutcome() {
        return this.closureOutcome;
    }

    public void setClosureOutcome(String closureOutcome) {
        this.closureOutcome = closureOutcome;
    }

    public String getResidualRisks() {
        return this.residualRisks;
    }

    public void setResidualRisks(String residualRisks) {
        this.residualRisks = residualRisks;
    }

    public int getVersion() {
        return this.version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public List<String> getSourceReportIds() {
        return List.copyOf(this.sourceReportIds);
    }

    public void addSourceReportId(String reportId) {
        if (reportId != null && !this.sourceReportIds.contains(reportId)) {
            this.sourceReportIds.add(reportId);
        }
    }

    public List<DepartmentAssignment> getAssignments() {
        return List.copyOf(this.assignments);
    }

    public void addAssignment(DepartmentAssignment assignment) {
        if (assignment != null) {
            this.assignments.add(assignment);
        }
    }

    public List<FieldUpdate> getFieldUpdates() {
        return List.copyOf(this.fieldUpdates);
    }

    public void addFieldUpdate(FieldUpdate fieldUpdate) {
        if (fieldUpdate != null) {
            this.fieldUpdates.add(fieldUpdate);
        }
    }

    /**
     * True when every assignment has reached a resolved state (precondition of
     * UC-13).
     */
    public boolean hasUnresolvedAssignments() {
        return this.assignments.stream().anyMatch(a ->
                !a.getStatus().isResolved());
    }

    /** Increments the optimistic-locking version after a committed change. */
    public void incrementVersion() {
        this.version++;
    }

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
