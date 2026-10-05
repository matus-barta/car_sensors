---
title: "Update the telemetry simulator for token authentication"
status: backlog
area: tools
depends_on: []
---

`tools/scripts/simulate-telemetry.js` predates device tokens. It identifies
itself with an `x-device-id` header alone and sends no `Authorization` header,
and its opening comment still describes the device id as "both the device's
name and its credential".

`ingest` no longer works that way. A protected request needs a `Device-Id`
header naming the device and an `Authorization: Bearer` token that proves it,
and `require_known_device` answers 401 to a request missing either. The header
name differs as well as the scheme, so the simulator is refused before its
device is even looked up - every upload it makes fails.

What it needs: a `--token` option alongside `--device-id`, both headers sent on
every request, and the comment rewritten to say where the pair comes from. The
pairing dialog in `www` is where a token is minted, and it is only ever shown
once, so the instructions should say to copy it at that point.
