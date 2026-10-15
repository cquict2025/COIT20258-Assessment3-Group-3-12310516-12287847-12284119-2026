# Test classes to screenshot for the report

For Fatiha, from Venkatesh. These are the JUnit 5 test classes on `main`
on 15 October 2026. The whole suite runs **258 tests**: 228 that need
nothing installed, and 30 that need MySQL. Without MySQL those 30 show as
*aborted* (skipped), not failed.

## How to run them

- **NetBeans:** right-click the project and choose **Test** (Alt+F6). For
  one class, right-click the file and choose **Test File** (Ctrl+F6). That
  gives a clean screenshot of a single class.
- **Terminal:** `ant test`. The summary at the end gives the totals for the
  "all tests pass" screenshot.
- **MySQL tests:** these need a `drs_test` account. The README, under
  *Testing*, gives the two SQL lines that create it. Take the totals
  screenshot with MySQL running, so it shows 258 successful and 0 aborted.

Each test has a display name starting with a test case ID (for example
`TC-EV-06`). Use those IDs in the report's test table.

## Client-server tier and Feature 2 (Venkatesh)

| Test class | Tests | IDs | What it shows |
|---|---|---|---|
| `protocol/ProtocolTest` | 12 | TC-PR | Requests and responses survive the trip through a byte stream |
| `protocol/ProtocolFilterTest` | 6 | TC-PF | The deserialisation allow-list rejects classes outside it |
| `server/RequestDispatcherTest` | 10 | TC-RD | Routing, permission denied, invalid token, public commands |
| `server/DrsServerTest` | 4 | TC-DS | 10 clients at once on a real socket; bad requests; settings |
| `server/SessionManagerTest` | 4 | TC-SM | Tokens, logout, 30-minute idle expiry |
| `server/AuthorisationPolicyTest` | 6 | TC-AP | Role rules and delegation to the access matrix |
| `server/ModuleRegistryTest` | 6 | TC-MR | Modules plug in; a missing module is skipped |
| `server/ServerConfigTest` | 3 | TC-CF-01..03 | MySQL settings; passwords from the environment stay hidden |
| `server/ServerOnMySqlTest` | 2 | TC-CF-04..05 | Whole server on MySQL: encryption, restart, modules (needs MySQL) |
| `server/event/EventBusTest` | 3 | TC-EV-01..03 | **Feature 2:** an event reaches 3 subscribers |
| `server/event/QueuedSubscriberTest` | 2 | TC-EV-04..05 | **Feature 2:** a stalled client does not delay the others |
| `server/event/EscalationMonitorTest` | 2 | TC-EV-06..07 | **Feature 2:** P1 Breached after 15 minutes, announced once |
| `server/event/EventChannelTest` | 3 | TC-EV-08..10 | **Feature 2:** live events over real sockets; connected count |
| `client/ServerConnectionTest` | 5 | TC-SC | Client connection, timeout and "Server unavailable" |
| `client/event/EventListenerTest` | 3 | TC-EV-11..13 | **Feature 2:** client receives events and reconnects |
| `client/ModuleViewLoaderTest` | 4 | TC-MV | Warning screens load if installed, otherwise a message |

## Data tier, security and Feature 1 (Milan)

| Test class | Tests | What it shows |
|---|---|---|
| `repository/mysql/MySqlRepositoryIntegrationTest` | 25 | Every MySQL repository against a real database (needs MySQL) |
| `repository/mysql/MySqlSchemaTest` | 6 | Schema and settings checks (mostly need MySQL) |
| `security/AccessMatrixTest` | 5 | Which role may run which command |
| `security/FieldCipherTest` | 7 | AES-GCM encryption of personal details |
| `security/AuditChainTest` | 5 | Hash-chained audit log detects tampering |
| `util/PasswordHasherTest` | 5 | PBKDF2 password hashing |
| `service/WarningServiceTest` | 16 | **Feature 1:** warning rules |
| `server/modules/warning/WarningModuleTest` | 6 | **Feature 1:** warnings through the server |
| `controller/warning/WarningScreenSafetyTest` | 2 | **Feature 1:** screen safety checks |
| `tools/migration/A2DataImporterTest` | 3 | Importing the Assessment 2 data |

## Business rules carried over from DRS-Initial

The prototype's original 102 tests, all still passing.

| Test class | Tests |
|---|---|
| `service/ReportServiceTest` | 12 |
| `service/CoordinationServiceTest` | 12 |
| `service/AssessmentServiceTest` | 11 (one is parameterised) |
| `service/IncidentServiceTest` | 10 |
| `service/DuplicateDetectionServiceTest` | 10 |
| `service/AuthenticationServiceTest` | 9 |
| `service/EscalationServiceTest` | 7 (one is parameterised) |
| `model/ModelBehaviourTest` | 11 |
| `repository/SqliteRepositoryIntegrationTest` | 8 |

The "Tests" column counts test methods. A parameterised test runs once per
row, so the totals printed by `ant test` are higher than these counts.
