package au.edu.cqu.drs.util;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing with PBKDF2-HMAC-SHA256, so that no password is ever
 * stored or logged in plaintext (NFR-04).
 *
 * <p>DRS-Initial used one round of salted SHA-256 and recorded it as a known
 * limitation: a fast hash lets an attacker who copies the user table try
 * billions of guesses a second. PBKDF2 makes every guess cost
 * {@value #ITERATIONS} rounds, the figure the OWASP Password Storage Cheat
 * Sheet gives for PBKDF2-HMAC-SHA256 (NIST SP 800-132 describes the
 * function).</p>
 *
 * <p>A stored hash names its own algorithm and work factor, for example
 * {@code pbkdf2-sha256$600000$...}. Hashes from DRS-Initial, which have no
 * prefix, are still accepted, and {@link #needsRehash(String)} tells the
 * login code to replace them with a PBKDF2 hash once the password has been
 * checked.</p>
 */
public final class PasswordHasher {

    /** PBKDF2 rounds for new hashes (OWASP, PBKDF2-HMAC-SHA256). */
    public static final int ITERATIONS = 600_000;

    /** Prefix that marks a PBKDF2 hash. */
    public static final String PBKDF2_PREFIX = "pbkdf2-sha256$";

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Prevents instances; every member is static.
     */
    private PasswordHasher() {
        // Utility class - not instantiated.
    }

    /**
     * Generates a new random salt.
     *
     * @return 16 random bytes encoded as Base64 text
     */
    public static String generateSalt() {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        return Base64.getEncoder().encodeToString(salt);
    }

    /**
     * Hashes a password with PBKDF2 and the supplied salt.
     *
     * @param password the plaintext password, never stored
     * @param salt     the Base64 salt generated for the account
     * @return the hash, in the form {@code pbkdf2-sha256$<rounds>$<base64>}
     */
    public static String hash(String password, String salt) {
        return PBKDF2_PREFIX + ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(
                        pbkdf2(password, salt, ITERATIONS));
    }

    /**
     * Verifies a candidate password against a stored salt and hash. Both
     * PBKDF2 hashes and DRS-Initial's SHA-256 hashes are accepted. The
     * comparison takes the same time wherever the first difference is.
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
        byte[] expected;
        byte[] actual;
        if (storedHash.startsWith(PBKDF2_PREFIX)) {
            String[] parts = storedHash.substring(PBKDF2_PREFIX.length())
                    .split("\\$", 2);
            try {
                int rounds = Integer.parseInt(parts[0]);
                if (rounds < 1 || parts.length < 2) {
                    return false;
                }
                expected = Base64.getDecoder().decode(parts[1]);
                actual = pbkdf2(candidate, salt, rounds);
            } catch (IllegalArgumentException e) {
                // A damaged hash or salt can never match.
                return false;
            }
        } else {
            expected = storedHash.getBytes(StandardCharsets.UTF_8);
            actual = legacySha256(candidate, salt)
                    .getBytes(StandardCharsets.UTF_8);
        }
        boolean same = MessageDigest.isEqual(expected, actual);
        Arrays.fill(actual, (byte) 0);
        return same;
    }

    /**
     * Tells whether a stored hash should be replaced at the next successful
     * login: it is a DRS-Initial SHA-256 hash, or a PBKDF2 hash made with a
     * different number of rounds from {@link #ITERATIONS}.
     *
     * @param storedHash the stored hash
     * @return true when the hash is out of date
     */
    public static boolean needsRehash(String storedHash) {
        return storedHash == null
                || !storedHash.startsWith(PBKDF2_PREFIX + ITERATIONS + "$");
    }

    /**
     * Derives a PBKDF2-HMAC-SHA256 key from a password, clearing the copy of
     * the password afterwards.
     *
     * @param password the password
     * @param salt     the salt, Base64 encoded
     * @param rounds   the number of PBKDF2 rounds
     * @return the 256-bit derived key
     */
    private static byte[] pbkdf2(String password, String salt, int rounds) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars,
                Base64.getDecoder().decode(salt), rounds, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM)
                    .generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(ALGORITHM
                    + " is not available in this JVM", e);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }

    /**
     * DRS-Initial's hash: SHA-256 of the salt text followed by the password.
     * Kept only to verify accounts created before the upgrade.
     *
     * @param password the password typed
     * @param salt     the account's stored salt
     * @return the Base64 hash
     */
    private static String legacySha256(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] hashed =
                    digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available in "
                    + "this JVM", e);
        }
    }

    /**
     * Describes the hashing used for new passwords.
     *
     * @return the algorithm and number of rounds
     */
    @Override
    public String toString() {
        return "PasswordHasher[" + ALGORITHM + ", " + ITERATIONS
                + " rounds]";
    }
}
