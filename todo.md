# TODO

Work that is understood but not scheduled yet.

## Web application

### Live-track the selected vehicle

`ingest` already publishes the newest position of a device to Valkey: the JSON
snapshot is stored at `device:live:{device_id}` with a fifteen minute expiry and
announced on the `telemetry:live` channel, carrying `deviceId`, `timestamp`,
`latitude`, `longitude`, `altitude`, `speedKmh`, `bearing`, `accuracyM`,
`charging` and `powerSource`. Nothing consumes it yet.

The transport on the `www` side is `query.live`, which the installed SvelteKit
ships as part of the remote functions this project already enables. It streams
values from an async generator to the browser over server-sent events, with
reconnection and backoff built in, so it needs no WebSocket, no custom Node
server and no separate endpoint to authenticate. A live query is the natural
shape here because the browser never sends anything back - writes already go
through commands.

What has to be built:

- One Valkey subscriber per `www` process. A subscribed connection cannot issue
  other commands, so it is a dedicated one, and it must fan out in memory to the
  live queries rather than opening a connection per browser tab.
- A `watchVehicle` live query beside `getVehicles`, guarded by the same
  authentication check, yielding the stored snapshot on connect and then each
  announcement for that device.
- Merging the live sample over that vehicle's summary, so the info card and the
  map marker follow it. Status needs no special handling: it is already derived
  from `lastSeenAt` against the shared clock.
- Keeping the existing thirty second poll for the rest of the fleet - only the
  selected vehicle is streamed.

Decisions worth making deliberately:

- `REDIS_URL` should be optional in `www`'s validated environment, with live
  tracking degrading to the existing poll when it is absent. Making it required
  would give development, CI and the whole end-to-end suite a hard dependency on
  Valkey, which today they do not have.
- The session is checked when the stream opens and not again, so a long-lived
  stream outlives a sign-out on the server side. The app shell already tears the
  client down; a lifetime cap or a periodic re-check would close the rest.
- SvelteKit's documentation warns that a live query response must never be
  cached by a service worker, since the cloned response keeps streaming after
  the page closes. This application has no service worker today.
- A reverse proxy must not buffer the response. Traefik does not by default and
  ignores its flush interval for streaming responses, so this only matters if
  someone puts a buffering middleware in front of the application.

Note that the Android app uploads only over unmetered networks and in batches,
so live updates will arrive in bursts until that changes. The plumbing is
correct either way, but it will not look live on the road until the device
pushes over mobile data.

## Continuous integration

### Stop the CI test credentials triggering GitGuardian

GitGuardian raised an incident for the `POSTGRES_PASSWORD: postgres` literal in
the workflow service containers. The value is a throwaway for an ephemeral
container that never leaves the runner, so this is noise rather than a finding -
but it is noise that recurs on every change to those files.

The alerts come from the GitHub App, and that decides which remedy works.
GitGuardian's own documentation is explicit that ggshield "does not share its
ignored secrets with the dashboard", while dashboard ignores *are* honoured by
ggshield. Committing a `.gitguardian.yaml` would therefore silence a local or CI
run of ggshield and change nothing about the incidents being raised today. That
file is worth adding only if ggshield is ever wired into a hook or a workflow;
its keys are `ignored_paths`, `ignored_matches` and `ignored_detectors`, and
`ggshield secret ignore --last-found` writes it in the right shape.

Two things that do work, and they compose:

- In the dashboard, ignore the incident with the reason "test credential", which
  also stops it reopening when the same string reappears, and add a filepath
  exclusion for `www/.env.test`. That file holds an end-to-end auth secret and is
  committed on purpose, because `vite preview` runs in production mode and would
  not read it otherwise. Exclusions are glob-style, can be scoped to a single
  repository, and apply retroactively to existing incidents.
- Remove the literal instead of excusing it. Setting
  `POSTGRES_HOST_AUTH_METHOD: trust` on the service container in place of
  `POSTGRES_PASSWORD` lets the job connect with no password in the URL at all -
  verified against `postgres:18` from an external client, which is how a job
  reaches a service container, with a password-protected container refusing the
  same connection. Trusting every connection is acceptable for a container that
  exists for the length of one job and is not reachable outside it. The
  `BETTER_AUTH_SECRET` values in the web workflow can go the same way, generated
  per run with `openssl rand -hex 32` into `GITHUB_ENV` rather than committed.

The compose files keep their `postgres` and `pgadmin` passwords either way:
those are for a local stack an operator is expected to change, and documenting
them is the point.

### Extract a setup-rust action once a second Rust job exists

`ingest-validation.yml` installs the toolchain with clippy and rustfmt and warms
`Swatinem/rust-cache` inline. That is one job, so there is nothing to share and
a composite action would be indirection for its own sake.

The moment a second Rust job appears - splitting formatting, clippy and tests to
run in parallel, adding `cargo audit`, or a coverage job - those two steps become
worth extracting into `.github/actions/setup-rust`, the same trade
`.github/actions/setup-www` already makes for four job references.

Two constraints to remember when that day comes. Service containers cannot live
in a composite action, because `services` is a job-level key; sharing the
Postgres and Valkey setup is what the reusable workflow is for. And the Rust
setup inside `setup-www` should stay where it is: it exists to install the SQLx
CLI and wants neither the components nor the workspace cache, so merging the two
would produce one action with flags selecting between unrelated behaviours.

## Telemetry upload protocol

### Stream uploads as NDJSON instead of one JSON array

An upload is a single JSON array, and the `Json` extractor buffers the whole
body before parsing it, so the memory a request costs is bounded only by the
body limits in `ingest/src/lib.rs` - currently 4 MiB compressed and 32 MiB once
expanded. That is the reason a limit has to exist at all, and it caps how much
backlog a device can hand over in one request.

Sending one sample per line instead would let the service parse and insert as
the body arrives, making the cost of a request proportional to a single sample
rather than to the whole backlog, and removing the need to guess a limit.

This is a protocol change on both sides: the Android uploader would have to emit
NDJSON as well, so it belongs with the Android work rather than as a
server-only change.

## Device authentication

### Decide whether the device id stays the credential

`X-Device-ID` both names a device and authorises it: the middleware checks that
the value matches an active row in `known_devices`, and nothing else is proven.
The id is a random UUIDv4 generated per installation, so it cannot be guessed,
and TLS is terminated by the reverse proxy, so it is not exposed in transit on
the public side.

What remains is that the identifier is the secret. It is visible to the phone's
user and to anyone signed in to the web application, it appears in the database
and in service logs, and if it leaks, whoever holds it can post telemetry
indistinguishable from the real device - fake positions on the live map, junk in
the history. Revoking it means deactivating the device, which locks out the
genuine phone too.

This was not worth deciding while the registration endpoint was open to anyone;
that endpoint is gone, so it is now the weakest link. Accepting it for a private
deployment is a reasonable answer, as long as it is a decision rather than an
oversight. If it should change, the options in increasing order of effort are a
per-device bearer token stored as a hash and issued at registration, HMAC-signed
uploads carrying a timestamp and nonce, which also stops replay, and mutual TLS.

## Android app

### Timestamp a sample from GPS when a fix is available

`TelemetryForegroundService.writeMergedSample()` stamps every sample with
`System.currentTimeMillis()`. That clock comes from the cellular network (NITZ)
or NTP — Android never sets it from GPS — so a device that has been offline for
a while can be wrong by minutes.

When a location fix is available, take the time from it (`Location.getTime()` is
GPS-derived for the GPS provider) and fall back to `System.currentTimeMillis()`
only when there is no fix.

This matters beyond tidiness: `ingest` picks the live position by timestamp and
keeps a monotonic guard, so a device whose clock runs ahead publishes a position
that suppresses every genuine one after it until the key expires. There is a
five-minute skew guard in `ingest/src/live/telemetry_sample.rs` that catches
gross errors, but the real fix is a trustworthy clock on the device.

### Stamp a sample when it is captured, not when it is written

The timestamp is currently taken inside `writeMergedSample()`, which runs on the
flush interval, so it records when the row was persisted rather than when the
reading was taken.

The gap shows up when GPS drops out: `latestLocation` keeps returning the last
known fix while each new row gets a fresh timestamp, so a phone parked in a
tunnel looks like a vehicle reporting live from a position it left long ago.
Capture the timestamp with the reading, and consider carrying the fix's own age
(`Location.getElapsedRealtimeNanos()`) so a stale position can be recognised as
stale.
