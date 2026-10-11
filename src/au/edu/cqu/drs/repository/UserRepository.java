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

    User save(User user);

    Optional<User> findByUsername(String username);

    Optional<User> findById(String userId);

    List<User> findAll();
}
