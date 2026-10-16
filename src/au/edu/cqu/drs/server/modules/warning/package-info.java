/**
 * The server module for Feature 1, public warnings and evacuation
 * notices.
 *
 * <p>The {@link au.edu.cqu.drs.server.modules.warning.WarningModule} is
 * listed in {@code server/modules/modules.properties}, so the server loads
 * it on start-up without changes to its own classes. It serves the six
 * warning commands (issue, update, cancel, acknowledge, the staff list and
 * the public list) by calling
 * {@link au.edu.cqu.drs.service.WarningService}, and stores warnings in
 * MySQL when the server runs on MySQL, or in memory otherwise.</p>
 */
package au.edu.cqu.drs.server.modules.warning;
