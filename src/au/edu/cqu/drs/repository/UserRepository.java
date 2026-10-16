package au.edu.cqu.drs.repository;

import au.edu.cqu.drs.model.User;

import java.util.List;
import java.util.Optional;

/**
 * Data-access contract for staff accounts. Assessment 1 (UR-09, NFR-08)
 * required persistence to sit behind an interface so that services can be unit
 * tested with in-memory doubles and the SQLite adapter can be replaced later.
 */
public interface UserRepository {

    /**
     * Saves a user account, giving it an identifier if it has none yet.
     *
     * @param user the account to save
     * @return the saved account, with its identifier set
     */
    User save(User user);

    /**
     * Finds a user account by its username.
     *
     * @param username the username to look up
     * @return the account, or empty if there is none
     */
    Optional<User> findByUsername(String username);

    /**
     * Finds a user account by its identifier.
     *
     * @param userId the user identifier
     * @return the account, or empty if there is none
     */
    Optional<User> findById(String userId);

    /**
     * Lists every stored user account.
     *
     * @return all accounts
     */
    List<User> findAll();
}
