package au.edu.cqu.drs.server;

import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.model.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link SessionManager}: tokens identify users until logout or
 * 30 minutes of inactivity.
 */
class SessionManagerTest {

    private final ServerTestSupport.MutableClock clock =
            new ServerTestSupport.MutableClock();
    private final SessionManager sessions =
            new SessionManager(this.clock, Duration.ofMinutes(30));
    private final User user =
            new User("USR-1", "coordinator", "Casey", UserRole.COORDINATOR);

    @Test
    @DisplayName("TC-SM-01 a token identifies its user and unknown tokens "
            + "identify nobody")
    void resolvesTokens() {
        String token = this.sessions.create(this.user);

        assertSame(this.user, this.sessions.resolve(token));
        assertNull(this.sessions.resolve("not-a-token"));
        assertNull(this.sessions.resolve(null));
        assertEquals(43, token.length());
    }

    @Test
    @DisplayName("TC-SM-02 tokens are unique")
    void tokensAreUnique() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(this.sessions.create(this.user));
        }

        assertEquals(1000, tokens.size());
    }

    @Test
    @DisplayName("TC-SM-03 a session expires after 30 idle minutes, and use "
            + "restarts the timer")
    void expiresWhenIdle() {
        String active = this.sessions.create(this.user);
        String idle = this.sessions.create(this.user);

        this.clock.advance(Duration.ofMinutes(20));
        assertSame(this.user, this.sessions.resolve(active));
        this.clock.advance(Duration.ofMinutes(20));

        assertSame(this.user, this.sessions.resolve(active));
        assertNull(this.sessions.resolve(idle));
    }

    @Test
    @DisplayName("TC-SM-04 logout ends a session and purging removes "
            + "expired ones")
    void removesSessions() {
        String loggedOut = this.sessions.create(this.user);
        this.sessions.create(this.user);

        assertTrue(this.sessions.remove(loggedOut));
        assertFalse(this.sessions.remove(loggedOut));
        assertNull(this.sessions.resolve(loggedOut));
        this.clock.advance(Duration.ofMinutes(31));
        assertEquals(1, this.sessions.purgeExpired());
        assertEquals(0, this.sessions.count());
        assertFalse(this.sessions.toString().contains(loggedOut));
    }
}
