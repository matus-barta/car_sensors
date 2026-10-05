---
title: "Validate the event payload before storing it"
status: backlog
area: protocol
depends_on: []
---

**After the documentation work.** `payload` arrives as a string holding JSON
whose shape depends on `event`, and `ingest` stores it in a `TEXT` column
without parsing it. Whatever a device sends ends up in the database verbatim,
and that is the wrong default for data that came from outside.

The per-event payload schemas will exist by then: they are being declared in
`ingest` for the OpenAPI document, as documentation only, with the Android
tests validating what the app produces against them. This is the step that makes
`ingest` use them too.

The constraint is the one the whole upload path is under: the payload is
flexible on purpose and every field in it is optional, so validation must not
turn into rejecting a batch. A phone running an older app, holding days of
backlog, has to keep being accepted. What validation should change is what gets
stored, not whether the upload succeeds - a payload that is not valid JSON, or
does not match its event's schema, should not reach the database as it was
sent.

Worth deciding when it is written:

- What happens to an invalid payload: dropped from the row, stored with a
  marker, or kept aside somewhere. The row itself, with its timestamp and
  sensor columns, should survive either way.
- What happens to an event nobody has a schema for yet, which a newer app
  sending to an older server will produce.
- Whether a payload that validates still needs storing as text at all, or
  whether this is the point to parse it into a `JSONB` column - possibly
  dropping the `payload` field entirely once every event's content has a home.
