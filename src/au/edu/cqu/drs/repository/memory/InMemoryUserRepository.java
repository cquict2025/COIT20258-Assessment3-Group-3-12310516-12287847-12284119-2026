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

    @Override
    public User save(User user) {
        if (user.getUserId() == null) {
            this.sequence++;
            user.setUserId(String.format("USR-%03d", this.sequence));
        }
        this.usersById.put(user.getUserId(), user);
        return user;
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return this.usersById.values().stream()
                .filter(u -> u.getUsername().equalsIgnoreCase(username))
                .findFirst();
    }

    @Override
    public Optional<User> findById(String userId) {
        return Optional.ofNullable(this.usersById.get(userId));
    }

    @Override
    public List<User> findAll() {
        return new ArrayList<>(this.usersById.values());
    }
}
