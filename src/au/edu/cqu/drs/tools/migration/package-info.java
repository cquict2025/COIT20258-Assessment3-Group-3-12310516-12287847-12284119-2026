/**
 * Command-line tools for moving the Assessment 2 (DRS-Initial) data into
 * DRS-Enhanced.
 *
 * <p>{@link au.edu.cqu.drs.tools.migration.SampleA2Data} writes a sample
 * DRS-Initial SQLite database using DRS-Initial's own services.
 * {@link au.edu.cqu.drs.tools.migration.A2DataImporter} reads such a
 * database, signs in to the server as an administrator and sends every
 * record as an {@code IMPORT_*} request in dependency order; it never
 * connects to MySQL itself. The result is reported as an
 * {@link au.edu.cqu.drs.tools.migration.ImportSummary} of the records
 * added, already there and refused for each table.</p>
 */
package au.edu.cqu.drs.tools.migration;
