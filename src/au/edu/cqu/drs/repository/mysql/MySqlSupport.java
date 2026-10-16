package au.edu.cqu.drs.repository.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Helpers shared by the MySQL repositories.
 *
 * <p>Readable codes such as INC-0007 are made from the row's
 * {@code AUTO_INCREMENT} id, inside the same transaction as the insert.
 * DRS-Initial counted the rows instead, which gives two clients the same
 * code when they save at the same moment; an id from MySQL cannot repeat.</p>
 */
final class MySqlSupport {

    /**
     * Prevents instances; every member is static.
     */
    private MySqlSupport() {
        // Utility class - not instantiated.
    }

    /**
     * Sets a date-time parameter, or SQL NULL.
     *
     * @param statement the statement
     * @param index     the parameter position
     * @param value     the value, may be null
     * @throws SQLException if the parameter cannot be set
     */
    static void setDateTime(PreparedStatement statement, int index,
            LocalDateTime value) throws SQLException {
        if (value == null) {
            statement.setNull(index, Types.TIMESTAMP);
        } else {
            statement.setObject(index, value);
        }
    }

    /**
     * Reads a date-time column.
     *
     * @param rs     the current row
     * @param column the column name
     * @return the value, or null
     * @throws SQLException if the column cannot be read
     */
    static LocalDateTime getDateTime(ResultSet rs, String column)
            throws SQLException {
        return rs.getObject(column, LocalDateTime.class);
    }

    /**
     * Converts an enum constant to its stored name.
     *
     * @param value the constant, may be null
     * @return the name, or null
     */
    static String fromEnum(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /**
     * Converts a stored name to an enum constant.
     *
     * @param type  the enum class
     * @param value the stored name, may be null or blank
     * @param <E>   the enum type
     * @return the constant, or null
     */
    static <E extends Enum<E>> E toEnum(Class<E> type, String value) {
        return value == null || value.isBlank() ? null
                : Enum.valueOf(type, value);
    }

    /**
     * Saves an entity that has a readable code: updates its row when the code
     * is already stored, otherwise inserts a new row.
     *
     * @param connection a connection inside a transaction
     * @param key        the table, code column and code format
     * @param columns    the other columns, in the order the binder sets them
     * @param code       the entity's code, or null for a new entity
     * @param binder     sets the values of {@code columns}
     * @return the entity's code
     * @throws SQLException if a statement fails
     */
    static String save(Connection connection, CodeKey key,
            List<String> columns, String code, FieldBinder binder)
            throws SQLException {
        if (code != null) {
            // Table and column names are constants in this package, never
            // input, so they can be joined into the SQL text.
            String sql = "UPDATE " + key.table() + " SET "
                    + String.join(" = ?, ", columns) + " = ? WHERE "
                    + key.column() + " = ?";
            try (PreparedStatement statement =
                    connection.prepareStatement(sql)) {
                binder.bind(statement, 1);
                statement.setString(columns.size() + 1, code);
                if (statement.executeUpdate() > 0) {
                    return code;
                }
            }
        }
        return insert(connection, key, columns, code, binder);
    }

    /**
     * Inserts a new row. When the entity has no code yet, the row's id is
     * turned into one, for example INC-0007, and written back in the same
     * transaction.
     *
     * @param connection a connection inside a transaction
     * @param key        the table, code column and code format
     * @param columns    the other columns, in the order the binder sets them
     * @param code       the entity's code, or null to generate one
     * @param binder     sets the values of {@code columns}
     * @return the entity's code
     * @throws SQLException if a statement fails, for example a duplicate code
     */
    static String insert(Connection connection, CodeKey key,
            List<String> columns, String code, FieldBinder binder)
            throws SQLException {
        String sql = "INSERT INTO " + key.table() + " (" + key.column()
                + ", " + String.join(", ", columns) + ") VALUES (?"
                + ", ?".repeat(columns.size()) + ")";
        long id;
        try (PreparedStatement statement = connection.prepareStatement(sql,
                Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, code);
            binder.bind(statement, 2);
            statement.executeUpdate();
            if (code != null) {
                return code;
            }
            try (ResultSet keys = statement.getGeneratedKeys()) {
                if (!keys.next()) {
                    throw new SQLException("MySQL returned no id for a new "
                            + key.table() + " row.");
                }
                id = keys.getLong(1);
            }
        }
        String newCode = key.format(id);
        String update = "UPDATE " + key.table() + " SET " + key.column()
                + " = ? WHERE id = ?";
        try (PreparedStatement statement =
                connection.prepareStatement(update)) {
            statement.setString(1, newCode);
            statement.setLong(2, id);
            statement.executeUpdate();
        }
        return newCode;
    }

    /**
     * Sets the column values of an INSERT or UPDATE statement.
     */
    @FunctionalInterface
    interface FieldBinder {

        /**
         * Sets one parameter per column, starting at {@code first}.
         *
         * @param statement the statement to fill
         * @param first     the position of the first column's parameter
         * @throws SQLException if a parameter cannot be set
         */
        void bind(PreparedStatement statement, int first) throws SQLException;
    }

    /**
     * Where a table keeps its readable code and how the code looks.
     */
    enum CodeKey {

        /** Staff accounts, USR-001. */
        USER("users", "user_id", "USR", 3),
        /** Public reports, RPT-0001. */
        REPORT("reports", "report_id", "RPT", 4),
        /** Incidents, INC-0001. */
        INCIDENT("incidents", "incident_id", "INC", 4),
        /** Assessments, ASM-0001. */
        ASSESSMENT("assessments", "assessment_id", "ASM", 4),
        /** Department assignments, ASG-0001. */
        ASSIGNMENT("assignments", "assignment_id", "ASG", 4),
        /** Resource allocations, ALC-0001. */
        ALLOCATION("allocations", "allocation_id", "ALC", 4),
        /** Field updates, UPD-0001. */
        FIELD_UPDATE("field_updates", "update_id", "UPD", 4),
        /** Audit events, AUD-00001. */
        AUDIT_EVENT("audit_events", "event_id", "AUD", 5);

        private final String table;
        private final String column;
        private final String prefix;
        private final int digits;

        /**
         * Creates a code key.
         *
         * @param table  the table that holds the code
         * @param column the column that holds the code
         * @param prefix the letters before the number, for example INC
         * @param digits how many digits the number is padded to
         */
        CodeKey(String table, String column, String prefix, int digits) {
            this.table = table;
            this.column = column;
            this.prefix = prefix;
            this.digits = digits;
        }

        /**
         * Returns the table name.
         *
         * @return the table
         */
        String table() {
            return this.table;
        }

        /**
         * Returns the code column name.
         *
         * @return the column
         */
        String column() {
            return this.column;
        }

        /**
         * Formats an id as a code, the same way DRS-Initial did.
         *
         * @param id the row id
         * @return the code, for example INC-0007
         */
        String format(long id) {
            return String.format("%s-%0" + this.digits + "d", this.prefix,
                    id);
        }

        /**
         * Describes the key.
         *
         * @return the table, column and an example code
         */
        @Override
        public String toString() {
            return this.table + "." + this.column + " (" + format(1) + ")";
        }
    }

    /**
     * Describes this utility class and the code keys it holds.
     *
     * @return the class name and the number of code keys
     */
    @Override
    public String toString() {
        return "MySqlSupport[" + CodeKey.values().length + " code keys]";
    }
}
