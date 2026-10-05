---
title: "Drop the phone's upload bookkeeping from the server table"
status: backlog
area: database
depends_on: []
---

`telemetry_samples` on the server has `uploaded`, `uploaded_at` and
`upload_attempt_count`, plus two indexes on them, `idx_telemetry_uploaded` and
`idx_telemetry_uploaded_timestamp`. They arrived in the first migration under
the comments "upload tracking (future use)" and "upload worker queries", and they
mirror the phone's Room entity, where they are real: `TelemetryUploader` marks
rows uploaded and counts failed attempts on the device.

On the server nothing touches them. `ingest` does not bind them in its insert,
so every row gets the defaults, and nothing in `www` reads them. There is no
upload worker on the server for the indexes to serve, and a sample that has
reached the server has by definition been uploaded. What they cost is two
indexes maintained on every insert into the largest table, and a schema that
suggests a mechanism which does not exist.

Removing them is a migration, a regenerated Drizzle schema and regenerated
schema docs. Worth checking first that nothing outside the repository - a
pgAdmin query, an export - has started relying on them.

**Related.** Worth doing before [Consider moving the existing tables into
schemas of their
own](consider-moving-the-existing-tables-into-schemas-of-their-own.md), so the
move carries less.
