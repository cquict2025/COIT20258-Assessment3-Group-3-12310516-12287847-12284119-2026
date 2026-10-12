package au.edu.cqu.drs.client;

import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.service.ErrorCode;

/**
 * A {@link ModuleContext} for a client with no server connection: it knows
 * the user but answers every request with a failure.
 *
 * <p>It lets module screens open before the client is connected to the
 * server; the connected context replaces it in the client networking
 * task.</p>
 */
public class UnconnectedModuleContext implements ModuleContext {

    /** Message returned for every request. */
    public static final String NOT_CONNECTED =
            "Not connected to the DRS server.";

    private final User currentUser;

    /**
     * Creates the context.
     *
     * @param currentUser the signed-in user, or null on a public screen
     */
    public UnconnectedModuleContext(User currentUser) {
        this.currentUser = currentUser;
    }

    /**
     * Returns the signed-in user.
     *
     * @return the user, or null
     */
    @Override
    public User getCurrentUser() {
        return this.currentUser;
    }

    /**
     * Returns no token, as there is no server session.
     *
     * @return null
     */
    @Override
    public String getSessionToken() {
        return null;
    }

    /**
     * Refuses the request because there is no connection.
     *
     * @param request the request
     * @return a failed response with {@link ErrorCode#ADAPTER_UNAVAILABLE}
     */
    @Override
    public Response send(Request request) {
        return Response.failure(request.getRequestId(),
                ErrorCode.ADAPTER_UNAVAILABLE, NOT_CONNECTED);
    }

    /**
     * Describes the context.
     *
     * @return the user and the connection state
     */
    @Override
    public String toString() {
        String name = "nobody";
        if (this.currentUser != null) {
            name = this.currentUser.getUsername();
        }
        return "UnconnectedModuleContext: " + name + ", no server connection";
    }
}
