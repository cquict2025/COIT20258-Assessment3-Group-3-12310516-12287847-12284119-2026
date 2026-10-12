/**
 * Disaster Response System (DRS-Enhanced), the Assessment 3 client-server
 * system for COIT20258 Software Engineering, extended from the DRS-Initial
 * prototype of Assessment 2.
 *
 * <p>This package holds the JavaFX application,
 * {@link au.edu.cqu.drs.DrsApplication}, started by
 * {@code client.ClientLauncher}, and {@link au.edu.cqu.drs.DemoDataSeeder},
 * which the server uses to create the demonstration accounts on first run.
 * The server's wiring of repositories to services is
 * {@code server.ServerContext}.</p>
 *
 * <p>The system has three tiers. The JavaFX client follows
 * Model-View-Controller: the FXML views raise events to the controllers, and
 * the controllers send requests to the server. The multi-threaded server
 * runs the application services, which reach persistence only through the
 * repository interfaces.</p>
 */
package au.edu.cqu.drs;
