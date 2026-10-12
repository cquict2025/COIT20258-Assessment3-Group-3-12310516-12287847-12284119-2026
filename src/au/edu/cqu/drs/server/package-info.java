/**
 * The server tier of DRS-Enhanced.
 *
 * <p>The server receives {@link au.edu.cqu.drs.protocol.Request}s from
 * clients, checks them against an
 * {@link au.edu.cqu.drs.server.AuthorisationPolicy} and routes each
 * {@link au.edu.cqu.drs.protocol.Command} to a
 * {@link au.edu.cqu.drs.server.CommandHandler}. Core handlers call the
 * existing services; further handlers come from
 * {@link au.edu.cqu.drs.server.CommandModule}s found by the
 * {@link au.edu.cqu.drs.server.ModuleRegistry}, so new features plug in
 * without changes to the classes in this package.</p>
 */
package au.edu.cqu.drs.server;
