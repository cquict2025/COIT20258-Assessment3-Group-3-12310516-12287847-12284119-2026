/**
 * Data-access contracts and the outbound partner contract.
 *
 * <p>Every interface in this package is implemented twice: by a SQLite adapter
 * in {@link au.edu.cqu.drs.repository.sqlite} that the running application
 * uses, and by an in-memory double in {@link au.edu.cqu.drs.repository.memory}
 * that the tests use. {@link au.edu.cqu.drs.repository.PartnerGateway} is the
 * single outbound contract to the partner departments named in the case
 * study.</p>
 *
 * <p>Keeping these as interfaces is what makes the persistence choice
 * replaceable: a shared database or a real departmental interface can be
 * introduced later without changing a service or a controller.</p>
 */
package au.edu.cqu.drs.repository;
