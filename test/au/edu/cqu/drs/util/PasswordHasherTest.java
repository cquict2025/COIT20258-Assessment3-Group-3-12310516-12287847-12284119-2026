package au.edu.cqu.drs.util;

import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.repository.memory.InMemoryAuditRepository;
import au.edu.cqu.drs.repository.memory.InMemoryUserRepository;
import au.edu.cqu.drs.service.AuditService;
import au.edu.cqu.drs.service.AuthenticationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for PBKDF2 password hashing and the upgrade of old hashes. */
class PasswordHasherTest {

    @Test
    @DisplayName("TC-SEC-01 the right password matches and a wrong one fails")
    void rightPasswordMatchesWrongFails() {
        String salt = PasswordHasher.generateSalt();
        String hash = PasswordHasher.hash("Password1", salt);

        assertTrue(hash.startsWith("pbkdf2-sha256$600000$"));
        assertTrue(PasswordHasher.matches("Password1", salt, hash));
        assertFalse(PasswordHasher.matches("password1", salt, hash));
        assertFalse(PasswordHasher.matches("", salt, hash));
        assertFalse(PasswordHasher.needsRehash(hash));
    }

    @Test
    @DisplayName("TC-SEC-02 the same password with two salts gives two "
            + "hashes, and the hash never contains the password")
    void saltsMakeHashesDiffer() {
        String first = PasswordHasher.hash("Password1",
                PasswordHasher.generateSalt());
        String second = PasswordHasher.hash("Password1",
                PasswordHasher.generateSalt());

        assertNotEquals(first, second);
        assertFalse(first.contains("Password1"));
    }

    @Test
    @DisplayName("TC-SEC-03 a DRS-Initial SHA-256 hash is still accepted and "
            + "flagged for upgrade")
    void legacyHashAcceptedAndFlagged() throws Exception {
        String salt = PasswordHasher.generateSalt();
        String legacy = legacyHash("Password1", salt);

        assertTrue(PasswordHasher.matches("Password1", salt, legacy));
        assertFalse(PasswordHasher.matches("Wrong-pass", salt, legacy));
        assertTrue(PasswordHasher.needsRehash(legacy));
    }

    @Test
    @DisplayName("TC-SEC-04 signing in upgrades an old hash to PBKDF2")
    void loginUpgradesLegacyHash() throws Exception {
        InMemoryUserRepository users = new InMemoryUserRepository();
        AuthenticationService authentication = new AuthenticationService(
                users, new AuditService(new InMemoryAuditRepository()));
        User user = new User(null, "coordinator", "Alex Coordinator",
                UserRole.COORDINATOR);
        String oldSalt = PasswordHasher.generateSalt();
        user.setPasswordSalt(oldSalt);
        user.setPasswordHash(legacyHash("Password1", oldSalt));
        users.save(user);

        assertTrue(authentication.login("coordinator", "Password1")
                .isSuccess());

        User stored = users.findByUsername("coordinator").orElseThrow();
        assertFalse(PasswordHasher.needsRehash(stored.getPasswordHash()));
        assertNotEquals(oldSalt, stored.getPasswordSalt());
        assertTrue(authentication.login("coordinator", "Password1")
                .isSuccess(), "the upgraded hash must still let them in");
    }

    @Test
    @DisplayName("TC-SEC-05 a damaged hash or salt never matches")
    void damagedValuesNeverMatch() {
        String salt = PasswordHasher.generateSalt();

        assertFalse(PasswordHasher.matches("Password1", salt,
                "pbkdf2-sha256$abc$xyz"));
        assertFalse(PasswordHasher.matches("Password1", salt,
                "pbkdf2-sha256$600000"));
        assertFalse(PasswordHasher.matches("Password1", "not base64!",
                PasswordHasher.hash("Password1", salt)));
        assertFalse(PasswordHasher.matches(null, salt, "x"));
    }

    /** Builds a hash the way DRS-Initial did. */
    private static String legacyHash(String password, String salt)
            throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(
                digest.digest(password.getBytes(StandardCharsets.UTF_8)));
    }
}
