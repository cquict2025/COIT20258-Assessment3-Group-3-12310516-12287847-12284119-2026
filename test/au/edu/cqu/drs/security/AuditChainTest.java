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

    @Test
    @DisplayName("TC-SEC-30 the same record and link always give the same "
            + "hash")
    void hashIsRepeatable() {
        String hash = AuditChain.hash(AuditChain.GENESIS, event("detail"));

        assertEquals(64, hash.length());
        assertEquals(hash, AuditChain.hash(AuditChain.GENESIS,
                event("detail")));
    }

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

    private static AuditEvent event(String detail) {
        AuditEvent event = new AuditEvent("coordinator", "DECIDE_REPORT",
                "DisasterReport", "RPT-0001", detail, AT);
        event.setEventId("AUD-00001");
        return event;
    }
}
