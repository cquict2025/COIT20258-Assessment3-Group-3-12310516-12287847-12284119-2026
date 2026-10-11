package au.edu.cqu.drs.protocol;

import au.edu.cqu.drs.model.Incident;
import au.edu.cqu.drs.service.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InvalidClassException;
import java.io.Serializable;
import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link ProtocolFilter}: classes outside the allow-list and
 * messages over the size limits are refused before they are rebuilt.
 */
class ProtocolFilterTest {

    private final ProtocolFilter filter = new ProtocolFilter();

    @Test
    @DisplayName("TC-PF-01 a class outside the allow-list in a request is "
            + "rejected")
    void foreignClassInPayloadIsRejected() {
        Request request = new Request(Command.PING, null)
                .put("counter", new AtomicInteger(1));

        assertThrows(InvalidClassException.class,
                () -> ProtocolTest.roundTrip(request, Request.class));
    }

    @Test
    @DisplayName("TC-PF-02 a foreign top-level object is rejected")
    void foreignTopLevelObjectIsRejected() {
        assertThrows(InvalidClassException.class,
                () -> ProtocolTest.roundTrip(new File("x"), Object.class));
    }

    @Test
    @DisplayName("TC-PF-03 the allow-list accepts the protocol's classes and "
            + "refuses others")
    void allowList() {
        assertTrue(this.filter.isAllowed(Request.class));
        assertTrue(this.filter.isAllowed(Incident.class));
        assertTrue(this.filter.isAllowed(ErrorCode.class));
        assertTrue(this.filter.isAllowed(String.class));
        assertTrue(this.filter.isAllowed(Integer.class));
        assertTrue(this.filter.isAllowed(LocalDateTime.class));
        assertTrue(this.filter.isAllowed(ArrayList.class));
        assertTrue(this.filter.isAllowed(byte[].class));
        assertTrue(this.filter.isAllowed(int[][].class));
        assertTrue(this.filter.isAllowed(Object[].class));
        assertTrue(this.filter.isAllowed(Incident[].class));

        assertFalse(this.filter.isAllowed(File.class));
        assertFalse(this.filter.isAllowed(Random.class));
        assertFalse(this.filter.isAllowed(AtomicInteger.class));
        assertFalse(this.filter.isAllowed(Proxy.class));
        assertFalse(this.filter.isAllowed(File[].class));
    }

    @Test
    @DisplayName("TC-PF-04 nesting deeper than the limit is rejected")
    void tooDeepIsRejected() {
        ArrayList<Serializable> outer = new ArrayList<>();
        ArrayList<Serializable> current = outer;
        for (int level = 0; level < ProtocolFilter.MAX_DEPTH; level++) {
            ArrayList<Serializable> inner = new ArrayList<>();
            current.add(inner);
            current = inner;
        }
        Response response = Response.success("deep", outer);

        assertThrows(InvalidClassException.class,
                () -> ProtocolTest.roundTrip(response, Response.class));
    }

    @Test
    @DisplayName("TC-PF-05 more objects than the limit are rejected")
    void tooManyReferencesIsRejected() {
        ArrayList<Integer> values = new ArrayList<>();
        for (int i = 0; i <= ProtocolFilter.MAX_REFERENCES; i++) {
            values.add(Integer.valueOf(1_000_000 + i));
        }
        Response response = Response.success("many", values);

        assertThrows(InvalidClassException.class,
                () -> ProtocolTest.roundTrip(response, Response.class));
    }

    @Test
    @DisplayName("TC-PF-06 an array of 1 MB is accepted and one byte more is "
            + "rejected")
    void arrayLengthLimit() throws Exception {
        int limit = (int) ProtocolFilter.MAX_ARRAY_LENGTH;
        Request atLimit = new Request(Command.PING, null);
        atLimit.setSignature(new byte[limit]);
        Request overLimit = new Request(Command.PING, null);
        overLimit.setSignature(new byte[limit + 1]);

        Request copy = ProtocolTest.roundTrip(atLimit, Request.class);

        assertEquals(limit, copy.getSignature().length);
        assertThrows(InvalidClassException.class,
                () -> ProtocolTest.roundTrip(overLimit, Request.class));
    }
}
