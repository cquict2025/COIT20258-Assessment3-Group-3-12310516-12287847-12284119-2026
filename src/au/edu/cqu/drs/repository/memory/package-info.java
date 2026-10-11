/**
 * In-memory implementations of the repository interfaces, used by the tests.
 *
 * <p>Each double stores its records in a map or list and assigns the same style
 * of identifier as the SQLite adapter, so a service cannot tell the two apart.
 * {@link au.edu.cqu.drs.repository.memory.SimulatedPartnerGateway} stands in
 * for
 * the partner departments and can be switched to fail on demand, which is how
 * the "delivery failed, retry required" behaviour is demonstrated and
 * tested.</p>
 */
package au.edu.cqu.drs.repository.memory;
