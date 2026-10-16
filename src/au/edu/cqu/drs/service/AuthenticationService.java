package au.edu.cqu.drs.service;

import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.repository.UserRepository;
import au.edu.cqu.drs.util.PasswordHasher;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Verifies staff credentials and enforces least privilege in the service layer
 * rather than in the screens (UC-01, FR-01, NFR-04). Because authorisation
 * lives here, the same rule applies no matter which controller calls a command.
 */
public class AuthenticationService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    /**
     * Creates an authentication service.
     *
     * @param userRepository the user repository
     * @param auditService   the audit service
     */
    public AuthenticationService(UserRepository userRepository,
                                 AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Authenticates a staff member.
     *
     * @param username the account name
     * @param password the plaintext password supplied on the login screen
     * @return the authenticated user, or a typed failure
     */
    public ServiceResult<User> login(String username, String password) {
        if (username == null || username.isBlank()) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Enter your username.", "username");
        }
        if (password == null || password.isBlank()) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Enter your password.", "password");
        }

        Optional<User> found = this.userRepository.findByUsername(username);
        if (found.isEmpty()) {
            this.auditService.record(username, "LOGIN_FAILED", "User", username,
                                "Unknown account");
            return ServiceResult.failure(ErrorCode.PERMISSION_DENIED,
                    "Username or password is incorrect.");
        }

        User user = found.get();
        if (!user.isActive()) {
            this.auditService.record(username, "LOGIN_REFUSED", "User",
                                user.getUserId(), 
                    "Account disabled");
            return ServiceResult.failure(ErrorCode.PERMISSION_DENIED,
                    "This account has been disabled. Contact an "
                            + "administrator.");
        }

        if (!PasswordHasher.matches(password, user.getPasswordSalt(),
                user.getPasswordHash())) {
            this.auditService.record(username, "LOGIN_FAILED", "User",
                                user.getUserId(), 
                    "Incorrect password");
            return ServiceResult.failure(ErrorCode.PERMISSION_DENIED,
                    "Username or password is incorrect.");
        }

        if (PasswordHasher.needsRehash(user.getPasswordHash())) {
            // The password is known to be right only now, so this is the
            // one moment an old SHA-256 hash can be upgraded to PBKDF2.
            String salt = PasswordHasher.generateSalt();
            user.setPasswordSalt(salt);
            user.setPasswordHash(PasswordHasher.hash(password, salt));
        }
        user.setLastLoginAt(LocalDateTime.now());
        this.userRepository.save(user);
        this.auditService.record(username, "LOGIN_SUCCESS", "User",
                user.getUserId(),
                "Role " + user.getRole().name());
        return ServiceResult.success(user);
    }

    /**
     * Checks that a user holds one of the roles permitted for a command.
     *
     * @param user     the signed-in user, may be null
     * @param permitted the roles allowed to perform the command
     * @return true when the user may proceed
     */
    public boolean isAuthorised(User user, UserRole... permitted) {
        if (user == null || !user.isActive()) {
            return false;
        }
        for (UserRole role : permitted) {
            if (user.getRole() == role) {
                return true;
            }
        }
        return false;
    }

    /**
     * Creates a staff account with a salted password hash (UC-14).
     *
     * @param username     unique account name
     * @param fullName     display name
     * @param role         least-privilege role
     * @param rawPassword plaintext password, hashed immediately and never
     * stored
     * @return the created user, or a typed failure when the username is taken
     */
    public ServiceResult<User> register(String username, String fullName,
                                        UserRole role, String rawPassword) {
        if (username == null || username.isBlank()) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Username is required.", "username");
        }
        if (rawPassword == null || rawPassword.length() < 8) {
            return ServiceResult.failure(ErrorCode.VALIDATION_ERROR,
                    "Password must be at least 8 characters.", "password");
        }
        if (this.userRepository.findByUsername(username).isPresent()) {
            return ServiceResult.failure(ErrorCode.CONFLICT,
                    "That username already exists.", "username");
        }

        User user = new User(null, username, fullName, role);
        String salt = PasswordHasher.generateSalt();
        user.setPasswordSalt(salt);
        user.setPasswordHash(PasswordHasher.hash(rawPassword, salt));
        User saved = this.userRepository.save(user);
        this.auditService.record(username, "CREATE_USER", "User",
                saved.getUserId(),
                "Role " + role.name());
        return ServiceResult.success(saved);
    }

    /**
     * Describes what this service does.
     *
     * @return a short description of this object
     */
    @Override
    public String toString() {
        return "AuthenticationService: signs staff in and checks their roles";
    }
}
