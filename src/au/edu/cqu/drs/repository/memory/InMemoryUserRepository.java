package au.edu.cqu.drs.repository.memory;

import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.repository.UserRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** In-memory test double used by the JUnit suite (Assessment 1, UR-09). */
public class InMemoryUserRepository implements UserRepository {

    private final Map<String, User> usersById = new LinkedHashMap<>();
    private int sequence = 0;

    /**
     * Stores a user account, replacing any stored one with the same
     * identifier. A new account is given the next USR- identifier.
     *
     * @param user the account to store
     * @return the same account, with its identifier set
     */
    @Override
    public User save(User user) {
        if (user.getUserId() == null) {
            this.sequence++;
            user.setUserId(String.format("USR-%03d", this.sequence));
        }
        this.usersById.put(user.getUserId(), user);
        return user;
    }

    /**
     * Finds a user account by its username, ignoring case.
     *
     * @param username the username to look up
     * @return the first matching account, or empty if there is none
     */
    @Override
    public Optional<User> findByUsername(String username) {
        return this.usersById.values().stream()
                .filter(u -> u.getUsername().equalsIgnoreCase(username))
                .findFirst();
    }

    /**
     * Finds a user account by its identifier.
     *
     * @param userId the user identifier
     * @return the account, or empty if there is none
     */
    @Override
    public Optional<User> findById(String userId) {
        return Optional.ofNullable(this.usersById.get(userId));
    }

    /**
     * Lists every stored user account.
     *
     * @return a new list of all accounts, in the order stored
     */
    @Override
    public List<User> findAll() {
        return new ArrayList<>(this.usersById.values());
    }

    /**
     * Describes the repository.
     *
     * @return how many user accounts it holds
     */
    @Override
    public String toString() {
        return "InMemoryUserRepository: " + this.usersById.size()
                + " user(s)";
    }
}
