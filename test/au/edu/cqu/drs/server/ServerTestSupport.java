package au.edu.cqu.drs.server;

import au.edu.cqu.drs.model.DisasterReport;
import au.edu.cqu.drs.model.DisasterType;
import au.edu.cqu.drs.model.Location;
import au.edu.cqu.drs.protocol.ArgumentKey;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.LoginResult;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Properties;

/**
 * Shared helpers for the server tests: in-memory settings, a clock the test
 * can move forward, a valid report, and signing in through a dispatcher.
 */
public final class ServerTestSupport {

    /** Password of every demonstration account. */
    public static final String PASSWORD = "Password1";

    /**
     * Prevents instances; every member is static.
     */
    private ServerTestSupport() {
    }

    /**
     * Settings for an in-memory server on any free port.
     *
     * @param threads the number of worker threads
     * @return the settings
     * @throws IOException if the defaults cannot be read
     */
    public static ServerConfig memoryConfig(int threads) throws IOException {
        Properties properties = ServerConfig.defaults();
        properties.setProperty("repository", "memory");
        properties.setProperty("port", "0");
        properties.setProperty("threads", String.valueOf(threads));
        return new ServerConfig(properties);
    }

    /**
     * Builds a report that passes the report service's validation.
     *
     * @return the report
     */
    public static DisasterReport validReport() {
        return new DisasterReport(null, DisasterType.FLOOD,
                new Location("1 Test Street", "Laverton", -37.86, 144.77),
                LocalDateTime.now().minusHours(1), "Water over the road");
    }

    /**
     * Signs in a demonstration account through a dispatcher.
     *
     * @param dispatcher the dispatcher
     * @param username   coordinator, liaison, responder or admin
     * @return the session token
     */
    public static String login(RequestDispatcher dispatcher,
                               String username) {
        Response response = dispatcher.dispatch(loginRequest(username));
        if (!response.isSuccess()) {
            throw new IllegalStateException("Login failed: " + response);
        }
        return ((LoginResult) response.getValue()).getSessionToken();
    }

    /**
     * Builds a LOGIN request for a demonstration account.
     *
     * @param username the account name
     * @return the request
     */
    public static Request loginRequest(String username) {
        return new Request(Command.LOGIN, null)
                .put(ArgumentKey.USERNAME, username)
                .put(ArgumentKey.PASSWORD, PASSWORD);
    }

    /**
     * Describes this helper class.
     *
     * @return a short description
     */
    @Override
    public String toString() {
        return "ServerTestSupport";
    }

    /**
     * A clock that stands still until the test moves it forward.
     */
    public static class MutableClock extends Clock {

        private Instant now;

        /**
         * Creates the clock at the current time.
         */
        public MutableClock() {
            this.now = Instant.now();
        }

        /**
         * Moves the clock forward.
         *
         * @param amount how far to move
         */
        public void advance(Duration amount) {
            this.now = this.now.plus(amount);
        }

        /**
         * Returns the time zone.
         *
         * @return the system zone
         */
        @Override
        public ZoneId getZone() {
            return ZoneId.systemDefault();
        }

        /**
         * Returns this clock; the zone is fixed.
         *
         * @param zone ignored
         * @return this clock
         */
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        /**
         * Returns the current instant of this clock.
         *
         * @return the instant
         */
        @Override
        public Instant instant() {
            return this.now;
        }

        /**
         * Describes the clock.
         *
         * @return the current instant
         */
        @Override
        public String toString() {
            return "MutableClock at " + this.now;
        }
    }
}
