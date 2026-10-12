package au.edu.cqu.drs.repository.mysql;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Creates the DRS database and its tables, and lends connections to the
 * MySQL repositories.
 *
 * <p>The server serves many clients at once, so one shared connection, as in
 * DRS-Initial's SQLite {@code Database}, is not enough. This class keeps a
 * small bounded pool in an {@link ArrayBlockingQueue}: a repository borrows a
 * connection for one operation and gives it back. When every connection is
 * busy the caller waits, up to {@value #BORROW_TIMEOUT_SECONDS} seconds,
 * instead of opening more connections than MySQL allows.</p>
 */
public class MySqlDatabase implements AutoCloseable {

    /** Longest wait for a free connection before giving up. */
    public static final int BORROW_TIMEOUT_SECONDS = 10;

    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    private final MySqlSettings settings;
    private final BlockingQueue<Connection> pool;
    private final List<Connection> opened;
    private volatile boolean closed;

    /**
     * Connects to MySQL, creates the database and every table if they are
     * missing (spec section 2.4), and fills the connection pool.
     *
     * @param settings where and how to connect
     * @throws IllegalStateException if MySQL cannot be reached or the schema
     *                               cannot be created
     */
    public MySqlDatabase(MySqlSettings settings) {
        this.settings = settings;
        this.pool = new ArrayBlockingQueue<>(settings.getPoolSize());
        this.opened = new ArrayList<>();
        try {
            createDatabase();
            for (int i = 0; i < settings.getPoolSize(); i++) {
                Connection connection = open();
                this.opened.add(connection);
                this.pool.add(connection);
            }
            createTables();
        } catch (SQLException e) {
            close();
            throw new IllegalStateException("Cannot use MySQL at "
                    + settings.databaseUrl().replaceAll("\\?.*", "")
                    + " as " + settings.getUser() + ": " + e.getMessage()
                    + " Check that MySQL is running and the mysql.* "
                    + "settings are correct.", e);
        }
    }

    /**
     * Runs work on a pooled connection and returns the connection afterwards.
     *
     * @param work the work to run
     * @param <T>  the result type
     * @return what the work returned
     * @throws SQLException if the work fails
     */
    public <T> T withConnection(SqlWork<T> work) throws SQLException {
        Connection connection = borrow();
        try {
            return work.run(connection);
        } finally {
            giveBack(connection);
        }
    }

    /**
     * Runs work in one transaction: it is committed if the work finishes and
     * rolled back if it throws.
     *
     * @param work the work to run
     * @param <T>  the result type
     * @return what the work returned
     * @throws SQLException if the work or the commit fails
     */
    public <T> T inTransaction(SqlWork<T> work) throws SQLException {
        return withConnection(connection -> {
            connection.setAutoCommit(false);
            try {
                T result = work.run(connection);
                connection.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        });
    }

    /**
     * Returns the settings in use.
     *
     * @return the settings
     */
    public MySqlSettings getSettings() {
        return this.settings;
    }

    /**
     * Closes every connection. Later calls to {@link #withConnection} fail.
     */
    @Override
    public void close() {
        this.closed = true;
        synchronized (this.opened) {
            for (Connection connection : this.opened) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    // The server is stopping; a connection that will not
                    // close cleanly is dropped by MySQL when the socket goes.
                    System.err.println("Could not close a MySQL "
                            + "connection: " + e.getMessage());
                }
            }
            this.opened.clear();
        }
        this.pool.clear();
    }

    /**
     * Describes the database.
     *
     * @return the settings and how many connections are free
     */
    @Override
    public String toString() {
        return "MySqlDatabase: " + this.settings + ", " + this.pool.size()
                + " free";
    }

    private void createDatabase() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                this.settings.serverUrl(), this.settings.getUser(),
                this.settings.getPassword());
             Statement statement = connection.createStatement()) {
            // The name has been checked to be a plain identifier.
            statement.executeUpdate("CREATE DATABASE IF NOT EXISTS "
                    + this.settings.getDatabase()
                    + " CHARACTER SET utf8mb4");
        }
    }

    private void createTables() throws SQLException {
        withConnection(connection -> {
            try (Statement statement = connection.createStatement()) {
                for (String sql : MySqlSchema.statements()) {
                    statement.executeUpdate(sql);
                }
            }
            return null;
        });
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(this.settings.databaseUrl(),
                this.settings.getUser(), this.settings.getPassword());
    }

    private Connection borrow() throws SQLException {
        if (this.closed) {
            throw new SQLException("The MySQL connection pool is closed.");
        }
        Connection connection;
        try {
            connection = this.pool.poll(BORROW_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SQLException("Interrupted while waiting for a MySQL "
                    + "connection.", e);
        }
        if (connection == null) {
            throw new SQLException("Every MySQL connection is busy. Try "
                    + "again, or raise mysql.pool.size.");
        }
        if (!connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
            connection = replace(connection);
        }
        return connection;
    }

    private Connection replace(Connection broken) throws SQLException {
        try {
            broken.close();
        } catch (SQLException e) {
            // It is already broken; replacing it is what matters.
        }
        Connection fresh;
        try {
            fresh = open();
        } catch (SQLException e) {
            // Keep the pool at full size so a later request can retry.
            this.pool.offer(broken);
            throw e;
        }
        synchronized (this.opened) {
            this.opened.remove(broken);
            this.opened.add(fresh);
        }
        return fresh;
    }

    private void giveBack(Connection connection) {
        if (this.closed) {
            return;
        }
        this.pool.offer(connection);
    }

    /**
     * Work done with a borrowed connection.
     *
     * @param <T> the result type
     */
    @FunctionalInterface
    public interface SqlWork<T> {

        /**
         * Runs the work.
         *
         * @param connection a connection that the caller must not close
         * @return the result
         * @throws SQLException if a statement fails
         */
        T run(Connection connection) throws SQLException;
    }
}
