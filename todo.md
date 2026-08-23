# TODO

Work that is understood but not scheduled yet.

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
