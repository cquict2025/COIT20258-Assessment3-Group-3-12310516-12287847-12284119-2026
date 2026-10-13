/**
 * Privacy and security measures required by spec section 2.5.
 *
 * <p>{@link au.edu.cqu.drs.security.AccessMatrix} holds the access rights
 * that the server enforces on every request,
 * {@link au.edu.cqu.drs.security.FieldCipher} encrypts and decrypts
 * personal details before they reach the database, and
 * {@link au.edu.cqu.drs.security.AuditChain} links audit records with
 * hashes so that a record edited or removed afterwards can be detected.</p>
 */
package au.edu.cqu.drs.security;
