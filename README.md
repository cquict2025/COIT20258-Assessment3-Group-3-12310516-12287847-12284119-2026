# DRS-Enhanced — Disaster Response System

COIT20258 Software Engineering, Assessment 3, Group 3.
Extended from the DRS-Initial prototype of Venkatesh Kumar Manjunath
(12310516).

| Member | Student ID |
|---|---|
| Venkatesh Kumar Manjunath (team leader) | 12310516 |
| Milan | 12287847 |
| Fatiha | 12284119 |

This is a NetBeans (Ant) Java project. Nothing needs to be installed except a
JDK 17 or later: the JavaFX, SQLite and JUnit libraries are all included in
`lib/`.

## Running DRS-Enhanced

DRS-Enhanced has three tiers: a JavaFX client, a multi-threaded server that
runs every business rule, and the database behind the server. **Start the
server first**, then as many clients as you like.

| Step | NetBeans | Terminal |
|---|---|---|
| 1. Start the server | Right-click `au.edu.cqu.drs.server.ServerLauncher` → **Run File** | `ant run-server` |
| 2. Start a client | **Run Project (F6)** | `ant run-client` (or `ant run`) |
| 3. Start a second client | Right-click `au.edu.cqu.drs.client.ClientLauncher` → **Run File** | `ant run-client` in a second terminal |

The server opens a status window that shows its port, the number of connected
clients and a log. The log names the worker thread (`drs-worker-1`,
`drs-worker-2`, ...) that served each request. Closing the window stops the
server. To run without a window:

    ant run-server -Dserver.args=--headless

The sign-in screen has **Server** fields, which default to `localhost` and
`5050`. To connect to a server on another computer, type its address there,
or start the client with it:

    ant run-client "-Dclient.args=192.168.1.20 5050"

### Live operating picture

Once signed in, each client opens a second connection to the server and
receives every change as it happens. Sign in on two clients and submit a
report on one: the other's dashboard shows it in **Live activity** and
reloads its tables by itself. The dashboard also shows the number of
connected users and a red **BREACHED** banner for incidents past their
target response time. The server checks escalation once a minute. See
`docs/design/feature-2-note.md`.

## Server settings

The defaults are in `src/au/edu/cqu/drs/server/drs-server.properties`. To
change any of them, put a `drs-server.properties` file with the keys you want
in the folder the server is started from, or pass
`-Dserver.args="--config my.properties"`.

| Key | Default | Meaning |
|---|---|---|
| `port` | 5050 | TCP port clients connect to |
| `threads` | 32 | worker threads, which is the most clients served at once |
| `repository` | mysql | storage: `mysql`, `sqlite` or `memory` |
| `mysql.host`, `mysql.port` | localhost, 3306 | the MySQL server |
| `mysql.database`, `mysql.user` | drs, drs | the database and account |
| `mysql.password` | (empty) | better set with the `DRS_MYSQL_PASSWORD` environment variable |
| `keystore.file` | drs-keystore.p12 | key that encrypts reporters' names and contacts, created on first start |
| `keystore.password` | (empty) | better set with `DRS_KEYSTORE_PASSWORD`; without one, personal details are stored unencrypted and imports are refused |
| `database.file` | drs.db | SQLite file, created on first start (`repository=sqlite`) |
| `client.timeout.minutes` | 30 | a silent connection is closed after this |
| `session.idle.minutes` | 30 | an idle session must sign in again after this |

## Testing

| NetBeans | Terminal |
|---|---|
| **Test Project (Alt+F6)** | `ant test` |

The suite needs no window. The servers it starts use in-memory storage and a
free port. The MySQL tests use a `drs_test` database and are skipped, not
failed, when MySQL is not available. To run them, create the account once:

    CREATE USER 'drs_test'@'localhost' IDENTIFIED BY 'drs_test';
    GRANT ALL ON drs_test.* TO 'drs_test'@'localhost';

`DRS_TEST_MYSQL_HOST`, `DRS_TEST_MYSQL_PORT`, `DRS_TEST_MYSQL_USER` and
`DRS_TEST_MYSQL_PASSWORD` point them at another server.

## JavaFX and platforms

`lib/javafx/` contains the JavaFX 21.0.5 modules for Windows and for macOS on
Apple silicon. The `-pre-init` target in `build.xml` detects the machine it is
building on and puts the matching folder on the module path, so the project
runs as-is on either platform.

To run on macOS with an Intel processor or on Linux, download the matching
JavaFX 21.0.5 jars (`javafx-base`, `javafx-controls`, `javafx-fxml` and
`javafx-graphics`) and place them in `lib/javafx/mac` or `lib/javafx/linux`.
The build selects the folder automatically.

## Demonstration accounts

| Username    | Role                 | Password  |
|-------------|----------------------|-----------|
| coordinator | DRS coordinator      | Password1 |
| liaison     | Department liaison   | Password1 |
| responder   | Field responder      | Password1 |
| admin       | System administrator | Password1 |

The server creates these accounts the first time it starts with an empty
database. Passwords are salted and hashed, and the plaintext appears only in
`DemoDataSeeder`. The server never sends the hash to a client.

A member of the public can submit a report without signing in, using
**Report a disaster without signing in** on the sign-in screen.

## Database

The server runs on MySQL by default. Once, as the MySQL root user, create
the account and, optionally, the demonstration data:

    CREATE USER 'drs'@'localhost' IDENTIFIED BY 'choose-a-password';
    GRANT ALL ON drs.* TO 'drs'@'localhost';

    mysql -u root -p < sql/schema.sql
    mysql -u root -p drs < sql/seed.sql

Then start the server with the passwords in the environment, so they are
never written to a file:

    export DRS_MYSQL_PASSWORD='choose-a-password'
    export DRS_KEYSTORE_PASSWORD='another-password'
    ant run-server

If the schema scripts were not run, the server creates the database tables
and the demonstration accounts itself on first start. It also creates
`drs-keystore.p12`; keep that file and its password, because without them
the encrypted names and contacts cannot be read. Never commit or submit the
keystore.

With `repository=sqlite`, the server creates `drs.db` in the folder it is
started from and builds the schema automatically. Stop the server and delete
the file to reset the data. With `repository=memory`, nothing is saved.

## Project structure

    build.xml                 Ant build: JavaFX platform selection, test,
                              run-server and run-client targets
    nbproject/                NetBeans project metadata
    lib/                      bundled libraries
    src/au/edu/cqu/drs/
        DrsApplication.java   the JavaFX client application
        DemoDataSeeder.java   demonstration accounts
        protocol/             messages shared by client and server: Command,
                              Request, Response, ProtocolFilter (allow-list
                              for deserialisation), MessageStreams
        server/               ServerLauncher, DrsServer (thread pool),
                              ClientHandler, RequestDispatcher, CoreCommands,
                              SessionManager, ServerContext, ModuleRegistry,
                              AuthorisationPolicy
        server/event/         live events: ServerEvent, EventBus,
                              EventChannel, QueuedSubscriber,
                              EscalationMonitor
        server/modules/       modules.properties: feature modules plugged in
                              by reflection (see docs/design/hooks.md)
        client/               ClientLauncher, ServerConnection, ClientSession,
                              RemoteServices, BackgroundCall, ModuleViewLoader
        client/event/         EventListener for the live dashboard
        model/                domain classes (Serializable)
        service/              application services (all business rules)
        repository/           data-access interfaces, SQLite and in-memory
                              adapters
        controller/           JavaFX controllers
        util/                 ViewLoader, TimeParser, PasswordHasher
        view/                 FXML views and app.css
    test/                     JUnit 5 test suite
    docs/                     diagrams, design notes and screen captures


## coding guidelines
    The source complies with the unit's *Java Coding Standards*, which include four-space indentation, no tabs, a line length of no more than 80 characters , one declaration and one statement per line, 
