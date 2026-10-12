package au.edu.cqu.drs.model.warning;

import au.edu.cqu.drs.model.Department;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A department's confirmation that it has seen a warning, given by its
 * liaison. One per department per warning.
 */
public class WarningAcknowledgement implements Serializable {

    /** Version of the serialised form sent between client and server. */
    private static final long serialVersionUID = 1L;

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private String warningId;
    private Department department;
    private String acknowledgedBy;
    private LocalDateTime acknowledgedAt;

    /**
     * Creates an empty acknowledgement, for the repositories.
     */
    public WarningAcknowledgement() {
    }

    /**
     * Creates an acknowledgement.
     *
     * @param warningId      the warning's code
     * @param department     the department acknowledging
     * @param acknowledgedBy the liaison's user name
     * @param acknowledgedAt when it was acknowledged
     */
    public WarningAcknowledgement(String warningId, Department department,
            String acknowledgedBy, LocalDateTime acknowledgedAt) {
        this.warningId = warningId;
        this.department = department;
        this.acknowledgedBy = acknowledgedBy;
        this.acknowledgedAt = acknowledgedAt;
    }

    /**
     * Returns the warning's code.
     *
     * @return the code, for example WRN-0001
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
     * Returns the department that acknowledged.
     *
     * @return the department
     */
    public Department getDepartment() {
        return this.department;
    }

    /**
     * Sets the department that acknowledged.
     *
     * @param department the department
     */
    public void setDepartment(Department department) {
        this.department = department;
    }

    /**
     * Returns the liaison who acknowledged.
     *
     * @return the user name
     */
    public String getAcknowledgedBy() {
        return this.acknowledgedBy;
    }

    /**
     * Sets the liaison who acknowledged.
     *
     * @param acknowledgedBy the user name
     */
    public void setAcknowledgedBy(String acknowledgedBy) {
        this.acknowledgedBy = acknowledgedBy;
    }

    /**
     * Returns when the warning was acknowledged.
     *
     * @return the time
     */
    public LocalDateTime getAcknowledgedAt() {
        return this.acknowledgedAt;
    }

    /**
     * Sets when the warning was acknowledged.
     *
     * @param acknowledgedAt the time
     */
    public void setAcknowledgedAt(LocalDateTime acknowledgedAt) {
        this.acknowledgedAt = acknowledgedAt;
    }

    /**
     * Describes the acknowledgement.
     *
     * @return the department, person and time
     */
    @Override
    public String toString() {
        return String.format("%s acknowledged %s (%s, %s)",
                this.department == null ? "unknown department"
                        : this.department.getDisplayName(),
                this.warningId, this.acknowledgedBy,
                this.acknowledgedAt == null ? "unknown time"
                        : this.acknowledgedAt.format(TIME_FORMAT));
    }
}
