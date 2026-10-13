# Feature 2: live operating picture

Owner: Venkatesh Kumar Manjunath (12310516). Written for the report.

## What it does

Every signed-in staff member sees the same picture at the same time. When
anyone submits a report, creates or moves an incident, adds a field update,
assigns a department, or issues a warning, every other client's dashboard
updates within about a second, with no Refresh button. The dashboard also
says how many users are connected, and a red banner names every incident
that has gone past its target response time.

## How it works

- After a command that changes data succeeds, the server publishes an event
  (what changed, its reference, a one-line summary and the time).
- Each client opens a second connection to the server after signing in and
  receives these events on it. Normal requests still use the first
  connection, so a stream of events never delays a request.
- Once a minute the server checks every open incident's escalation state
  and publishes an event only when that state changes, for example when a
  P1 goes from At risk to Breached.

## Rules

1. Only a change that succeeded is published. Failed or refused commands,
   reads, sign-ins, a member of the public's saved drafts and data imports
   publish nothing.
2. An event names what changed and who changed it, but never carries a
   reporter's name or contact details. A client that needs the new data asks
   for it, so the normal permission checks still apply.
3. Only a signed-in user can receive events. The server refuses an
   expired or unknown session.
4. A slow or frozen client never holds up the others. Each client has its
   own queue of up to 500 events. If it fills, the oldest event is dropped
   and the drop is logged.
5. An incident's escalation change is announced once, not every minute
   (At risk at 75% of the target, Breached past 100%; P1 targets 15
   minutes).
6. If the connection drops, the client says so and reconnects every five
   seconds.

## Screens

The **Operational dashboard** (screen 1) gains:

- a red **BREACHED** banner at the top listing the incidents past their
  target. The banner text says "BREACHED", so its meaning does not depend
  on colour alone (NFR-06).
- **Live updates on** and **Connected users: n** next to the Refresh
  button.
- a **Live activity** panel: the latest 50 changes, newest first, for
  example `14:02:31  Change  Report RPT-0007 submitted by a member of the
  public`.

The incident and escalation tables reload by themselves after each event.

## Where it is in the code

`server/event/` (ServerEvent, EventBus, ChangeEvents, EventChannel,
QueuedSubscriber, EscalationMonitor), `client/event/EventListener`, and
`controller/DashboardController`. Tests are TC-EV-01 to TC-EV-13 in
`test/au/edu/cqu/drs/server/event/` and `test/au/edu/cqu/drs/client/event/`.
