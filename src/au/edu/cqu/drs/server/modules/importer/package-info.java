/**
 * The server module for importing the Assessment 2 (DRS-Initial) data.
 *
 * <p>The {@link au.edu.cqu.drs.server.modules.importer.ImportModule}
 * serves the seven {@code IMPORT_*} commands, one record per request, and
 * only an administrator may send them. Each record is written to MySQL
 * through {@link au.edu.cqu.drs.repository.mysql.MySqlImportStore}, which
 * skips a record that is already stored, so an import can be run again
 * safely. Without MySQL and a field-encryption key every import command
 * is refused with a message saying why.</p>
 */
package au.edu.cqu.drs.server.modules.importer;
