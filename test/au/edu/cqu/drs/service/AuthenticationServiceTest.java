package au.edu.cqu.drs.service;

import au.edu.cqu.drs.TestFixtures;
import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for authentication and least-privilege authorisation (FR-01,
        NFR-04). */
class AuthenticationServiceTest {

    private TestFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new TestFixtures();
    }

    @Test
    @DisplayName("TC-AU-01 correct credentials sign a user in and record the "
            + "login")
    void correctCredentialsSignIn() {
        fixtures.createUser("coordinator", UserRole.COORDINATOR);

        ServiceResult<User> result = fixtures.authenticationService
                .login("coordinator", TestFixtures.TEST_PASSWORD);

        assertTrue(result.isSuccess());
        assertNotNull(result.getValue().getLastLoginAt());
    }

    @Test
    @DisplayName("TC-AU-02 an incorrect password is refused without revealing "
            + "which field was wrong")
    void incorrectPasswordIsRefused() {
        fixtures.createUser("coordinator", UserRole.COORDINATOR);

        ServiceResult<User> result =
                fixtures.authenticationService.login("coordinator",
                                                     "WrongPassword");

        assertTrue(result.isFailure());
        assertEquals(ErrorCode.PERMISSION_DENIED, result.getErrorCode());
        assertEquals("Username or password is incorrect.", result.getMessage());
    }

    @Test
    @DisplayName("TC-AU-03 an unknown username is refused with the same "
            + "message")
    void unknownUsernameIsRefused() {
        ServiceResult<User> result =
                fixtures.authenticationService.login("nobody",
                                                     TestFixtures
                                                             .TEST_PASSWORD);

        assertTrue(result.isFailure());
        assertEquals("Username or password is incorrect.", result.getMessage());
    }

    @Test
    @DisplayName("TC-AU-04 a disabled account cannot sign in")
    void disabledAccountCannotSignIn() {
        User user = fixtures.createUser("liaison", UserRole.LIAISON);
        user.setActive(false);
        fixtures.userRepository.save(user);

        ServiceResult<User> result =
                fixtures.authenticationService.login("liaison",
                                                     TestFixtures
                                                             .TEST_PASSWORD);

        assertTrue(result.isFailure());
        assertTrue(result.getMessage().contains("disabled"));
    }

    @Test
    @DisplayName("TC-AU-05 the password is never stored in plaintext")
    void passwordIsNeverStoredInPlaintext() {
        User user = fixtures.createUser("admin", UserRole.ADMINISTRATOR);

        assertNotEquals(TestFixtures.TEST_PASSWORD, user.getPasswordHash());
        assertNotNull(user.getPasswordSalt());
        assertFalse(user.toString().contains(user.getPasswordHash()),
                "toString must not expose the stored hash");
    }

    @Test
    @DisplayName("TC-AU-06 two accounts with the same password receive "
            + "different hashes")
    void identicalPasswordsProduceDifferentHashes() {
        User first = fixtures.createUser("userone", UserRole.RESPONDER);
        User second = fixtures.createUser("usertwo", UserRole.RESPONDER);

        assertNotEquals(first.getPasswordHash(), second.getPasswordHash());
    }

    @Test
    @DisplayName("TC-AU-07 a password shorter than eight characters is refused")
    void shortPasswordIsRefused() {
        ServiceResult<User> result = fixtures.authenticationService
                .register("shorty", "Short Password", UserRole.RESPONDER,
                          "Pass1");

        assertTrue(result.isFailure());
        assertEquals("password", result.getAffectedField());
    }

    @Test
    @DisplayName("TC-AU-08 a duplicate username is refused as a conflict")
    void duplicateUsernameIsRefused() {
        fixtures.createUser("coordinator", UserRole.COORDINATOR);

        ServiceResult<User> result = fixtures.authenticationService
                .register("coordinator", "Second Account", UserRole.LIAISON,
                          "Password1");

        assertTrue(result.isFailure());
        assertEquals(ErrorCode.CONFLICT, result.getErrorCode());
    }

    @Test
    @DisplayName("TC-AU-09 authorisation permits only the listed roles")
    void authorisationPermitsOnlyListedRoles() {
        User responder = fixtures.createUser("responder", UserRole.RESPONDER);

        assertTrue(fixtures.authenticationService
                .isAuthorised(responder, UserRole.RESPONDER, UserRole.LIAISON));
        assertFalse(fixtures.authenticationService
                .isAuthorised(responder, UserRole.COORDINATOR));
        assertFalse(fixtures.authenticationService
                .isAuthorised(null, UserRole.RESPONDER));
    }
}
