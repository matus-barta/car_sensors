# TODO

Work that is understood but not scheduled yet.

## Continuous integration

### Run clippy and the test suite in CI

`.github/workflows/ingest-build.yml` builds and pushes the Docker image but never
runs `cargo clippy --all-targets --all-features -- -D warnings` or `cargo test`,
which is why the workspace accumulated four clippy errors without anyone
noticing. `www` has its own validation workflow; the Rust side has no equivalent.

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
