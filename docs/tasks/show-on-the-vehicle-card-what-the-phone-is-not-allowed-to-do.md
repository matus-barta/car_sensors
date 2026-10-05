---
title: "Show on the vehicle card what the phone is not allowed to do"
status: backlog
area: www
depends_on: []
---

The phone reports the settings that decide whether it can work unattended, and
nothing reads them yet. Without background location ("Allow all the time"), a
logger restarted after a reboot records rows with no position in them; without
notifications, the warning that uploads have stopped is never seen; with its
battery use restricted, it may not run at all. The app's own screen says all of
it, but a phone in a car is the one whose screen nobody opens, and the web
application is where somebody actually looks.

They arrive as event rows in `telemetry_samples`, under the same keys in the
JSON held in `payload` (a TEXT column):

- `service_started`, written at every start of the logger;
- `access_changed`, written when any of them changes while the logger runs.

```json
{
  "locationAccess": "WHILE_IN_USE",
  "preciseLocation": true,
  "notificationsEnabled": false,
  "batteryUnrestricted": false,
  "backgroundRestricted": false,
  "dataSaverRestricted": false
}
```

`service_started` also carries `previousExitReason`, `previousExitDescription`
and `previousExitAt` from Android 11: how the process before it ended.
`USER_REQUESTED` there means somebody stopped the app from Active apps or with
Force stop, which nothing restarts - worth saying on the card in its own right,
since it is the one reason a logger stops for good without anything being
broken. Of the others, `backgroundRestricted` is the most serious: from Android
13 a restricted app may not run the logger at all.

`locationAccess` is `NONE`, `WHILE_IN_USE` or `ALWAYS`. Android 9 has no
separate background permission and reports `ALWAYS` whenever location is
allowed at all, so only newer phones ever show the middle value.

The newest of those two events for a device is its current state. That is one
more lateral join beside the two `getVehicleSummaries()` already makes, ordered
by `timestamp` and limited to one, with the payload cast to `jsonb` to pick the
fields out. Rows from phones older than this reporting carry none of the keys,
which should read as unknown rather than as fine.

On the card it wants to be a remedy rather than a status, worded the way the
app's own warnings are (`ui/SetupWarnings.kt` has them): `WHILE_IN_USE` as
"Location is allowed only while the app is open - after a reboot it records
without GPS", notifications off as "the phone cannot warn about failed
uploads", and so on. Silent when all is well.

What it cannot show: a phone whose uploads have stopped never sends the row
that would say why. That case is already visible as the last-seen time going
stale.

**Related.** Better after [Declare a schema for every event
payload](declare-a-schema-for-every-event-payload.md), so the fields read here
come from the documented contract rather than being restated. [Expand the
vehicle info card, grouped into
tabs](expand-the-vehicle-info-card-grouped-into-tabs.md) decides where on the
card this lives, and the extra lateral join lands in `getVehicleSummaries()`,
which [Validate vehicle summary rows with a zod
schema](validate-vehicle-summary-rows-with-a-zod-schema.md) would make safer to
change.
