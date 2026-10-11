/**
 * SQLite adapters used by the running prototype.
 *
 * <p>{@link au.edu.cqu.drs.repository.sqlite.Database} owns the single
 * connection and creates the schema on first use. Each adapter implements one
 * repository interface with parameterised JDBC statements; date-times are
 * stored as ISO-8601 text because SQLite has no native timestamp type, which
 * keeps them both sortable and readable.</p>
 *
 * <p>An embedded database is a deliberate trade-off for a single-workstation
 * prototype: simple deployment and reliable local transactions were judged more
 * valuable than concurrent access.</p>
 */
package au.edu.cqu.drs.repository.sqlite;
