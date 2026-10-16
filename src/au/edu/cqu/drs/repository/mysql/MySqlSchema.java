package au.edu.cqu.drs.repository.mysql;

import java.util.List;

/**
 * The MySQL schema of DRS-Enhanced, kept in one place.
 *
 * <p>{@link MySqlDatabase} runs these statements when the server starts, as
 * spec section 2.4 requires, and {@code sql/schema.sql} is generated from them
 * by {@link #main(String[])}, so the script handed in always matches the code.
 * A test compares the two.</p>
 *
 * <p>Every table has a {@code BIGINT AUTO_INCREMENT} primary key named
 * {@code id}. The readable code that people use (for example INC-0001) is
 * kept in its own unique column, and foreign keys point at that code, because
 * the repository interfaces and the GUI work with codes.</p>
 *
 * <p>Two references are deliberately not foreign keys.
 * {@code reports.linked_incident_id} would make reports and incidents depend
 * on each other (the link table already enforces the relationship), and
 * {@code warning_acknowledgements.warning_id} points at a warning code that
 * is shared by every version of that warning, so it is not a unique key.</p>
 */
public final class MySqlSchema {

    /** Tables in the order they are created; children follow parents. */
    public static final List<String> TABLES = List.of(
            "users", "reports", "incidents", "incident_reports",
            "assessments", "assignments", "allocations", "field_updates",
            "audit_events", "warnings", "warning_acknowledgements",
            "user_keys");

    /** Character set and collation of the database and every table. */
    public static final String CHARSET =
            "CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci";

    /**
     * The collation is named rather than left to the server default, because
     * a foreign key between columns with different collations is refused.
     */
    private static final String TABLE_OPTIONS =
            " ENGINE=InnoDB DEFAULT " + CHARSET;

    private static final List<String> STATEMENTS = List.of(
            """
            CREATE TABLE IF NOT EXISTS users (
                id BIGINT NOT NULL AUTO_INCREMENT,
                user_id VARCHAR(16) NULL,
                username VARCHAR(64) NOT NULL,
                full_name VARCHAR(120),
                role VARCHAR(32) NOT NULL,
                department VARCHAR(48),
                password_salt VARCHAR(255) NOT NULL,
                password_hash VARCHAR(255) NOT NULL,
                active BOOLEAN NOT NULL DEFAULT TRUE,
                last_login_at DATETIME(6),
                PRIMARY KEY (id),
                UNIQUE KEY uk_users_code (user_id),
                UNIQUE KEY uk_users_username (username),
                KEY ix_users_role (role)
            )""" + TABLE_OPTIONS,
            """
            CREATE TABLE IF NOT EXISTS reports (
                id BIGINT NOT NULL AUTO_INCREMENT,
                report_id VARCHAR(16) NULL,
                disaster_type VARCHAR(32) NOT NULL,
                other_type_description VARCHAR(200),
                address_line VARCHAR(200),
                suburb VARCHAR(100),
                latitude DOUBLE,
                longitude DOUBLE,
                observed_at DATETIME(6),
                submitted_at DATETIME(6),
                description TEXT,
                immediate_risks TEXT,
                reporter_name TEXT,
                reporter_contact TEXT,
                status VARCHAR(32) NOT NULL,
                decision_reason TEXT,
                linked_incident_id VARCHAR(16),
                PRIMARY KEY (id),
                UNIQUE KEY uk_reports_code (report_id),
                KEY ix_reports_status (status),
                KEY ix_reports_incident (linked_incident_id)
            )""" + TABLE_OPTIONS,
            """
            CREATE TABLE IF NOT EXISTS incidents (
                id BIGINT NOT NULL AUTO_INCREMENT,
                incident_id VARCHAR(16) NULL,
                title VARCHAR(200),
                disaster_type VARCHAR(32),
                address_line VARCHAR(200),
                suburb VARCHAR(100),
                latitude DOUBLE,
                longitude DOUBLE,
                status VARCHAR(32) NOT NULL,
                priority VARCHAR(8) NOT NULL,
                created_at DATETIME(6),
                first_response_at DATETIME(6),
                closed_at DATETIME(6),
                closure_outcome TEXT,
                residual_risks TEXT,
                version INT NOT NULL DEFAULT 1,
                PRIMARY KEY (id),
                UNIQUE KEY uk_incidents_code (incident_id),
                KEY ix_incidents_status (status),
                KEY ix_incidents_priority (priority)
            )""" + TABLE_OPTIONS,
            """
            CREATE TABLE IF NOT EXISTS incident_reports (
                id BIGINT NOT NULL AUTO_INCREMENT,
                incident_id VARCHAR(16) NOT NULL,
                report_id VARCHAR(16) NOT NULL,
                PRIMARY KEY (id),
                UNIQUE KEY uk_incident_reports (incident_id, report_id),
                KEY ix_incident_reports_report (report_id),
                CONSTRAINT fk_incident_reports_incident
                    FOREIGN KEY (incident_id)
                    REFERENCES incidents (incident_id),
                CONSTRAINT fk_incident_reports_report
                    FOREIGN KEY (report_id)
                    REFERENCES reports (report_id)
            )""" + TABLE_OPTIONS,
            """
            CREATE TABLE IF NOT EXISTS assessments (
                id BIGINT NOT NULL AUTO_INCREMENT,
                assessment_id VARCHAR(16) NULL,
                incident_id VARCHAR(16) NOT NULL,
                threat_to_life INT,
                scale INT,
                infrastructure_impact INT,
                source_confidence INT,
                calculated_score INT,
                recommended_priority VARCHAR(8),
                confirmed_priority VARCHAR(8),
                override_reason TEXT,
                assessed_by VARCHAR(64),
                assessed_at DATETIME(6),
                version INT NOT NULL DEFAULT 1,
                PRIMARY KEY (id),
                UNIQUE KEY uk_assessments_code (assessment_id),
                KEY ix_assessments_incident (incident_id),
                CONSTRAINT fk_assessments_incident
                    FOREIGN KEY (incident_id)
                    REFERENCES incidents (incident_id)
            )""" + TABLE_OPTIONS,
            """
            CREATE TABLE IF NOT EXISTS assignments (
                id BIGINT NOT NULL AUTO_INCREMENT,
                assignment_id VARCHAR(16) NULL,
                incident_id VARCHAR(16) NOT NULL,
                department VARCHAR(48) NOT NULL,
                requested_action TEXT,
                urgency VARCHAR(64),
                target_response_by DATETIME(6),
                status VARCHAR(32) NOT NULL,
                created_at DATETIME(6),
                responded_at DATETIME(6),
                response_reason TEXT,
                estimated_arrival DATETIME(6),
                delivery_attempts INT NOT NULL DEFAULT 0,
                PRIMARY KEY (id),
                UNIQUE KEY uk_assignments_code (assignment_id),
                KEY ix_assignments_incident (incident_id),
                KEY ix_assignments_status (status),
                CONSTRAINT fk_assignments_incident
                    FOREIGN KEY (incident_id)
                    REFERENCES incidents (incident_id)
            )""" + TABLE_OPTIONS,
            """
            CREATE TABLE IF NOT EXISTS allocations (
                id BIGINT NOT NULL AUTO_INCREMENT,
                allocation_id VARCHAR(16) NULL,
                assignment_id VARCHAR(16) NOT NULL,
                resource_name VARCHAR(120) NOT NULL,
                department VARCHAR(48),
                quantity INT NOT NULL DEFAULT 1,
                available_from DATETIME(6),
                available_to DATETIME(6),
                staging_point VARCHAR(200),
                PRIMARY KEY (id),
                UNIQUE KEY uk_allocations_code (allocation_id),
                KEY ix_allocations_assignment (assignment_id),
                KEY ix_allocations_resource (resource_name),
                CONSTRAINT fk_allocations_assignment
                    FOREIGN KEY (assignment_id)
                    REFERENCES assignments (assignment_id)
            )""" + TABLE_OPTIONS,
            """
            CREATE TABLE IF NOT EXISTS field_updates (
                id BIGINT NOT NULL AUTO_INCREMENT,
                update_id VARCHAR(16) NULL,
                incident_id VARCHAR(16) NOT NULL,
                submitted_by VARCHAR(64),
                submitted_at DATETIME(6),
                progress_note TEXT,
                observed_impact TEXT,
                resource_needs TEXT,
                next_review_at DATETIME(6),
                incident_version_seen INT,
                PRIMARY KEY (id),
                UNIQUE KEY uk_field_updates_code (update_id),
                KEY ix_field_updates_incident (incident_id, submitted_at),
                CONSTRAINT fk_field_updates_incident
                    FOREIGN KEY (incident_id)
                    REFERENCES incidents (incident_id)
            )""" + TABLE_OPTIONS,
            """
            CREATE TABLE IF NOT EXISTS audit_events (
                id BIGINT NOT NULL AUTO_INCREMENT,
                event_id VARCHAR(16) NULL,
                actor VARCHAR(64),
                action VARCHAR(64),
                entity_type VARCHAR(48),
                entity_id VARCHAR(16),
                detail TEXT,
                occurred_at DATETIME(6),
                previous_hash CHAR(64),
                entry_hash CHAR(64),
                signature TEXT,
                PRIMARY KEY (id),
                UNIQUE KEY uk_audit_events_code (event_id),
                KEY ix_audit_events_entity (entity_type, entity_id),
                KEY ix_audit_events_occurred (occurred_at)
            )""" + TABLE_OPTIONS,
            """
            CREATE TABLE IF NOT EXISTS warnings (
                id BIGINT NOT NULL AUTO_INCREMENT,
                warning_id VARCHAR(16) NOT NULL,
                version INT NOT NULL DEFAULT 1,
                incident_id VARCHAR(16) NOT NULL,
                level VARCHAR(32) NOT NULL,
                affected_area VARCHAR(200) NOT NULL,
                message TEXT NOT NULL,
                recommended_action TEXT,
                evacuation_centre VARCHAR(200),
                evacuation_capacity INT,
                status VARCHAR(16) NOT NULL,
                issued_by VARCHAR(64) NOT NULL,
                issued_at DATETIME(6) NOT NULL,
                expires_at DATETIME(6),
                PRIMARY KEY (id),
                UNIQUE KEY uk_warnings_version (warning_id, version),
                KEY ix_warnings_incident (incident_id),
                KEY ix_warnings_status (status),
                CONSTRAINT fk_warnings_incident
                    FOREIGN KEY (incident_id)
                    REFERENCES incidents (incident_id)
            )""" + TABLE_OPTIONS,
            """
            CREATE TABLE IF NOT EXISTS warning_acknowledgements (
                id BIGINT NOT NULL AUTO_INCREMENT,
                warning_id VARCHAR(16) NOT NULL,
                department VARCHAR(48) NOT NULL,
                acknowledged_by VARCHAR(64) NOT NULL,
                acknowledged_at DATETIME(6) NOT NULL,
                PRIMARY KEY (id),
                UNIQUE KEY uk_warning_ack (warning_id, department)
            )""" + TABLE_OPTIONS,
            """
            CREATE TABLE IF NOT EXISTS user_keys (
                id BIGINT NOT NULL AUTO_INCREMENT,
                user_id VARCHAR(16) NOT NULL,
                algorithm VARCHAR(32) NOT NULL,
                public_key TEXT NOT NULL,
                created_at DATETIME(6) NOT NULL,
                PRIMARY KEY (id),
                KEY ix_user_keys_user (user_id),
                CONSTRAINT fk_user_keys_user
                    FOREIGN KEY (user_id)
                    REFERENCES users (user_id)
            )""" + TABLE_OPTIONS);

    /**
     * Prevents instances; every member is static.
     */
    private MySqlSchema() {
        // Holds constants only.
    }

    /**
     * Returns the CREATE TABLE statements, parents before children.
     *
     * @return the statements, without trailing semicolons
     */
    public static List<String> statements() {
        return STATEMENTS;
    }

    /**
     * Builds the full {@code sql/schema.sql} script for a database name.
     *
     * @param databaseName the database to create and use
     * @return the script text
     */
    public static String script(String databaseName) {
        StringBuilder script = new StringBuilder();
        script.append("-- DRS-Enhanced MySQL schema.\n")
                .append("-- Generated by au.edu.cqu.drs.repository.mysql")
                .append(".MySqlSchema; do not edit by hand.\n")
                .append("-- The server creates the same tables on start-up.")
                .append("\n\n")
                .append("CREATE DATABASE IF NOT EXISTS ").append(databaseName)
                .append(' ').append(CHARSET).append(";\n")
                .append("USE ").append(databaseName).append(";\n");
        for (String statement : STATEMENTS) {
            script.append('\n').append(statement).append(";\n");
        }
        return script.toString();
    }

    /**
     * Prints the schema script, for regenerating {@code sql/schema.sql}.
     *
     * @param args optional database name; the default is {@code drs}
     */
    public static void main(String[] args) {
        String name = args.length > 0
                ? args[0] : MySqlSettings.DEFAULT_DATABASE;
        System.out.print(script(name));
    }

    /**
     * Describes this constants class and the tables it defines.
     *
     * @return the class name and the number of tables
     */
    @Override
    public String toString() {
        return "MySqlSchema[" + TABLES.size() + " tables]";
    }
}
