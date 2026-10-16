package au.edu.cqu.drs.repository.sqlite;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Owns the single SQLite connection used by the prototype and creates the
 * schema on first use. Assessment 1 chose an embedded database deliberately:
 * simple deployment and reliable local transactions were judged more valuable
 * for a single-workstation prototype than concurrent access.
 */
public class Database {

    private final String jdbcUrl;
    private Connection connection;

    /**
     * Creates a database.
     *
     * @param databaseFile the database file
     */
    public Database(String databaseFile) {
        this.jdbcUrl = "jdbc:sqlite:" + databaseFile;
    }

    /**
     * Returns the open connection, opening and initialising it on first call.
     *
     * @return the live JDBC connection
     */
    public Connection getConnection() {
        try {
            if (this.connection == null || this.connection.isClosed()) {
                this.connection = DriverManager.getConnection(this.jdbcUrl);
                try (Statement statement = this.connection.createStatement()) {
                    statement.execute("PRAGMA foreign_keys = ON");
                }
                createSchema();
            }
            return this.connection;
        } catch (SQLException e) {
            throw new IllegalStateException("Unable to open the DRS database: "
                    + e.getMessage(),
                                            
                                            
                                            
                                            e);
        }
    }

    /** Closes the connection if it is open. */
    public void close() {
        try {
            if (this.connection != null && !this.connection.isClosed()) {
                this.connection.close();
            }
        } catch (SQLException e) {
            // A failure to close is logged rather than propagated: the
            // application is shutting down and the user can do nothing about
            // it.
            System.err.println("Could not close the DRS database cleanly: "
                    + e.getMessage());
        }
    }

    /**
     * Creates every table the prototype uses, skipping any that already
     * exist.
     *
     * @throws SQLException if a table cannot be created
     */
    private void createSchema() throws SQLException {
        try (Statement statement = this.connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS users (
                        user_id TEXT PRIMARY KEY,
                        username TEXT NOT NULL UNIQUE,
                        full_name TEXT,
                        role TEXT NOT NULL,
                        department TEXT,
                        password_salt TEXT NOT NULL,
                        password_hash TEXT NOT NULL,
                        active INTEGER NOT NULL DEFAULT 1,
                        last_login_at TEXT)""");

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS reports (
                        report_id TEXT PRIMARY KEY,
                        disaster_type TEXT NOT NULL,
                        other_type_description TEXT,
                        address_line TEXT,
                        suburb TEXT,
                        latitude REAL,
                        longitude REAL,
                        observed_at TEXT,
                        submitted_at TEXT,
                        description TEXT,
                        immediate_risks TEXT,
                        reporter_name TEXT,
                        reporter_contact TEXT,
                        status TEXT NOT NULL,
                        decision_reason TEXT,
                        linked_incident_id TEXT)""");

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS incidents (
                        incident_id TEXT PRIMARY KEY,
                        title TEXT,
                        disaster_type TEXT,
                        address_line TEXT,
                        suburb TEXT,
                        latitude REAL,
                        longitude REAL,
                        status TEXT NOT NULL,
                        priority TEXT NOT NULL,
                        created_at TEXT,
                        first_response_at TEXT,
                        closed_at TEXT,
                        closure_outcome TEXT,
                        residual_risks TEXT,
                        version INTEGER NOT NULL DEFAULT 1)""");

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS incident_reports (
                        incident_id TEXT NOT NULL,
                        report_id TEXT NOT NULL,
                        PRIMARY KEY (incident_id, report_id))""");

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS assessments (
                        assessment_id TEXT PRIMARY KEY,
                        incident_id TEXT NOT NULL,
                        threat_to_life INTEGER,
                        scale INTEGER,
                        infrastructure_impact INTEGER,
                        source_confidence INTEGER,
                        calculated_score INTEGER,
                        recommended_priority TEXT,
                        confirmed_priority TEXT,
                        override_reason TEXT,
                        assessed_by TEXT,
                        assessed_at TEXT,
                        version INTEGER NOT NULL DEFAULT 1)""");

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS assignments (
                        assignment_id TEXT PRIMARY KEY,
                        incident_id TEXT NOT NULL,
                        department TEXT NOT NULL,
                        requested_action TEXT,
                        urgency TEXT,
                        target_response_by TEXT,
                        status TEXT NOT NULL,
                        created_at TEXT,
                        responded_at TEXT,
                        response_reason TEXT,
                        estimated_arrival TEXT,
                        delivery_attempts INTEGER NOT NULL DEFAULT 0)""");

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS allocations (
                        allocation_id TEXT PRIMARY KEY,
                        assignment_id TEXT NOT NULL,
                        resource_name TEXT NOT NULL,
                        department TEXT,
                        quantity INTEGER NOT NULL DEFAULT 1,
                        available_from TEXT,
                        available_to TEXT,
                        staging_point TEXT)""");

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS field_updates (
                        update_id TEXT PRIMARY KEY,
                        incident_id TEXT NOT NULL,
                        submitted_by TEXT,
                        submitted_at TEXT,
                        progress_note TEXT,
                        observed_impact TEXT,
                        resource_needs TEXT,
                        next_review_at TEXT,
                        incident_version_seen INTEGER)""");

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS audit_events (
                        event_id TEXT PRIMARY KEY,
                        actor TEXT,
                        action TEXT,
                        entity_type TEXT,
                        entity_id TEXT,
                        detail TEXT,
                        occurred_at TEXT)""");
        }
    }

    /**
     * Describes the database.
     *
     * @return its JDBC URL and whether a connection has been opened
     */
    @Override
    public String toString() {
        return "Database: " + this.jdbcUrl
                + (this.connection == null ? " (not connected)"
                        : " (connection opened)");
    }
}
