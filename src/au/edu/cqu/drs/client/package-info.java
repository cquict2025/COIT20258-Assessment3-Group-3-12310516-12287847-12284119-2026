/**
 * The client tier of DRS-Enhanced: what the JavaFX application needs to
 * talk to the server and to host screens supplied by feature modules.
 *
 * <p>{@link au.edu.cqu.drs.client.ModuleContext} is the only client type a
 * module's controller depends on, and
 * {@link au.edu.cqu.drs.client.ModuleViewLoader} opens a module's FXML only
 * when the module is installed.</p>
 */
package au.edu.cqu.drs.client;
