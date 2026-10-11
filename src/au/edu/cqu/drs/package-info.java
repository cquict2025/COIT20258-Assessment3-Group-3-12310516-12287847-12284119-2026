/**
 * Disaster Response System (DRS-Initial), the Assessment 2 prototype for
 * COIT20258 Software Engineering.
 *
 * <p>This package holds the application entry point and the wiring that joins
 * the layers together. {@link au.edu.cqu.drs.DrsApplication} starts JavaFX and
 * shows the first view, {@link au.edu.cqu.drs.AppContext} constructs the
 * concrete SQLite adapters and hands them to the services, and
 * {@link au.edu.cqu.drs.DemoDataSeeder} creates the demonstration accounts on
 * first run.</p>
 *
 * <p>The prototype is organised as a Model-View-Controller application over
 * three tiers. Dependencies run in one direction only: the FXML views raise
 * events to the controllers, the controllers send commands to the application
 * services, and the services reach persistence only through the repository
 * interfaces.</p>
 */
package au.edu.cqu.drs;
