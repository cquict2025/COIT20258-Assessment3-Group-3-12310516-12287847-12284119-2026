package au.edu.cqu.drs.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Salted SHA-256 hashing so that no password is ever stored or logged in
 * plaintext (NFR-04). A production system would use an adaptive function such
 * as PBKDF2 or bcrypt; that is recorded as a known limitation of the prototype.
 */
public final class PasswordHasher {

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
        // Utility class - not instantiated.
    }

    /** Generates a new random salt encoded as Base64 text. */
    public static String generateSalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Hashes a password with the supplied salt.
     *
     * @param password the plaintext password, never stored
     * @param salt     the Base64 salt generated for the account
     * @return the Base64 encoded hash
     */
    public static String hash(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashed =
                    digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available in this "
                    + "JVM",
                                            
                                            
                                            
                                            e);
        }
    }

    /**
     * Verifies a candidate password against a stored salt and hash.
     *
     * @param candidate  the password entered by the user
     * @param salt       the stored salt
     * @param storedHash the stored hash
     * @return true when the candidate matches
     */
    public static boolean matches(String candidate, String salt,
                                  String storedHash) {
        if (candidate == null || salt == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(candidate, salt).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }
}
