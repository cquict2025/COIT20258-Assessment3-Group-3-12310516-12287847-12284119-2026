package au.edu.cqu.drs.client;

import au.edu.cqu.drs.DrsApplication;
import javafx.application.Application;

/**
 * Entry point of the DRS client.
 *
 * <p>Usage: {@code ClientLauncher [host] [port]}; the defaults are
 * {@code localhost} and {@code 5050}, and both can be changed on the sign-in
 * screen. Starting the server first is required: see
 * {@code au.edu.cqu.drs.server.ServerLauncher}.</p>
 *
 * <p>The launcher does not extend {@link Application} itself, so the client
 * also starts from a plain classpath, which is how NetBeans and
 * {@code ant run} start it.</p>
 */
public final class ClientLauncher {

    /**
     * Prevents instances; this class only holds {@link #main}.
     */
    private ClientLauncher() {
    }

    /**
     * Starts the JavaFX client.
     *
     * @param args optional server host and port
     */
    public static void main(String[] args) {
        Application.launch(DrsApplication.class, args);
    }

    /**
     * Describes the launcher.
     *
     * @return a short description
     */
    @Override
    public String toString() {
        return "ClientLauncher: starts the DRS client";
    }
}
