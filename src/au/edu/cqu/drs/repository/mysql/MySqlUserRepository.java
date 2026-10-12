package au.edu.cqu.drs.repository.mysql;

import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.repository.UserRepository;
import au.edu.cqu.drs.repository.mysql.MySqlSupport.CodeKey;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** MySQL adapter for {@link UserRepository}. */
public class MySqlUserRepository implements UserRepository {

    private static final List<String> COLUMNS = List.of(
            "username", "full_name", "role", "department", "password_salt",
            "password_hash", "active", "last_login_at");

    private final MySqlDatabase database;

    /**
     * Creates the repository.
     *
     * @param database the pooled MySQL database
     */
    public MySqlUserRepository(MySqlDatabase database) {
        this.database = database;
    }

    /**
     * Inserts a new account, giving it a code, or updates an existing one.
     *
     * @param user the account
     * @return the same account, with its code set
     */
    @Override
    public User save(User user) {
        try {
            String code = this.database.inTransaction(connection ->
                    MySqlSupport.save(connection, CodeKey.USER, COLUMNS,
                            user.getUserId(),
                            (statement, first) ->
                                    bind(statement, first, user)));
            user.setUserId(code);
            return user;
        } catch (SQLException e) {
            throw new IllegalStateException("Could not save the user "
                    + "account.", e);
        }
    }

    /**
     * Finds an account by user name.
     *
     * @param username the user name
     * @return the account, if there is one
     */
    @Override
    public Optional<User> findByUsername(String username) {
        return findOne("SELECT * FROM users WHERE username = ?", username);
    }

    /**
     * Finds an account by code.
     *
     * @param userId the code, for example USR-001
     * @return the account, if there is one
     */
    @Override
    public Optional<User> findById(String userId) {
        return findOne("SELECT * FROM users WHERE user_id = ?", userId);
    }

    /**
     * Lists every account.
     *
     * @return the accounts in code order
     */
    @Override
    public List<User> findAll() {
        try {
            return this.database.withConnection(connection -> {
                List<User> users = new ArrayList<>();
                try (PreparedStatement statement = connection
                        .prepareStatement("SELECT * FROM users ORDER BY id");
                     ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        users.add(map(rs));
                    }
                }
                return users;
            });
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read user accounts.",
                    e);
        }
    }

    /**
     * Describes the repository.
     *
     * @return the table it uses
     */
    @Override
    public String toString() {
        return "MySqlUserRepository: users";
    }

    private Optional<User> findOne(String sql, String parameter) {
        try {
            return this.database.withConnection(connection ->
                    queryOne(connection, sql, parameter));
        } catch (SQLException e) {
            throw new IllegalStateException("Could not read the user "
                    + "account.", e);
        }
    }

    private Optional<User> queryOne(Connection connection, String sql,
            String parameter) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private void bind(PreparedStatement statement, int first, User user)
            throws SQLException {
        int i = first;
        statement.setString(i++, user.getUsername());
        statement.setString(i++, user.getFullName());
        statement.setString(i++, MySqlSupport.fromEnum(user.getRole()));
        statement.setString(i++,
                MySqlSupport.fromEnum(user.getDepartment()));
        statement.setString(i++, user.getPasswordSalt());
        statement.setString(i++, user.getPasswordHash());
        statement.setBoolean(i++, user.isActive());
        MySqlSupport.setDateTime(statement, i, user.getLastLoginAt());
    }

    private User map(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getString("user_id"));
        user.setUsername(rs.getString("username"));
        user.setFullName(rs.getString("full_name"));
        user.setRole(MySqlSupport.toEnum(UserRole.class,
                rs.getString("role")));
        user.setDepartment(MySqlSupport.toEnum(Department.class,
                rs.getString("department")));
        user.setPasswordSalt(rs.getString("password_salt"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setActive(rs.getBoolean("active"));
        user.setLastLoginAt(MySqlSupport.getDateTime(rs, "last_login_at"));
        return user;
    }
}
