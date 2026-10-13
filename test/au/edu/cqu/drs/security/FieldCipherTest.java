package au.edu.cqu.drs.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for AES-256-GCM encryption of personal details. */
class FieldCipherTest {

    @TempDir
    Path folder;

    private final FieldCipher cipher =
            new FieldCipher(FieldCipher.generateKey());

    @Test
    @DisplayName("TC-SEC-20 the ciphertext hides the plaintext and decrypts "
            + "back to it")
    void encryptsAndDecrypts() {
        String stored = this.cipher.encrypt("Priya Shah", "reporter_name");

        assertTrue(stored.startsWith(FieldCipher.PREFIX));
        assertFalse(stored.contains("Priya"));
        assertEquals("Priya Shah",
                this.cipher.decrypt(stored, "reporter_name"));
    }

    @Test
    @DisplayName("TC-SEC-21 the same value encrypts differently each time")
    void freshIvEveryTime() {
        assertNotEquals(this.cipher.encrypt("0400 111 222", "contact"),
                this.cipher.encrypt("0400 111 222", "contact"));
    }

    @Test
    @DisplayName("TC-SEC-22 a changed ciphertext is rejected, not decrypted "
            + "to wrong text")
    void tamperingIsDetected() {
        String stored = this.cipher.encrypt("Priya Shah", "reporter_name");
        byte[] bytes = Base64.getDecoder()
                .decode(stored.substring(FieldCipher.PREFIX.length()));
        bytes[bytes.length - 1] ^= 1;
        String tampered = FieldCipher.PREFIX
                + Base64.getEncoder().encodeToString(bytes);

        assertThrows(IllegalStateException.class,
                () -> this.cipher.decrypt(tampered, "reporter_name"));
    }

    @Test
    @DisplayName("TC-SEC-23 a value copied into another column, or read "
            + "with another key, is rejected")
    void wrongColumnOrKeyIsRejected() {
        String stored = this.cipher.encrypt("Priya Shah", "reporter_name");
        FieldCipher other = new FieldCipher(FieldCipher.generateKey());

        assertThrows(IllegalStateException.class,
                () -> this.cipher.decrypt(stored, "reporter_contact"));
        assertThrows(IllegalStateException.class,
                () -> other.decrypt(stored, "reporter_name"));
    }

    @Test
    @DisplayName("TC-SEC-24 null and values stored before encryption pass "
            + "through unchanged")
    void nullAndLegacyValues() {
        assertNull(this.cipher.encrypt(null, "reporter_name"));
        assertNull(this.cipher.decrypt(null, "reporter_name"));
        assertEquals("Tom Nguyen",
                this.cipher.decrypt("Tom Nguyen", "reporter_name"));
        assertFalse(this.cipher.toString().contains("key="));
    }

    @Test
    @DisplayName("TC-SEC-25 the keystore is created once and gives the same "
            + "key afterwards")
    void keystoreKeepsTheKey() {
        Path file = this.folder.resolve("drs-keystore.p12");
        char[] password = "keystore-test".toCharArray();

        String stored = FieldCipher.fromKeyStore(file, password)
                .encrypt("Priya Shah", "reporter_name");
        FieldCipher reopened = FieldCipher.fromKeyStore(file, password);

        assertTrue(Files.exists(file));
        assertEquals("Priya Shah", reopened.decrypt(stored, "reporter_name"));
        assertThrows(IllegalStateException.class,
                () -> FieldCipher.fromKeyStore(file,
                        "wrong-password".toCharArray()));
    }

    @Test
    @DisplayName("TC-SEC-26 only a 256-bit AES key is accepted")
    void weakKeyIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> new FieldCipher(null));
        assertThrows(IllegalArgumentException.class,
                () -> new FieldCipher(new javax.crypto.spec.SecretKeySpec(
                        new byte[16], "AES")));
    }
}
