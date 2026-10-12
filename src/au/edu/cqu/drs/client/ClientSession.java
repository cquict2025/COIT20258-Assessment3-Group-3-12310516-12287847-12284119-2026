package au.edu.cqu.drs.client;

import au.edu.cqu.drs.model.User;
import au.edu.cqu.drs.protocol.ArgumentKey;
import au.edu.cqu.drs.protocol.Command;
import au.edu.cqu.drs.protocol.LoginResult;
import au.edu.cqu.drs.protocol.Request;
import au.edu.cqu.drs.protocol.Response;
import au.edu.cqu.drs.service.ServiceResult;

/**
 * The client's sign-in state: the connection to the server, the session
 * token and the signed-in user.
 *
 * <p>It replaces {@code AppContext.currentUser} on the client side, and is
 * the {@link ModuleContext} given to module screens, so they share the same
 * connection and session as the core screens.</p>
 */
public class ClientSession implements ModuleContext, AutoCloseable {

    private final ServerConnection connection;
    private volatile String sessionToken;
    private volatile User currentUser;

    /**
     * Creates a session that is not signed in.
     *
     * @param connection the connection to the server
     */
    public ClientSession(ServerConnection connection) {
        this.connection = connection;
    }

    /**
     * Signs in and keeps the token for later requests.
     *
     * @param username the account name
     * @param password the password
     * @return the signed-in user, or the reason sign-in failed
     */
    public ServiceResult<User> login(String username, String password) {
        Response response = this.connection.send(
                new Request(Command.LOGIN, null)
                        .put(ArgumentKey.USERNAME, username)
                        .put(ArgumentKey.PASSWORD, password));
        if (!response.isSuccess()) {
            return response.toServiceResult(User.class);
        }
        LoginResult result = (LoginResult) response.getValue();
        this.sessionToken = result.getSessionToken();
        this.currentUser = result.getUser();
        return ServiceResult.success(this.currentUser);
    }

    /**
     * Ends the session on the server and forgets the user.
     */
    public void logout() {
        if (this.sessionToken != null) {
            this.connection.send(newRequest(Command.LOGOUT));
        }
        this.sessionToken = null;
        this.currentUser = null;
    }

    /**
     * Tells whether a user is signed in.
     *
     * @return {@code true} after a successful login
     */
    public boolean isSignedIn() {
        return this.currentUser != null;
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
     * Returns the session token.
     *
     * @return the token, or null when not signed in
     */
    @Override
    public String getSessionToken() {
        return this.sessionToken;
    }

    /**
     * Sends a request on the shared connection.
     *
     * @param request the request
     * @return the response, or a failed response if the server is
     *         unreachable
     */
    @Override
    public Response send(Request request) {
        return this.connection.send(request);
    }

    /**
     * Returns the connection to the server.
     *
     * @return the connection
     */
    public ServerConnection getConnection() {
        return this.connection;
    }

    /**
     * Closes the connection.
     */
    @Override
    public void close() {
        this.connection.close();
    }

    /**
     * Describes the session without revealing the token.
     *
     * @return the user and server
     */
    @Override
    public String toString() {
        String name = "nobody";
        if (this.currentUser != null) {
            name = this.currentUser.getUsername();
        }
        return "ClientSession: " + name + " on " + this.connection.getHost()
                + ":" + this.connection.getPort();
    }
}
