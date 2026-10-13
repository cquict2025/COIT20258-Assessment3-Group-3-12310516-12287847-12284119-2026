# Privacy and security (report §3.6)

Spec §2.5 asks for access rights, time stamps, encryption, decryption and
non-repudiation. The table maps each measure to the class that does it and
the automated test that proves it. Test IDs are the JUnit display names.

| Measure | What DRS-Enhanced does | Class | Proved by |
|---|---|---|---|
| **Access rights** | One role × command table that the **server** checks before every signed-in request; a command with no row is refused. Services check the role again. The public can submit reports and read warnings in force, nothing else. | `security/AccessMatrix`, applied by `server/AccessMatrixPolicy` | `AccessMatrixTest` TC-SEC-10…14; `WarningModuleTest` TC-WM-03; `A2DataImporterTest` TC-IM-03 |
| **Time stamps** | Every audit record, warning version and acknowledgement stores the **server's** time to the microsecond, never the client's. Each time stamp is inside the audit hash, so it cannot be changed unnoticed. | `security/AuditChain`, `MySqlAuditRepository`, `WarningService` | `AuditChainTest` TC-SEC-33; `MySqlRepositoryIntegrationTest` TC-MY-27 (a back-dated row breaks the chain) |
| **Encryption** | A reporter's name and contact are encrypted with **AES-256-GCM** before they reach MySQL: random 12-byte IV per value, column name as additional data. Passwords are hashed with **PBKDF2-HMAC-SHA256, 600,000 rounds** (OWASP); old SHA-256 hashes are upgraded at the next sign-in. | `security/FieldCipher`, `MySqlReportRepository`, `util/PasswordHasher` | `FieldCipherTest` TC-SEC-20, 21, 26; `PasswordHasherTest` TC-SEC-01…05; TC-MY-23 (no plaintext in the table) |
| **Decryption** | Values are decrypted only on the server, for staff screens. GCM rejects a changed value, one copied into another column, or one read with another key, instead of returning wrong text. The key is in a PKCS12 keystore (owner-only file, never committed); its password comes from an environment variable. | `security/FieldCipher` | `FieldCipherTest` TC-SEC-22…25 |
| **Non-repudiation** | Audit records form a **hash chain**: each stores SHA-256 of the record before plus all of its own fields. `verifyChain()` names the first record that was edited, deleted or back-dated. Rows are chained one at a time, so concurrent clients cannot fork it. | `security/AuditChain`, `MySqlAuditRepository.verifyChain()` | `AuditChainTest` TC-SEC-30…34; TC-MY-24…28 |
| **Privacy** | A public report's audit actor is always `public`, never the reporter's name. The public warning board hides who issued each warning. | `service/ReportService`, `service/WarningService` | `ReportServiceTest` TC-RP-12; `WarningServiceTest` TC-WS-15 |

Supporting measures from the client–server tier: session tokens are 32
random bytes and expire after 30 idle minutes; incoming objects pass a
deserialisation allow-list with depth and size limits; every SQL value is a
`PreparedStatement` parameter, so input is never joined into SQL.

## Limits

- **Client–server traffic is not encrypted.** The socket is plain, so the
  password at sign-in and the decrypted reporter details cross the network
  in clear. Wrapping the socket in TLS (`SSLServerSocket`) is the first
  thing to add before any real deployment.
- **Signatures are not implemented.** The hash chain proves the log was not
  changed afterwards, but who acted rests on the server's session, not on a
  key only the user holds. ECDSA signatures on critical actions were
  planned; the `user_keys` table and `Request.signature` field are ready for
  them. Future work.
- **The newest records can be cut off unnoticed:** nothing points at them
  yet. Copying the latest hash somewhere else (for example a daily printout)
  closes that gap. Someone with database access *and* the code could also
  rebuild the whole chain; the same outside copy defends against that.
- **Losing the keystore loses the reporter details.** Back it up, separately
  from the database.
- **Encrypted columns cannot be searched or indexed**, so staff cannot
  search reports by reporter name.
- **Access rights are per role, not per record:** any coordinator may act on
  any incident, which suits one emergency operations centre but not several
  agencies sharing a server.
