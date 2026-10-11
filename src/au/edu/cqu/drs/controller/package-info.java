/**
 * JavaFX controllers: the event-handling half of the Model-View-Controller
 * pattern.
 *
 * <p>A controller converts the values on its FXML view into a command, calls
 * the appropriate application service, and maps the typed result back onto the
 * screen, including moving the keyboard focus to the field an error concerns.
 * No controller performs validation, scoring or persistence.</p>
 *
 * <p>The FXML files deliberately omit the {@code fx:controller} attribute.
 * Each controller is constructed with the services it needs and attached with
 * {@code FXMLLoader.setController()}, so that a controller never has to find
 * its own dependencies.</p>
 */
package au.edu.cqu.drs.controller;
