package au.edu.cqu.drs.repository.sqlite;

import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.repository.UserRepository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** SQLite adapter for {@link UserRepository}. */
public class SqliteUserRepository implements UserRepository {

    private final Database database;

    /**
     * Creates a SQLite user repository.
     *
     * @param database the database
     */
    public SqliteUserRepository(Database database) {
        this.database = database;
    }

    /**
     * Inserts a user account, or updates the stored row with the same
     * identifier. A new account is given the next USR- identifier.
     *
     * @param user the account to save
     * @return the same account, with its identifier set
     * @throws IllegalStateException if the row cannot be written
     */
    @Override
    public User save(User user) {
        Connection connection = this.database.getConnection();
        if (user.getUserId() == null) {
            user.setUserId(SqlSupport
                    .nextId(connection, "users", "user_id", "USR", 3));
        }
        String sql = """
                INSERT INTO users (
                    user_id, username, full_name, role, department,
                    password_salt, password_hash, active, last_login_at)
                VALUES (?,?,?,?,?,?,?,?,?)
                ON CONFLICT(user_id) DO UPDATE SET
                    username = excluded.username,
                    full_name = excluded.full_name,
                    role = excluded.role,
                    department = excluded.department,
                    password_salt = excluded.password_salt,
                    password_hash = excluded.password_hash,
                    active = excluded.active,
                    last_login_at = excluded.last_login_at""";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, user.getUserId());
            statement.setString(2, user.getUsername());
            statement.setString(3, user.getFullName());
            statement.setString(4, SqlSupport.fromEnum(user.getRole()));
            statement.setString(5, SqlSupport.fromEnum(user.getDepartment()));
            statement.setString(6, user.getPasswordSalt());
            statement.setString(7, user.getPasswordHash());
            statement.setInt(8, user.isActive() ? 1 : 0);
            statement.setString(9, SqlSupport.toText(user.getLastLoginAt()));
            statement.executeUpdate();
            return user;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save the user account.",
                                            e);
        }
    }

    /**
     * Finds a user account by its exact username.
     *
     * @param username the username to look up
     * @return the account, or empty if there is none
     */
    @Override
    public Optional<User> findByUsername(String username) {
        return findOne("SELECT * FROM users WHERE username = ?", username);
    }

    /**
     * Finds a user account by its identifier.
     *
     * @param userId the user identifier
     * @return the account, or empty if there is none
     */
    @Override
    public Optional<User> findById(String userId) {
        return findOne("SELECT * FROM users WHERE user_id = ?", userId);
    }

    /**
     * Lists every user account, ordered by identifier.
     *
     * @return all accounts
     * @throws IllegalStateException if the rows cannot be read
     */
    @Override
    public List<User> findAll() {
        List<User> users = new ArrayList<>();
        try (PreparedStatement statement =
                this.database.getConnection().prepareStatement("SELECT * FROM "
                             + "users ORDER BY user_id");
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                users.add(map(rs));
            }
            return users;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read user accounts.", e);
        }
    }

    /**
     * Runs a one-parameter query and maps the first row to an account.
     *
     * @param sql the query to run
     * @param parameter the query parameter
     * @return the account, or empty if no row matched
     * @throws IllegalStateException if the row cannot be read
     */
    private Optional<User> findOne(String sql, String parameter) {
        try (PreparedStatement statement = this.database.getConnection()
                .prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the user account.",
                                            e);
        }
    }

    /**
     * Builds a user account from the current row of a result set.
     *
     * @param rs the result set, positioned on the row to read
     * @return the account read from the row
     * @throws SQLException if a column cannot be read
     */
    private User map(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getString("user_id"));
        user.setUsername(rs.getString("username"));
        user.setFullName(rs.getString("full_name"));
        user.setRole(SqlSupport.toEnum(UserRole.class, rs.getString("role")));
        user.setDepartment(SqlSupport
                .toEnum(Department.class, rs.getString("department")));
        user.setPasswordSalt(rs.getString("password_salt"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setActive(rs.getInt("active") == 1);
        user.setLastLoginAt(SqlSupport
                .toDateTime(rs.getString("last_login_at")));
        return user;
    }

    /**
     * Describes the repository.
     *
     * @return the database it reads and writes
     */
    @Override
    public String toString() {
        return "SqliteUserRepository: " + this.database;
    }
}
