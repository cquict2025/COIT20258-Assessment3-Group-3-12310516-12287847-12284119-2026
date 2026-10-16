package au.edu.cqu.drs.security;

import au.edu.cqu.drs.model.AuditEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for the audit hash chain that need no database. */
class AuditChainTest {

    private static final LocalDateTime AT =
            LocalDateTime.of(2026, 10, 13, 9, 30, 15, 123_456_789);

    /**
     * Checks that the same record and link always give the same 64-character
     * hash.
     */
    @Test
    @DisplayName("TC-SEC-30 the same record and link always give the same "
            + "hash")
    void hashIsRepeatable() {
        String hash = AuditChain.hash(AuditChain.GENESIS, event("detail"));

        assertEquals(64, hash.length());
        assertEquals(hash, AuditChain.hash(AuditChain.GENESIS,
                event("detail")));
    }

    /**
     * Checks that changing the detail, the link or the time stamp changes the
     * hash.
     */
    @Test
    @DisplayName("TC-SEC-31 changing any field or the link changes the "
            + "hash")
    void everyFieldCounts() {
        String original = AuditChain.hash(AuditChain.GENESIS,
                event("VALIDATED"));

        assertNotEquals(original, AuditChain.hash(AuditChain.GENESIS,
                event("REJECTED")));
        assertNotEquals(original, AuditChain.hash("1".repeat(64),
                event("VALIDATED")));
        AuditEvent later = event("VALIDATED");
        later.setOccurredAt(AT.plusSeconds(1));
        assertNotEquals(original,
                AuditChain.hash(AuditChain.GENESIS, later));
    }

    /**
     * Checks that moving text from one field to the next does not give the
     * same hash.
     */
    @Test
    @DisplayName("TC-SEC-32 text moved between fields does not give the "
            + "same hash")
    void fieldBoundariesCount() {
        AuditEvent first = event("x");
        first.setEntityType("Incid");
        first.setEntityId("entINC-0001");
        AuditEvent second = event("x");
        second.setEntityType("Incident");
        second.setEntityId("INC-0001");

        assertNotEquals(AuditChain.hash(AuditChain.GENESIS, first),
                AuditChain.hash(AuditChain.GENESIS, second));
    }

    /**
     * Checks that time stamps are cut to microseconds, as the database stores
     * them, so a stored record hashes the same.
     */
    @Test
    @DisplayName("TC-SEC-33 time stamps are kept to the microsecond the "
            + "database stores")
    void stampsMatchTheDatabase() {
        assertEquals(123_456_000, AuditChain.stamp(AT).getNano());
        AuditEvent precise = event("x");
        AuditEvent stored = event("x");
        stored.setOccurredAt(AuditChain.stamp(AT));
        assertEquals(AuditChain.hash(AuditChain.GENESIS, precise),
                AuditChain.hash(AuditChain.GENESIS, stored));
    }

    /**
     * Checks that intact and broken verification results report their state
     * and describe themselves.
     */
    @Test
    @DisplayName("TC-SEC-34 a verification result explains itself")
    void verificationDescribesItself() {
        ChainVerification ok = ChainVerification.intact(5, 2);
        ChainVerification bad = ChainVerification.broken(1, 0, "AUD-00002",
                "its content was changed");

        assertTrue(ok.isIntact());
        assertTrue(ok.toString().contains("5 record(s)"));
        assertFalse(bad.isIntact());
        assertTrue(bad.toString().contains("AUD-00002"));
    }

    /**
     * Builds a report-decision audit event with code AUD-00001 at the fixed
     * test time.
     *
     * @param detail the event's detail
     * @return the audit event
     */
    private static AuditEvent event(String detail) {
        AuditEvent event = new AuditEvent("coordinator", "DECIDE_REPORT",
                "DisasterReport", "RPT-0001", detail, AT);
        event.setEventId("AUD-00001");
        return event;
    }

    /**
     * Describes this test class and the fixture it uses.
     *
     * @return the class name and the fixed time stamp it uses
     */
    @Override
    public String toString() {
        return "AuditChainTest[checks the audit hash chain, at=" + AT + "]";
    }
}
