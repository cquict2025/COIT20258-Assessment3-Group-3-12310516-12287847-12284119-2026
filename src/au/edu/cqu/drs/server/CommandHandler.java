package au.edu.cqu.drs.server;

import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;

/**
 * Performs one {@link au.edu.cqu.drs.protocol.Command} on the server.
 *
 * <p>The dispatcher has already checked the session and the
 * {@link AuthorisationPolicy} before a handler runs. Any exception a handler
 * throws is turned into a failed {@link Response} by the dispatcher, so a
 * stack trace never reaches the client.</p>
 */
@FunctionalInterface
public interface CommandHandler {

    /**
     * Performs the command.
     *
     * @param request the request, with its arguments
     * @param user    the signed-in user, or null for a public command
     * @return the response to send back
     * @throws Exception if the command cannot be completed
     */
    Response handle(Request request, User user) throws Exception;
}
