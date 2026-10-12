/**
 * Small helpers shared across the layers.
 *
 * <p>{@link au.edu.cqu.drs.util.PasswordHasher} hashes passwords with
 * PBKDF2 so that no password is stored or logged in plaintext,
 * {@link au.edu.cqu.drs.util.TimeParser} turns the 24-hour time text used on
 * the
 * forms into a date-time so that every screen treats an invalid time the same
 * way, and {@link au.edu.cqu.drs.util.ViewLoader} loads an FXML view and binds
 * it to a controller that has already been constructed.</p>
 */
package au.edu.cqu.drs.util;
