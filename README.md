# DRS-Initial — Disaster Response System prototype

COIT20258 Software Engineering, Assessment 2 (individual).
Venkatesh Kumar Manjunath, student number 12310516, Melbourne campus.

This is a NetBeans (Ant) Java project. Nothing needs to be installed except a
JDK: the JavaFX, SQLite and JUnit libraries are all included in `lib/`.

## Opening in NetBeans

**File → Open Project…** and select the `DRS-Initial` folder. NetBeans will show
it as a Java project with *Source Packages* and *Test Packages*.

| Action in NetBeans | What it does |
|---|---|
| Run Project (F6) | Starts the application (`au.edu.cqu.drs.DrsApplication`) |
| Test Project (Alt+F6) | Runs the 102 JUnit 5 tests |
| Clean and Build | Compiles and produces `dist/DRS-Initial.jar` |

## Building from a terminal

    ant run      # start the application
    ant test     # run the JUnit 5 test suite (102 tests)
    ant jar      # build dist/DRS-Initial.jar
    ant clean

## JavaFX and platforms

`lib/javafx/` contains the JavaFX 21.0.5 modules for Windows and for macOS on
Apple silicon. The `-pre-init` target in `build.xml` detects the machine it is
building on and puts the matching folder on the module path, so the project runs
as-is on either platform.

To run on macOS with an Intel processor or on Linux, download the matching
JavaFX 21.0.5 jars (`javafx-base`, `javafx-controls`, `javafx-fxml` and
`javafx-graphics`) and place them in `lib/javafx/mac` or `lib/javafx/linux`;
the build selects the folder automatically.

## Demonstration accounts

| Username    | Role                 | Password  |
|-------------|----------------------|-----------|
| coordinator | DRS coordinator      | Password1 |
| liaison     | Department liaison   | Password1 |
| responder   | Field responder      | Password1 |
| admin       | System administrator | Password1 |

Passwords are salted and hashed when the accounts are created; the plaintext
appears only in `DemoDataSeeder`, which exists to create them on first run.

A member of the public can submit a report without signing in, using the
**Report a disaster without signing in** button on the sign-in screen.

## Database

`drs.db` (SQLite) is created in the project folder on first run and the schema
is created automatically. Delete the file to reset the prototype.

## Project structure

    build.xml                 Ant build, with the JavaFX platform selection
    manifest.mf
    nbproject/                NetBeans project metadata
    lib/                      bundled libraries
        javafx/<platform>/    JavaFX 21.0.5 modules per platform
        sqlite-jdbc-*.jar     JDBC driver
        junit-platform-*.jar  JUnit 5 console launcher used by "ant test"
    src/                      application sources and FXML/CSS resources
        au/edu/cqu/drs/
            DrsApplication.java   JavaFX entry point
            AppContext.java       wires the SQLite adapters to the services
            model/                domain classes and enumerations
            service/              application services (all business rules)
            repository/           data-access interfaces + PartnerGateway
            repository/sqlite/    SQLite adapters used by the application
            repository/memory/    in-memory doubles used by the tests
            controller/           JavaFX controllers
            util/                 ViewLoader, TimeParser, PasswordHasher
            view/                 FXML views and app.css
    test/                     JUnit 5 test suite
    docs/                     UML diagrams and screen captures used in the report

## Coding standards

The source follows the unit's *Java Coding Standards*: four-space indentation
and no tabs, no line longer than 80 characters, one declaration and one
statement per line, the member order required by Rule 2, the naming rules for
classes, methods, variables and constants, instance variables qualified with
`this`, and Javadoc on every package, class and method.
