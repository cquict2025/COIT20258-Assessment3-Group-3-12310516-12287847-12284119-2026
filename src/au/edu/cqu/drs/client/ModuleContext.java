package au.edu.cqu.drs.client;

import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;

/**
 * What a module's screen controller is given: who is signed in and a way to
 * send requests to the server.
 *
 * <p>A module controller declares a public constructor taking a
 * {@code ModuleContext}; {@link ModuleViewLoader} passes it in when the
 * module's FXML is loaded. See {@code docs/design/hooks.md}.</p>
 */
public interface ModuleContext {

    /**
     * Returns the signed-in user.
     *
     * @return the user, or null on a public screen
     */
    User getCurrentUser();

    /**
     * Returns the session token sent with each request.
     *
     * @return the token, or null when nobody is signed in
     */
    String getSessionToken();

    /**
     * Sends a request to the server and waits for the answer. Call it from
     * a background task, not the JavaFX thread.
     *
     * @param request the request
     * @return the server's response, or a failed response if the server
     *         cannot be reached
     */
    Response send(Request request);

    /**
     * Creates a request for a command carrying this session's token.
     *
     * @param command the command
     * @return a new request, ready for arguments
     */
    default Request newRequest(Command command) {
        return new Request(command, getSessionToken());
    }
}
