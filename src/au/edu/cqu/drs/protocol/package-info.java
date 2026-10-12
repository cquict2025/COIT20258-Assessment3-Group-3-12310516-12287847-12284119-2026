/**
 * The message contract between the DRS client and the DRS server.
 *
 * <p>A client sends a {@link au.edu.cqu.drs.protocol.Request} naming one
 * {@link au.edu.cqu.drs.protocol.Command} and receives one
 * {@link au.edu.cqu.drs.protocol.Response}, which carries either a value or
 * the same typed error that the services return. Both are sent as Java
 * serialised objects through {@link au.edu.cqu.drs.protocol.MessageStreams},
 * and every incoming stream is checked by
 * {@link au.edu.cqu.drs.protocol.ProtocolFilter}, so only classes on the
 * allow-list can be rebuilt on the other side.</p>
 *
 * <p>The package has no JavaFX, socket server or database code, so the client,
 * the server and the modules written by other team members can all depend on
 * it.</p>
 */
package au.edu.cqu.drs.protocol;
