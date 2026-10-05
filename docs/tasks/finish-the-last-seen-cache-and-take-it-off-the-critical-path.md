---
title: "Finish the last_seen cache and take it off the critical path"
status: next
area: protocol
depends_on: []
---

**Next up.** Two halves of the same thing: a cache that was half built, and the
bookkeeping around it that runs where it should not.

`ingest` used to write `device:last_seen:{device_id}` to Valkey on every
authenticated request. Nothing ever read it - not `ingest`, not `www` - so it
was removed rather than left as a write nobody could explain. The intent behind
it was real though, and worth finishing rather than losing: `www` was meant to
read it.

**What it was for.** `www` decides whether a vehicle is online from
`known_devices.last_seen_at`, and that column is deliberately written at most
once every `LAST_SEEN_WRITE_INTERVAL_SECS`. Add the browser's own 30 second poll
and the freshness a vehicle is judged on can be a minute behind what the server
actually knows, against an online threshold of two minutes. A key written on
every request has no such lag, which is the ordinary division here: Postgres
holds the durable record, Valkey carries what is live.

The live channel is not a substitute for it. `device:live:{device_id}` is only
published when a sample carries a position, so a device uploading without a fix
touches neither it nor anything else, and `watchVehicle` only streams the one
vehicle that is selected. The rest of the list still needs a cheap way to say
how recently each device was heard from.

**How it should be written this time.**

- `ingest` sets the key on every authenticated request, with the value as epoch
  milliseconds to match the live sample's `timestamp`. Like every other key
  crossing this boundary it is a contract with a service in another language,
  so it wants naming in both places.
- The TTL has to outlive the point at which a vehicle reads as offline, which
  is fifteen minutes in `calculateVehicleStatus`. The old value was ten, so the
  key expired while a vehicle should still have read as stale. Anything past
  the stale threshold works, because expiry then means only "no recent contact
  at all" and the Postgres column answers.
- `www` reads the keys for the listed devices in one `MGET` and takes whichever
  of that and `last_seen_at` is newer. It must stay a fallback in both
  directions: no Valkey, or no key, leaves the database answer standing, the
  same way live tracking is simply absent when `REDIS_URL` is not configured.

**And move the bookkeeping off the upload path while doing it.**
`require_known_device` awaits `record_device_seen` before the handler runs, so
every upload waits for it - normally one cache read, and once per interval a row
update as well. Sub-millisecond on a local network, but it is bookkeeping whose
failures are already only logged being paid for synchronously by the one path
that must never be slow, and the live push travels it every couple of seconds
while a car is moving. Spawning it would remove that wait entirely: it needs
nothing from the response, and the cost is a task per request with no ordering
between two of them, which the update tolerates because it only ever writes
`NOW()`.

The interval check can also be one round trip instead of two.
`SET key value NX EX 30` claims the marker atomically and says whether it did,
where the current code reads the key and then writes it. That also closes a
small race in which two concurrent uploads both find the marker absent and both
update the row - harmless today, since the second write is identical to the
first. The reason it was not written that way is an ordering the current code is
careful about: the marker must not be claimed before the update succeeds, or a
failed write would suppress the next attempt for the whole interval. Claiming
first and deleting the key if the update fails preserves that.

Note on the naming, since it caused real confusion once. This was called
`update_last_seen_throttled`, gated by `LAST_SEEN_DB_THROTTLE_SECS` and marked
in Valkey under `device:last_seen_db_throttle:{device_id}`, and "throttle" reads
as rate limiting - which it never was. It has been renamed to
`record_device_seen`, `LAST_SEEN_WRITE_INTERVAL_SECS` and
`device:last_seen_written:{device_id}`, and the word is worth keeping out of
this area entirely so that
[Rate limit the upload endpoint](rate-limit-the-upload-endpoint.md) is the only
thing it ever refers to.
