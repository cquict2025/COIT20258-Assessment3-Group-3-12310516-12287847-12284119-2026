package au.edu.cqu.drs.security;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Encrypts and decrypts personal details, such as a reporter's name and
 * contact, before they are stored (spec 2.5).
 *
 * <p>Each value is encrypted with AES-256 in GCM mode (NIST SP 800-38D)
 * under a fresh random 12-byte IV, so the same name stored twice gives two
 * different ciphertexts. GCM also authenticates: a ciphertext that has been
 * changed, or copied into a different column, fails to decrypt instead of
 * giving wrong text. The column name is bound in as additional data for
 * that reason.</p>
 *
 * <p>A stored value looks like {@code enc:v1:<base64 of IV + ciphertext>}.
 * A value without that prefix was stored before encryption was switched on,
 * for example by {@code sql/seed.sql}, and is returned unchanged.</p>
 *
 * <p>The key lives in a PKCS12 keystore on the server, which is never
 * committed. If the database is copied without the keystore, these columns
 * cannot be read. The cost is that they cannot be searched or indexed.</p>
 */
public final class FieldCipher {

    /** Marks an encrypted value. */
    public static final String PREFIX = "enc:v1:";

    /** Keystore entry that holds the key. */
    public static final String KEY_ALIAS = "drs-field-key";

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BITS = 256;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKey key;

    /**
     * Creates a cipher around a key.
     *
     * @param key a 256-bit AES key
     * @throws IllegalArgumentException if the key is not a 256-bit AES key
     */
    public FieldCipher(SecretKey key) {
        if (key == null || !"AES".equalsIgnoreCase(key.getAlgorithm())
                || key.getEncoded() == null
                || key.getEncoded().length * 8 != KEY_BITS) {
            throw new IllegalArgumentException("FieldCipher needs a 256-bit "
                    + "AES key.");
        }
        this.key = key;
    }

    /**
     * Generates a new random 256-bit AES key.
     *
     * @return the key
     */
    public static SecretKey generateKey() {
        try {
            KeyGenerator generator = KeyGenerator.getInstance("AES");
            generator.init(KEY_BITS, RANDOM);
            return generator.generateKey();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES is not available in this "
                    + "JVM", e);
        }
    }

    /**
     * Loads the key from a PKCS12 keystore, creating the keystore with a new
     * key the first time. A new file is made readable by its owner only
     * where the file system supports that.
     *
     * @param file     the keystore file, for example drs-keystore.p12
     * @param password the keystore password
     * @return a cipher using the stored key
     * @throws IllegalStateException if the file cannot be read or written,
     *                               or the password is wrong
     */
    public static FieldCipher fromKeyStore(Path file, char[] password) {
        try {
            KeyStore store = KeyStore.getInstance("PKCS12");
            KeyStore.PasswordProtection protection =
                    new KeyStore.PasswordProtection(password);
            if (Files.exists(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    store.load(in, password);
                }
                KeyStore.Entry entry = store.getEntry(KEY_ALIAS, protection);
                if (!(entry instanceof KeyStore.SecretKeyEntry)) {
                    throw new IllegalStateException(file + " has no "
                            + KEY_ALIAS + " entry.");
                }
                return new FieldCipher(
                        ((KeyStore.SecretKeyEntry) entry).getSecretKey());
            }
            SecretKey key = generateKey();
            store.load(null, password);
            store.setEntry(KEY_ALIAS, new KeyStore.SecretKeyEntry(key),
                    protection);
            try (OutputStream out = Files.newOutputStream(file)) {
                store.store(out, password);
            }
            ownerOnly(file);
            return new FieldCipher(key);
        } catch (IOException | GeneralSecurityException e) {
            throw new IllegalStateException("Cannot open the keystore " + file
                    + ": " + e.getMessage(), e);
        }
    }

    /**
     * Encrypts a value for one column.
     *
     * @param plaintext the value, may be null
     * @param column    the column it will be stored in
     * @return the stored form, or null for null
     */
    public String encrypt(String plaintext, String column) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, this.key,
                    new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(column.getBytes(StandardCharsets.UTF_8));
            byte[] sealed = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer joined = ByteBuffer.allocate(iv.length + sealed.length);
            joined.put(iv).put(sealed);
            return PREFIX
                    + Base64.getEncoder().encodeToString(joined.array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not encrypt " + column
                    + ".", e);
        }
    }

    /**
     * Decrypts a stored value. A value without the {@link #PREFIX} is
     * returned unchanged.
     *
     * @param stored the stored form, may be null
     * @param column the column it was read from
     * @return the plaintext, or null for null
     * @throws IllegalStateException if the value has been changed, belongs
     *                               to another column, or was encrypted
     *                               under another key
     */
    public String decrypt(String stored, String column) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored;
        }
        try {
            byte[] joined = Base64.getDecoder()
                    .decode(stored.substring(PREFIX.length()));
            if (joined.length <= IV_BYTES) {
                throw new IllegalStateException("The encrypted " + column
                        + " is too short.");
            }
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, this.key,
                    new GCMParameterSpec(TAG_BITS, joined, 0, IV_BYTES));
            cipher.updateAAD(column.getBytes(StandardCharsets.UTF_8));
            byte[] plain = cipher.doFinal(joined, IV_BYTES,
                    joined.length - IV_BYTES);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("The stored " + column
                    + " could not be decrypted: it was changed, or the key "
                    + "is not the one it was encrypted with.", e);
        }
    }

    /**
     * Restricts a key file so only its owner can read and write it. On a file
     * system without POSIX permissions, such as Windows, it leaves the file as
     * it is.
     *
     * @param file the key file
     */
    private static void ownerOnly(Path file) {
        try {
            Files.setPosixFilePermissions(file,
                    PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException e) {
            // Windows has no POSIX permissions; the file inherits the
            // folder's access list instead.
        }
    }

    /**
     * Describes the cipher without revealing the key.
     *
     * @return the algorithm
     */
    @Override
    public String toString() {
        return "FieldCipher: AES-256-GCM";
    }
}
