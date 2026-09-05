# How the pieces fit together

Four independent pieces share one PostgreSQL database.

```text
Android app  ──uploads──▶  ingest  ──writes──▶  PostgreSQL  ◀──reads──  www
                              │                                          ▲
                              └──────publishes latest position──▶ Valkey ┘
```

## One database, not one per service

The services are small, and the coupling a shared database buys is cheaper than the operational cost of running five of them. This is a deliberate departure from the usual microservice advice, which exists to decouple independent teams on independent release cycles - a problem this project does not have.

The cost it does pay is schema coupling: `db/migrations` is a single global set, so a change to a table has to suit every reader and be deployed in the right order. The discipline that keeps that manageable is **one writer per table**, with as many readers as need it. `known_devices` is the exception that shows the rule: `www` owns the row and `ingest` touches only `last_seen_at`, which works precisely because the two never write the same column.

## Data moves through the database; calls are for answers that do not exist yet

A producer writes what it computes and a consumer reads it. `ingest` writes `telemetry_samples` and touches `known_devices.last_seen_at`; `www` reads both. Neither calls the other, and neither can take the other down.

A service call is the exception, for work that cannot be precomputed into a table - an answer that does not exist until something asks for it, or logic that cannot be shared because the pieces are written in different languages. The Rust and TypeScript split makes that a real constraint rather than a stylistic one: there is no shared module to reach for.

The test for whether such a call is acceptable is what happens when it fails. **Enrichment may depend on a service; the ingest path may not.** A geocoder that is down means a position shown as coordinates instead of a street name. An authentication service that is down would mean telemetry refused and data lost, which is why device authentication is a database lookup rather than a call.

## What each piece is responsible for

**The Android app** collects location and sensor data, stores it locally in Room, and uploads it in batches. It is store-and-forward by design: it records whether or not a server is reachable, and a backlog can be days deep. When live upload is enabled it also pushes each new position as it changes.

**`ingest`** accepts those uploads, authenticates the device, and writes the batch to PostgreSQL. It also publishes the newest position of each device to Valkey, which is the only thing it does that is not durable.

**PostgreSQL** is the record. Everything that matters ends up here, and `db/migrations` is the only thing permitted to change its shape.

**Valkey** carries what is current rather than what is kept: the live position of each device, a cache of which devices are known, and the throttle that stops `last_seen_at` being written on every upload. Losing all of it costs a little latency and nothing else.

**`www`** reads the database for history and subscribes to Valkey for live positions, merging the two so a map updates between polls.

## Why live data goes through Valkey rather than the database

A live position is worth having for minutes and worthless afterwards, and it arrives as often as a device can send it. Writing that to PostgreSQL would mean a table whose rows are almost all obsolete, and a `www` that polls it. Valkey holds one key per device with a fifteen-minute expiry and publishes each change, so `www` learns about a new position rather than asking for one, and nothing has to be cleaned up.

The durable copy is written first, and the announcement is best effort: if Valkey cannot be reached, the upload still succeeded and the history is still complete.
