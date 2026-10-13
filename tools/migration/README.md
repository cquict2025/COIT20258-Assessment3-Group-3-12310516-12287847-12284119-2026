# Loading the Assessment 2 data (W7)

Spec section 2.1 asks for the Assessment 2 data to be loaded into MySQL
*through the server*. `A2DataImporter` reads a DRS-Initial `drs.db` with
DRS-Initial's own SQLite code, signs in as an administrator and sends every
record to the server as an `IMPORT_*` request. It never connects to MySQL;
the server's `ImportModule` stores each record.

`a2-sample-drs.db` is a DRS-Initial database of sample data, written by
running DRS-Initial's own services on SQLite (`SampleA2Data`): 4 staff
accounts, 4 public reports (3 validated, 1 rejected as a duplicate),
3 incidents with assessments, a Fire and Emergency assignment with a
resource, 3 field updates and the audit trail.

## Run it

1. Start the server on MySQL (`repository=mysql` in `drs-server.properties`,
   with its keystore), so the import module is active.
2. Build: `ant jar`
3. Import (macOS/Linux; on Windows use `;` instead of `:` in the class
   path). Type the admin password when asked:

   ```
   java -cp "build/classes:lib/*" au.edu.cqu.drs.tools.migration.A2DataImporter tools/migration/a2-sample-drs.db localhost 5050 admin
   ```

   To import another DRS-Initial database, give its path instead.

The importer prints a count per table:

```
Table            Added  Already  Refused
users                4        0        0
reports              4        0        0
...
```

Run it a second time and every record shows under *Already*: the server
skips any code it already holds, so the import never duplicates or
overwrites data. Run it on a new database, before the system is used, so
Assessment 2 codes cannot clash with new records.

## Rules the server applies

- Records keep their codes (RPT-0001, INC-0001 ...) so links still hold.
- A user name that already exists is skipped; the server creates the
  demonstration accounts itself.
- Audit events join the hash chain under new codes, with the original code
  kept in the detail, for example `[A2 AUD-00012] Fire at Laverton`.
- Reporter names and contacts are encrypted like any other report, and a
  public report's audit actor is recorded as `public`.
- Only an administrator may send import commands.

## Make a fresh sample

```
java -cp "build/classes:lib/*" au.edu.cqu.drs.tools.migration.SampleA2Data new-sample.db
```
