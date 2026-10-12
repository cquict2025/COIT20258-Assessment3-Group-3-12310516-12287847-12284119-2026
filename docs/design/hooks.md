# Plugging a module into DRS-Enhanced

For: Milan (warning module, import module, access matrix).

Your code plugs in without any edits to the server, client or controller
classes. The server, the client and the build work whether or not your code
is there yet. All classes named below are on `feature/hooks` (PR stacked on
#1).

## 1. Server: a `CommandModule`

```java
package au.edu.cqu.drs.server.modules.warning;

public class WarningModule implements CommandModule {

    public WarningModule(ServerContext context) { ... } // or no arguments

    @Override
    public Map<Command, CommandHandler> handlers() {
        return Map.of(
                Command.ISSUE_WARNING, this::issue,
                Command.LIST_PUBLIC_WARNINGS, this::listPublic);
    }

    private Response issue(Request request, User user) throws Exception {
        WarningNotice notice = request.get("notice", WarningNotice.class);
        ServiceResult<WarningNotice> result =
                this.warningService.issue(user, notice);
        return Response.fromServiceResult(request.getRequestId(), result);
    }
}
```

- `CommandHandler` is `Response handle(Request request, User user)`. By the
  time it runs, the dispatcher has already checked the session token and the
  authorisation policy. `user` is null only for public commands
  (`LIST_PUBLIC_WARNINGS`).
- Any exception you throw becomes a failed `Response`. No stack trace reaches
  the client.
- Constructor: the registry calls a public constructor that takes the server
  context if you have one, otherwise the no-argument constructor. The context
  type (`ServerContext`, with the repositories and services) arrives with the
  server task (T3). Until then, use the no-argument constructor.
- A command that already has a handler can't be taken over. The registry
  logs it and keeps the first handler.
- The commands are already in `protocol/Command`: `ISSUE_WARNING`,
  `UPDATE_WARNING`, `CANCEL_WARNING`, `ACK_WARNING`, `LIST_WARNINGS`,
  `LIST_PUBLIC_WARNINGS` and `IMPORT_USER` ... `IMPORT_AUDIT`. If you need a
  new one, ask Venkatesh to add it to `Command`.
- Anything sent in a request or response must be `Serializable`, with
  `serialVersionUID = 1L`, and must live in `au.edu.cqu.drs.**`. Otherwise
  `ProtocolFilter` rejects it.

## 2. Register it: one line in `modules.properties`

`src/au/edu/cqu/drs/server/modules/modules.properties` already lists both
modules:

```
module.1.warning=au.edu.cqu.drs.server.modules.warning.WarningModule
module.2.import=au.edu.cqu.drs.server.modules.importer.ImportModule
```

**Note:** `import` is a Java keyword, so `server/modules/import/` can't be a
package. The line uses `importer`; change it if you choose another name.
Until a class exists, the server logs "not installed; skipped" and carries
on.

## 3. Client: two FXML files

| File (in `src/au/edu/cqu/drs/view/`) | Opened from |
|---|---|
| `warning-main.fxml` | "6. Public warnings" in the staff navigation |
| `warning-board.fxml` | "Public warnings" button on the sign-in screen (no login) |

Unlike the core views, these name their controller in the FXML:

```xml
<VBox xmlns:fx="http://javafx.com/fxml/1"
      fx:controller="au.edu.cqu.drs.controller.warning.WarningMainController">
```

The controller (package `au.edu.cqu.drs.controller.warning`) gets a public
constructor taking a `client.ModuleContext`:

```java
public WarningMainController(ModuleContext context) { ... }

// in a javafx.concurrent.Task, not on the JavaFX thread:
Response response = context.send(
        context.newRequest(Command.LIST_WARNINGS));
```

`ModuleContext` gives you `getCurrentUser()` (null on the public board),
`getSessionToken()`, `send(Request)` and `newRequest(Command)`. Until the
client networking task (T4) is merged, `send` answers every request with
`ADAPTER_UNAVAILABLE` "Not connected to the DRS server". After T4 it reaches
the server. Your controller code stays the same.

Until the files exist, both buttons show "Warnings module not installed".

## 4. Access matrix

The server uses `RoleBasedPolicy` (it mirrors the role checks in the
services) until `au.edu.cqu.drs.security.AccessMatrix` exists. It then
delegates to that class automatically:

```java
public boolean isAllowed(UserRole role, Command command) // static or instance
```

- An instance method needs a public no-argument constructor.
- Public commands, a missing user and an inactive user are decided before
  your matrix is asked, so `role` is never null.
- If the matrix throws, the request is refused (fails closed).
- `RoleBasedPolicy` is a good starting table to copy.

## 5. Testing your module

`ModuleRegistry.load("/your/test-modules.properties", context)` loads modules
from any list on the test classpath. See `test/au/edu/cqu/drs/server/
ModuleRegistryTest.java` and `TestModules.java`.
