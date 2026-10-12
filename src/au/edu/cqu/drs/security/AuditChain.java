package au.edu.cqu.drs.security;

import au.edu.cqu.drs.model.AuditEvent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;

/**
 * Hashes that link audit records into a chain, so a record that is edited
 * or removed afterwards can be detected (spec 2.5, time stamps and
 * non-repudiation).
 *
 * <p>Each record stores the hash of the record before it and its own hash:
 * SHA-256 over the previous hash and every field of the record, including
 * its time stamp. Changing any field changes that record's hash, which no
 * longer matches what is stored; deleting a record leaves the next one
 * pointing at a hash that is not there. Fields are length-prefixed before
 * hashing, so moving text from one field to the next cannot give the same
 * hash.</p>
 *
 * <p>The chain cannot show that the newest records were removed, because
 * nothing points at them yet. Keeping a copy of the latest hash somewhere
 * else, such as a daily printout, would close that gap.</p>
 */
public final class AuditChain {

    /** The previous hash of the first record in the chain. */
    public static final String GENESIS = "0".repeat(64);

    private AuditChain() {
        // Utility class - not instantiated.
    }

    /**
     * Rounds a time stamp to what the database keeps (microseconds), so the
     * hash made before saving matches the one made after reading back.
     *
     * @param time the time, may be null
     * @return the time to microsecond precision, or null
     */
    public static LocalDateTime stamp(LocalDateTime time) {
        return time == null ? null : time.truncatedTo(ChronoUnit.MICROS);
    }

    /**
     * Computes a record's hash.
     *
     * @param previousHash the hash of the record before, or {@link #GENESIS}
     * @param event        the record, with its code and time stamp set
     * @return 64 lower-case hex digits
     */
    public static String hash(String previousHash, AuditEvent event) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            add(digest, previousHash);
            add(digest, event.getEventId());
            add(digest, event.getActor());
            add(digest, event.getAction());
            add(digest, event.getEntityType());
            add(digest, event.getEntityId());
            add(digest, event.getDetail());
            LocalDateTime time = stamp(event.getOccurredAt());
            add(digest, time == null ? null : time.toString());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available in "
                    + "this JVM", e);
        }
    }

    /**
     * Adds one field: -1 for null, otherwise its byte length then its bytes.
     */
    private static void add(MessageDigest digest, String value) {
        if (value == null) {
            digest.update(new byte[] {-1, -1, -1, -1});
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        int length = bytes.length;
        digest.update(new byte[] {(byte) (length >>> 24),
            (byte) (length >>> 16), (byte) (length >>> 8), (byte) length});
        digest.update(bytes);
    }
}
