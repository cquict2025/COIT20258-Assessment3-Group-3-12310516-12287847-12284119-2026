package au.edu.cqu.drs.repository.sqlite;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Small helpers shared by the SQLite adapters. SQLite has no native timestamp
 * type, so date-times are stored as ISO-8601 text, which sorts correctly and
 * stays readable if the file is inspected during marking.
 */
final class SqlSupport {

    private SqlSupport() {
        // Utility class - not instantiated.
    }

    static String toText(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    static LocalDateTime toDateTime(String value) {
        return value == null
                || value.isBlank() ? null : LocalDateTime.parse(value);
    }

    static <E extends Enum<E>> E toEnum(Class<E> type, String value) {
        return value == null || value.isBlank() ? null : Enum.valueOf(type,
                value);
    }

    static String fromEnum(Enum<?> value) {
        return value == null ? null : value.name();
    }

    /**
     * Generates the next identifier for a table, for example INC-0007.
     *
     * @param connection  the live connection
     * @param table       the table to count
     * @param idColumn    the primary key column
     * @param prefix      the identifier prefix
     * @param digits      how many digits to pad to
     * @return the next identifier
     */
    static String nextId(Connection connection, String table, String idColumn,
                         String prefix, int digits) {
        String sql = "SELECT COUNT(*) FROM " + table;
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            int count = rs.next() ? rs.getInt(1) : 0;
            return String.format("%s-%0" + digits + "d", prefix, count + 1);
        } catch (SQLException e) {
            throw new IllegalStateException("Could not generate an identifier "
                    + "for " + table,
                                            
                                            
                                            
                                            e);
        }
    }
}
