package au.edu.cqu.drs.server;

import au.edu.cqu.drs.model.User;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues and checks session tokens for signed-in users.
 *
 * <p>A token is 32 bytes from {@link SecureRandom}, so it cannot be guessed,
 * and maps to the user in a {@link ConcurrentHashMap} that every client
 * thread can read safely. A session ends at logout or once it has been idle
 * longer than the configured time; each successful use restarts the idle
 * timer.</p>
 */
public class SessionManager {

    /** Bytes of randomness in a token. */
    public static final int TOKEN_BYTES = 32;

    private final SecureRandom random;
    private final Clock clock;
    private final Duration idleLimit;
    private final Map<String, Session> sessions;

    /**
     * Creates a session manager.
     *
     * @param clock     the clock used to measure idle time
     * @param idleLimit how long a session may stay idle
     */
    public SessionManager(Clock clock, Duration idleLimit) {
        this.random = new SecureRandom();
        this.clock = clock;
        this.idleLimit = idleLimit;
        this.sessions = new ConcurrentHashMap<>();
    }

    /**
     * Starts a session for a user who has just signed in.
     *
     * @param user the user
     * @return the new token
     */
    public String create(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        this.random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(bytes);
        this.sessions.put(token, new Session(user, this.clock.instant()));
        return token;
    }

    /**
     * Finds the user for a token and restarts its idle timer.
     *
     * @param token the token sent by the client, may be null
     * @return the user, or null if the token is unknown or has expired
     */
    public User resolve(String token) {
        if (token == null) {
            return null;
        }
        Session session = this.sessions.get(token);
        if (session == null) {
            return null;
        }
        Instant now = this.clock.instant();
        if (session.isExpired(now, this.idleLimit)) {
            this.sessions.remove(token, session);
            return null;
        }
        session.touch(now);
        return session.getUser();
    }

    /**
     * Ends a session.
     *
     * @param token the token to remove
     * @return {@code true} if a session was ended
     */
    public boolean remove(String token) {
        if (token == null) {
            return false;
        }
        return this.sessions.remove(token) != null;
    }

    /**
     * Removes every session that has been idle too long.
     *
     * @return the number removed
     */
    public int purgeExpired() {
        Instant now = this.clock.instant();
        int before = this.sessions.size();
        this.sessions.values().removeIf(
                session -> session.isExpired(now, this.idleLimit));
        return before - this.sessions.size();
    }

    /**
     * Counts the sessions held, including any not yet purged.
     *
     * @return the number of sessions
     */
    public int count() {
        return this.sessions.size();
    }

    /**
     * Describes the manager without revealing any token.
     *
     * @return the session count and idle limit
     */
    @Override
    public String toString() {
        return "SessionManager: " + this.sessions.size()
                + " session(s), idle limit " + this.idleLimit.toMinutes()
                + " min";
    }

    /**
     * One signed-in user and when the session was last used.
     */
    private static final class Session {

        private final User user;
        private volatile Instant lastUsed;

        /**
         * Creates a session.
         *
         * @param user     the signed-in user
         * @param lastUsed when the session started
         */
        private Session(User user, Instant lastUsed) {
            this.user = user;
            this.lastUsed = lastUsed;
        }

        /**
         * Returns the signed-in user.
         *
         * @return the user
         */
        private User getUser() {
            return this.user;
        }

        /**
         * Records a use of the session.
         *
         * @param now the current time
         */
        private void touch(Instant now) {
            this.lastUsed = now;
        }

        /**
         * Tells whether the session has been idle too long.
         *
         * @param now       the current time
         * @param idleLimit the idle limit
         * @return {@code true} once the limit has passed
         */
        private boolean isExpired(Instant now, Duration idleLimit) {
            return Duration.between(this.lastUsed, now)
                    .compareTo(idleLimit) > 0;
        }

        /**
         * Describes the session.
         *
         * @return the username and last use
         */
        @Override
        public String toString() {
            return "Session of " + this.user.getUsername() + ", last used "
                    + this.lastUsed;
        }
    }
}
