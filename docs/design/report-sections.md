# Report sections: Venkatesh Kumar Manjunath (12310516)

Draft text for sections 1, 3.1, 3.3 and 3.4 of the Assessment 3 report.
Figures are from the code on `main` on 15 October 2026. Diagrams are in
`docs/` and `docs/design/`, and each `.png` is built from the `.puml` file
beside it.

---

## 1. Selection of the DRS-Initial

We extended Venkatesh's Assessment 2 prototype. It was the best of the
team's three prototypes to build on, for four reasons.

**The business rules were already separate from the screens.** Every rule
(report validation, scoring and priority, incident lifecycle, department
coordination, escalation, duplicate detection and auditing) lives in eight
service classes. Controllers only collect input and show results. Moving
the rules to a server therefore meant moving the services, not untangling
them from JavaFX code.

**The services depended only on repository interfaces.** They were written
against `UserRepository`, `ReportRepository` and the other repository
interfaces, with SQLite and in-memory implementations behind them. A MySQL
implementation could be added without changing a single service, and that
is what Milan's data tier did.

**It was well tested.** The prototype came with 102 JUnit 5 tests, all
passing (we re-ran them on the original upload). They gave us a safety net
for the whole extension. Every one of them still passes, and the suite has
grown to 258.

**It followed the unit's coding standards** (Javadoc throughout,
`this.`-qualified fields, hand-written `toString()`, 80-column lines), so
new code could match it.

**What it lacked.** DRS-Initial was a single-user desktop program: the
screens called the services directly in one process. It had no network
layer, no way for two staff members to share the same data, nothing
thread-safe, and only an embedded SQLite file for storage. It saved
passwords as a salted SHA-256 hash (its own Javadoc noted PBKDF2 as the
proper choice) and personal details in plain text.
Assessment 3 adds the missing parts: a client-server split, a
multi-threaded server, MySQL, stronger security, and two new features
(public warnings and the live operating picture).

---

## 3.1 Architecture, design patterns and data structures

### Three tiers

DRS-Enhanced has three tiers (`docs/design/architecture-3tier.png`):

1. **Presentation: the JavaFX client.** Screens, controllers and a thin
   client model. It holds no business rules and never touches the
   database.
2. **Business logic: the multi-threaded server.** It accepts connections on
   port 5050, checks each request's session and permissions, and runs the
   services, where every rule is applied.
3. **Data: MySQL.** Only the server connects to it, through a pool of
   eight JDBC connections.

Because every rule runs on the server, all clients see the same data and
the same rules. A client cannot get round a rule by changing its own code,
and the database password never leaves the server.

### MVC on the client

The client keeps the Model-View-Controller structure of DRS-Initial
(`docs/mvc-architecture.png`):

- **View.** The FXML files and `app.css`.
- **Controller.** One class per screen. It reacts to user events, sends the
  request through `BackgroundCall` on a worker thread, and shows the
  result on the JavaFX thread, so the screen never freezes while waiting
  for the server.
- **Model.** On the client, the model is `RemoteServices`. It offers the
  same methods the controllers called before and returns the same
  `ServiceResult` type, but each method sends a request to the server.
  The controllers therefore needed little change.

### Design patterns

**Command.** Each operation is a constant of the `Command` enum carried in
a `Request` object, with its arguments in a map. On the server,
`RequestDispatcher` looks up the handler for the command and runs it. New
operations are added by adding a constant and a handler. Milan's warning
and import modules add their own commands this way, through the
`CommandModule` interface, without editing the dispatcher.
*Trade-off:* the arguments map is less type-safe than one class per
message. A missing or wrongly typed argument is found when the request
runs, not when the code compiles. The dispatcher turns such a mistake into
a clear validation error that names the field.

**Proxy.** `RemoteServices` stands in for the services on the server. A
controller calls `submitReport(report)` as before, and the proxy turns the
call into a network request.
*Trade-off:* the network is hidden, so a call can be slow or fail where a
local call could not. Every call therefore runs off the JavaFX thread and
can come back as "Server unavailable".

**Observer (Feature 2).** `EventBus` is the subject and each connected
client's `QueuedSubscriber` is an observer. After a successful change, the
dispatcher publishes a `ServerEvent` once, and the bus hands it to every
subscriber. The services do not know the event system exists.
*Trade-off:* the events say what changed (for example "Report RPT-0007
submitted"), not the new data. Clients reload what they need. This costs
one extra request per change, but it keeps personal details out of the
events and makes every read pass the normal permission checks.

**Repository and Strategy.** The services use repository interfaces. The
storage strategy (MySQL, SQLite or in-memory) is chosen by one setting,
`repository=`, when the server starts. Production runs on MySQL, while
most tests use the in-memory strategy and run in seconds with no database.
*Trade-off:* each strategy is a separate set of classes to maintain, and
behaviour can drift between them. The MySQL integration tests run the same
checks against a real database to catch that.

### Concurrency

The server accepts each connection on a `ServerSocket` and serves it on a
fixed pool of 32 worker threads (`ExecutorService`). A fixed pool limits
the memory and threads the server uses, however many clients connect. The
cost is that once all 32 are busy, a new client waits in the queue.

The dispatcher uses a fair `ReentrantReadWriteLock`. Read-only commands
share the read lock and run in parallel. Each command that changes data
takes the write lock and runs alone. This keeps the older SQLite and
in-memory adapters safe, because they were written for one user. The cost
is that writes run one at a time, which is acceptable at the number of
staff the system serves.

### Data structures

| Structure | Where | Why | Trade-off |
|---|---|---|---|
| `EnumMap<Command, CommandHandler>` | `RequestDispatcher` | Handler lookup by the enum's ordinal: fast, and ordered by command | Keys can only be `Command` constants, which is all that is needed |
| `ConcurrentHashMap<String, Session>` | `SessionManager` | Many worker threads look up tokens at once without one lock | No order, so finding idle sessions needs a sweep, which runs once a minute |
| `CopyOnWriteArrayList<EventSubscriber>` | `EventBus` | Publishing, which happens often, walks the list without locking | Every subscribe or unsubscribe copies the list, but clients rarely join or leave |
| `LinkedBlockingQueue<ServerEvent>`, capacity 500 | `QueuedSubscriber` | Each client has its own bounded queue, so a slow client cannot slow the others or exhaust memory | When a client falls 500 events behind, its oldest events are dropped (and logged); it then reloads its tables anyway |
| `ArrayDeque<ServerEvent>`, latest 50 | client `EventListener` | The activity feed keeps the newest events for screens opened later | Older events are not kept on the client |
| Database indexes | MySQL schema (Milan) | Lookups by code, status and role avoid full table scans | Each index slows inserts slightly and uses space |

---

## 3.3 Class diagrams

Two diagrams describe the classes.

- **`docs/domain-class-diagram.png`: the domain model.** The incident,
  report, assessment, assignment, field update, user and audit classes come
  from DRS-Initial. The `model.warning` package comes from Feature 1:
  `Warning`, `WarningAcknowledgement`, `EvacuationCentre`, `WarningLevel`
  and `WarningStatus`. Every model class now implements `Serializable` so
  it can travel between client and server.
- **`docs/mvc-architecture.png`: the whole system by tier.**
  - On the client, the views, the controllers and the client model
    (`RemoteServices`, `ClientSession`, `ServerConnection`, `EventListener`).
  - In the middle, the protocol shared by both tiers (`Request`,
    `Response`, `ProtocolFilter`).
  - On the server, the classes (`DrsServer`, `ClientHandler`,
    `RequestDispatcher`, `SessionManager`, `AccessMatrixPolicy`, the
    modules and the live picture classes), then the services and the
    repository interfaces with their three implementations.

---

## 3.4 Sequence diagrams

Four sequence diagrams follow the main flows through all three tiers.
Each is drawn from the merged code, not from a plan.

1. **Sign-in (`seq-login.png`).** The login runs on a background thread.
   The server checks the password (PBKDF2), records failed attempts in
   the audit log, and creates a random 32-byte session token. The client
   keeps the token and sends it with every later request. The password
   hash never leaves the server.
2. **A member of the public submits a report (`seq-submit-report.png`).**
   Submitting needs no sign-in. The service validates the report, the
   MySQL repository encrypts the reporter's name and contact before saving
   them, and the action is audited. Only after it succeeds does the
   dispatcher publish an event.
3. **Live push (`seq-live-push.png`).**
   - After sign-in, the client opens a second socket and subscribes.
   - A change made by anyone is queued for every subscriber and written on
     each subscriber's own thread. The receiving dashboard adds it to its
     feed and reloads its tables.
   - Once a minute the escalation monitor announces any incident whose
     state has changed, such as a P1 that has passed its 15-minute target.
4. **Issue a public warning (`seq-issue-warning.png`).** This is Milan's
   Feature 1 as it runs through the server. The dispatcher checks the
   access matrix (coordinators only), the warning module calls
   `WarningService`, the warning is validated and saved as version 1, and
   the change is published to every dashboard.

---

## References

Check each reference against the unit's referencing style before
submission. The URLs were not opened while writing this draft.

- Gamma, E, Helm, R, Johnson, R & Vlissides, J 1994, *Design patterns:
  elements of reusable object-oriented software*, Addison-Wesley, Reading,
  MA. (Command, Proxy, Observer, Strategy.)
- Fowler, M 2002, *Patterns of enterprise application architecture*,
  Addison-Wesley, Boston, MA. (Repository.)
- Goetz, B, Peierls, T, Bloch, J, Bowbeer, J, Holmes, D & Lea, D 2006,
  *Java concurrency in practice*, Addison-Wesley, Upper Saddle River, NJ.
  (Thread pools, concurrent collections, read-write locks.)
- Oracle n.d., *ObjectInputFilter (Java SE 17 & JDK 17)*, viewed
  [date], <https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/io/ObjectInputFilter.html>.
  **Not verified in this draft.** (Deserialisation allow-list.)
- OWASP n.d., *Deserialization cheat sheet*, OWASP Cheat Sheet Series,
  viewed [date],
  <https://cheatsheetseries.owasp.org/cheatsheets/Deserialization_Cheat_Sheet.html>.
  **Not verified in this draft.**
