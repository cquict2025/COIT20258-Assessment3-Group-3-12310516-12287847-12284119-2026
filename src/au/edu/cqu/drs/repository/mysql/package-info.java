/**
 * MySQL adapters for the repository interfaces in
 * {@link au.edu.cqu.drs.repository}.
 *
 * <p>{@link au.edu.cqu.drs.repository.mysql.MySqlDatabase} creates the
 * database and its tables when the server starts and lends pooled
 * connections to the repositories, so many clients can be served at once.
 * {@link au.edu.cqu.drs.repository.mysql.MySqlSchema} holds the table
 * definitions, from which {@code sql/schema.sql} is generated. Every
 * statement that carries a value uses a {@code PreparedStatement}
 * parameter, so no input is ever joined into SQL text.</p>
 */
package au.edu.cqu.drs.repository.mysql;
