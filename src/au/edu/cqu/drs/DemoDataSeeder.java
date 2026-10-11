package au.edu.cqu.drs;

import au.edu.cqu.drs.model.Department;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.repository.UserRepository;
import au.edu.cqu.drs.service.AuthenticationService;
import au.edu.cqu.drs.service.ServiceResult;

/**
 * Creates the four demonstration accounts on first run so that the prototype
 * can
 * be assessed without a separate setup step. Passwords are supplied here in
 * source only to create the accounts; they are hashed with a per-account salt
 * before storage and are never written to the database in plaintext.
 */
public class DemoDataSeeder {

    private final AuthenticationService authenticationService;
    private final UserRepository userRepository;

    public DemoDataSeeder(AuthenticationService authenticationService,
                          UserRepository userRepository) {
        this.authenticationService = authenticationService;
        this.userRepository = userRepository;
    }

    /** Creates the demonstration accounts only when no accounts exist yet. */
    public void seedIfEmpty() {
        if (!this.userRepository.findAll().isEmpty()) {
            return;
        }
        create("coordinator", "Alex Coordinator", UserRole.COORDINATOR, null);
        create("liaison", "Sam Liaison", UserRole.LIAISON,
               Department.FIRE_AND_EMERGENCY);
        create("responder", "Jo Responder", UserRole.RESPONDER, null);
        create("admin", "Robin Administrator", UserRole.ADMINISTRATOR, null);
    }

    private void create(String username, String fullName, UserRole role,
                        Department department) {
        ServiceResult<User> result =
                this.authenticationService.register(username, fullName, role,
                                               "Password1");
        if (result.isSuccess() && department != null) {
            User user = result.getValue();
            user.setDepartment(department);
            this.userRepository.save(user);
        }
    }
}
