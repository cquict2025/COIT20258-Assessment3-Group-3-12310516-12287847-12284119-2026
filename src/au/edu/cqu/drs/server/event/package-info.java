/**
 * The live operating picture (Feature 2): events the server pushes to every
 * signed-in client as the shared state changes.
 *
 * <p>The {@link au.edu.cqu.drs.server.RequestDispatcher} publishes a
 * {@link au.edu.cqu.drs.server.event.ServerEvent} on the
 * {@link au.edu.cqu.drs.server.event.EventBus} after every successful
 * state-changing command, and the
 * {@link au.edu.cqu.drs.server.event.EscalationMonitor} publishes one when
 * an incident's escalation state changes. The
 * {@link au.edu.cqu.drs.server.event.EventChannel} delivers each event to
 * the clients over a second socket, through a queue per client so that a
 * slow client never holds up the others.</p>
 */
package au.edu.cqu.drs.server.event;
