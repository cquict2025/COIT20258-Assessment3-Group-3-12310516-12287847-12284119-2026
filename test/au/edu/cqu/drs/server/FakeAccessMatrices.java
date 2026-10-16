package au.edu.cqu.drs.server;

import au.edu.cqu.drs.model.UserRole;
import au.edu.cqu.drs.protocol.Command;

/**
 * Stand-ins for the security module's access matrix, used by
 * {@link AuthorisationPolicyTest}.
 */
public final class FakeAccessMatrices {

    /**
     * Prevents instances; the matrices are nested classes.
     */
    private FakeAccessMatrices() {
    }

    /**
     * A matrix with a static method allowing only coordinators.
     */
    public static final class StaticMatrix {

        /**
         * Prevents instances; the method is static.
         */
        private StaticMatrix() {
        }

        /**
         * Allows coordinators only.
         *
         * @param role    the user's role
         * @param command the command
         * @return {@code true} for a coordinator
         */
        public static boolean isAllowed(UserRole role, Command command) {
            return role == UserRole.COORDINATOR;
        }

        /**
         * Describes the matrix.
         *
         * @return its name
         */
        @Override
        public String toString() {
            return "StaticMatrix";
        }
    }

    /**
     * A matrix with an instance method that lets responders list incidents
     * and nothing else.
     */
    public static class InstanceMatrix {

        /**
         * Allows responders to list incidents and nothing else.
         *
         * @param role    the user's role
         * @param command the command
         * @return {@code true} for a responder listing incidents
         */
        public boolean isAllowed(UserRole role, Command command) {
            return role == UserRole.RESPONDER
                    && command == Command.LIST_INCIDENTS;
        }

        /**
         * Describes the matrix.
         *
         * @return its name
         */
        @Override
        public String toString() {
            return "InstanceMatrix";
        }
    }

    /**
     * A matrix that always fails.
     */
    public static class FailingMatrix {

        /**
         * Throws, as a faulty matrix would.
         *
         * @param role    the user's role
         * @param command the command
         * @return never returns
         */
        public boolean isAllowed(UserRole role, Command command) {
            throw new IllegalStateException("matrix fault");
        }

        /**
         * Describes the matrix.
         *
         * @return its name
         */
        @Override
        public String toString() {
            return "FailingMatrix";
        }
    }

    /**
     * A matrix whose method returns the wrong type.
     */
    public static class WrongTypeMatrix {

        /**
         * Returns a String instead of a boolean.
         *
         * @param role    the user's role
         * @param command the command
         * @return a word
         */
        public String isAllowed(UserRole role, Command command) {
            return "yes";
        }

        /**
         * Describes the matrix.
         *
         * @return its name
         */
        @Override
        public String toString() {
            return "WrongTypeMatrix";
        }
    }

    /**
     * Describes this helper class.
     *
     * @return a short description of this object
     */
    @Override
    public String toString() {
        return "FakeAccessMatrices: stand-in access matrices for policy tests";
    }
}
