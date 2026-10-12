package au.edu.cqu.drs.protocol;

/**
 * Names of the arguments carried in a {@link Request}, shared by the client
 * and the server so both use the same spelling.
 *
 * <p>The comment on each constant gives the type of value expected.</p>
 */
public final class ArgumentKey {

    /** Account name ({@code String}). */
    public static final String USERNAME = "username";
    /** Plaintext password, sent only with LOGIN and REGISTER_USER. */
    public static final String PASSWORD = "password";
    /** Display name of a new account ({@code String}). */
    public static final String FULL_NAME = "fullName";
    /** Role of a new account ({@code UserRole}). */
    public static final String ROLE = "role";

    /** A whole report ({@code DisasterReport}). */
    public static final String REPORT = "report";
    /** A second report to compare with ({@code DisasterReport}). */
    public static final String OTHER_REPORT = "otherReport";
    /** Reference of a report ({@code String}). */
    public static final String REPORT_ID = "reportId";
    /** Status to list or decide ({@code ReportStatus}). */
    public static final String REPORT_STATUS = "reportStatus";
    /** Reason for a decision, override or response ({@code String}). */
    public static final String REASON = "reason";

    /** Reference of an incident ({@code String}). */
    public static final String INCIDENT_ID = "incidentId";
    /** Target status of an incident ({@code IncidentStatus}). */
    public static final String INCIDENT_STATUS = "incidentStatus";
    /** Outcome recorded on closure ({@code String}). */
    public static final String OUTCOME = "outcome";
    /** Risks remaining at closure ({@code String}). */
    public static final String RESIDUAL_RISKS = "residualRisks";
    /** A field update ({@code FieldUpdate}). */
    public static final String FIELD_UPDATE = "fieldUpdate";
    /** A priority ({@code Priority}). */
    public static final String PRIORITY = "priority";

    /** An assessment ({@code Assessment}). */
    public static final String ASSESSMENT = "assessment";
    /** Threat-to-life factor ({@code Integer}). */
    public static final String THREAT_TO_LIFE = "threatToLife";
    /** Scale factor ({@code Integer}). */
    public static final String SCALE = "scale";
    /** Infrastructure impact factor ({@code Integer}). */
    public static final String INFRASTRUCTURE_IMPACT = "infrastructureImpact";
    /** Severity score ({@code Integer}). */
    public static final String SCORE = "score";

    /** Disaster type ({@code DisasterType}). */
    public static final String DISASTER_TYPE = "disasterType";
    /** Department ({@code Department}). */
    public static final String DEPARTMENT = "department";
    /** Action requested of a department ({@code String}). */
    public static final String REQUESTED_ACTION = "requestedAction";
    /** Urgency of an assignment ({@code String}). */
    public static final String URGENCY = "urgency";
    /** Reference of an assignment ({@code String}). */
    public static final String ASSIGNMENT_ID = "assignmentId";
    /** A liaison's answer to an assignment ({@code AssignmentStatus}). */
    public static final String ASSIGNMENT_RESPONSE = "assignmentResponse";
    /** Estimated arrival time ({@code LocalDateTime}). */
    public static final String ETA = "eta";
    /** A resource allocation ({@code ResourceAllocation}). */
    public static final String ALLOCATION = "allocation";
    /** Whether the partner interfaces are available ({@code Boolean}). */
    public static final String AVAILABLE = "available";

    /** Entity type in the audit log ({@code String}). */
    public static final String ENTITY_TYPE = "entityType";
    /** Entity reference in the audit log ({@code String}). */
    public static final String ENTITY_ID = "entityId";

    /**
     * Prevents instances; the class only holds constants.
     */
    private ArgumentKey() {
    }

    /**
     * Describes this constants class.
     *
     * @return a short description
     */
    @Override
    public String toString() {
        return "ArgumentKey: names of request arguments";
    }
}
