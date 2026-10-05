---
title: "Declare a schema for every event payload"
status: next
area: protocol
depends_on: []
---

`docs/api/openapi.json` describes the upload envelope exactly - it is generated
from the type `ingest` deserialises into - but `payload` only as a string
holding JSON. The app writes 17 events besides `telemetry_sample`, each with a
payload of its own shape, and their names are string literals at the call sites
in `TelemetryForegroundService.kt`, so not even the list of events is written
down anywhere.

What to add:

- **One struct per event payload in `ingest`, deriving `ToSchema`**, registered
  in `ApiDoc`'s components and named after its event. They exist for the
  document only: `ingest` goes on accepting any payload, so nothing about the
  upload path changes. A small `x-event-payloads` extension, added through a
  `Modify`, maps each event name to its schema so a reader does not have to
  rely on a naming convention.
- **One list of events on the phone**: a sealed class or enum replacing the
  literals, so the set of events is something the code can be asked for.
- **An Android unit test validating against the committed `openapi.json`.** It
  builds each event's payload the way the service does and validates it against
  that event's schema, and it validates a whole upload body from
  `TelemetryUploader.buildJsonPayload` (private today, so it needs opening to
  the test) against `TelemetrySample` - the envelope is assembled by hand with
  `JSONObject` too, so it can drift just as easily.
- **A coverage check both ways**: every event in the Kotlin list has a schema,
  and every schema has an event.
- **An example per payload schema**, which the existing `ingest` test already
  validates against its own schema.

The structs are documentation that nothing at runtime ties to what the phone
sends, which is why the Android test is the part that matters: without it they
are exactly as trustworthy as prose.

The rules for changing a payload belong in the protocol documentation at the
same time: fields are only ever added, every new field is optional, a field is
never reused for a different meaning, and a real change of shape gets a new
event name or a version field. `ingest` has to keep accepting what older app
versions send, with days of backlog behind them.
