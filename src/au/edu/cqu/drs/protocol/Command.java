package au.edu.cqu.drs.protocol;

/**
 * Every operation a client can ask the server to perform.
 *
 * <p>There is one constant for each public operation of the services in
 * {@code au.edu.cqu.drs.service}, plus the connection commands, the
 * partner-outage switch and the warning and import commands handled by
 * modules. Two service methods are deliberately absent because only the
 * server may call them: {@code AuditService.record}, so a client can never
 * write its own audit entries, and {@code AuthenticationService.isAuthorised},
 * because permission checks are made on the server.</p>
 *
 * <p>A command that needs no login can be sent without a session token; all
 * other commands are refused unless the token belongs to a signed-in
 * user.</p>
 */
public enum Command {

    /** Checks that the server is reachable. */
    PING(false),
    /** Signs in with a username and password. */
    LOGIN(false),
    /** Ends the current session. */
    LOGOUT(true),
    /** Opens the channel on which the server pushes live events. */
    SUBSCRIBE_EVENTS(true),

    /** A member of the public submits a disaster report. */
    SUBMIT_REPORT(false),
    /** Saves an incomplete report as a draft. */
    SAVE_DRAFT(true),
    /** A coordinator validates, rejects or links a report. */
    DECIDE_REPORT(true),
    /** Lists every report. */
    LIST_REPORTS(true),
    /** Lists the reports with one status. */
    LIST_REPORTS_BY_STATUS(true),
    /** Finds one report by its reference. */
    GET_REPORT(true),

    /** Finds reports that may describe the same event. */
    FIND_DUPLICATE_CANDIDATES(true),
    /** Scores how similar two reports are. */
    REPORT_SIMILARITY(true),

    /** Creates an incident from a validated report. */
    CREATE_INCIDENT_FROM_REPORT(true),
    /** Moves an incident to another status. */
    CHANGE_INCIDENT_STATUS(true),
    /** Closes an incident with its outcome and residual risks. */
    CLOSE_INCIDENT(true),
    /** A field responder records progress on an incident. */
    ADD_FIELD_UPDATE(true),
    /** Lists open incidents by priority, optionally for one priority. */
    LIST_OPEN_INCIDENTS(true),
    /** Lists the field updates of one incident. */
    GET_INCIDENT_TIMELINE(true),
    /** Finds one incident by its reference. */
    GET_INCIDENT(true),
    /** Lists every incident. */
    LIST_INCIDENTS(true),

    /** Calculates the severity score from the three factors. */
    CALCULATE_SCORE(true),
    /** Recommends a priority for a severity score. */
    RECOMMEND_PRIORITY(true),
    /** Records a scored assessment of an incident. */
    SCORE_ASSESSMENT(true),
    /** Confirms or overrides the recommended priority. */
    CONFIRM_PRIORITY(true),
    /** Finds the latest assessment of an incident. */
    GET_LATEST_ASSESSMENT(true),

    /** Recommends the departments for a disaster type. */
    RECOMMEND_DEPARTMENTS(true),
    /** Assigns an incident to a department. */
    CREATE_ASSIGNMENT(true),
    /** Sends an undelivered assignment to the partner again. */
    RETRY_DELIVERY(true),
    /** A department liaison accepts or declines an assignment. */
    RESPOND_TO_ASSIGNMENT(true),
    /** Allocates a resource to an assignment. */
    ALLOCATE_RESOURCE(true),
    /** Lists the assignments of one incident. */
    LIST_ASSIGNMENTS_FOR_INCIDENT(true),
    /** Lists every assignment. */
    LIST_ASSIGNMENTS(true),
    /** Simulates the partner interfaces going down or coming back. */
    SET_PARTNER_AVAILABLE(true),

    /** Lists open incidents that are at risk or past their target. */
    GET_ESCALATIONS(true),
    /** Evaluates the escalation state of every open incident. */
    EVALUATE_ESCALATIONS(true),
    /** Evaluates the escalation state of one incident. */
    EVALUATE_INCIDENT_ESCALATION(true),

    /** An administrator creates a staff account. */
    REGISTER_USER(true),
    /** Lists every audit event. */
    LIST_AUDIT_EVENTS(true),
    /** Lists the audit events of one entity. */
    LIST_AUDIT_FOR_ENTITY(true),

    /** Issues a public warning notice (warning module). */
    ISSUE_WARNING(true),
    /** Updates a warning notice (warning module). */
    UPDATE_WARNING(true),
    /** Cancels a warning notice (warning module). */
    CANCEL_WARNING(true),
    /** Acknowledges a warning notice (warning module). */
    ACK_WARNING(true),
    /** Lists warning notices for staff (warning module). */
    LIST_WARNINGS(true),
    /** Lists the active warnings shown to the public (warning module). */
    LIST_PUBLIC_WARNINGS(false),

    /** Imports a user from DRS-Initial data (import module). */
    IMPORT_USER(true),
    /** Imports a report (import module). */
    IMPORT_REPORT(true),
    /** Imports an incident (import module). */
    IMPORT_INCIDENT(true),
    /** Imports an assessment (import module). */
    IMPORT_ASSESSMENT(true),
    /** Imports an assignment (import module). */
    IMPORT_ASSIGNMENT(true),
    /** Imports a field update (import module). */
    IMPORT_FIELD_UPDATE(true),
    /** Imports an audit event (import module). */
    IMPORT_AUDIT(true);

    private final boolean loginRequired;

    /**
     * Creates a command.
     *
     * @param loginRequired whether a valid session token must be sent
     */
    Command(boolean loginRequired) {
        this.loginRequired = loginRequired;
    }

    /**
     * Tells whether the sender must be signed in.
     *
     * @return {@code true} unless the command is open to the public
     */
    public boolean isLoginRequired() {
        return this.loginRequired;
    }

    /**
     * Describes the command for logs.
     *
     * @return the constant name, marked when no login is needed
     */
    @Override
    public String toString() {
        if (this.loginRequired) {
            return name();
        }
        return name() + " (public)";
    }
}
