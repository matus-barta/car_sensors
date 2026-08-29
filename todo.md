# TODO

Work that is understood but not scheduled yet.

## Web application

### Expand the vehicle info card, grouped into tabs

`vehicle-info-card.svelte` shows only name, id, status, last seen, coordinates
and bearing - a small slice of what is already being collected. A telemetry
row persists the full sensor suite (power and charging state, GPS speed,
altitude, accuracy and provider, plus accelerometer, gyroscope, magnetometer
and barometer readings - see `telemetry_samples` in
`20260614151604_init_telemetry.sql`), and `LiveSample` already carries speed,
altitude, accuracy, charging and power source over the live stream - none of
it reaches the card today, since `VehicleSummary`/`VehicleLivePosition` only
carry lastSeenAt, latitude, longitude and bearing.

Worth growing the card to show more of this, and grouping it into tabs rather
than one long stacked list once it does - an "Overview" tab for identity,
status and location, a "Telemetry" or "Sensors" tab for the rest. `Tabs` is
not vendored yet (`pnpm dlx shadcn-svelte@latest add tabs`), so this would be
the first shadcn-svelte component added since the ones already in
`src/lib/components/ui/`.

Two things to carry over deliberately rather than lose along the way: the card
is absolutely positioned over the map at a width capped by the viewport
(`w-[min(24rem,calc(100vw-2rem))]`), so it needs to keep behaving on narrow
screens once there is more inside it; and surfacing more live fields means
growing `VehicleLivePosition` and the merge in `vehicle-state.svelte.ts`
beyond the four fields it carries now.

### Let the map be panned away from the selected vehicle without losing it

`vehicle-map.svelte`'s `focusSelectedVehicle()` runs inside an `$effect` that
reads `selectedVehicleId` and `vehicles`, and re-centers the map with
`easeTo()` on every change to either. That means any update to the selected
vehicle's position re-centers the map, even if someone had just panned or
zoomed away to look at something else - the view snaps back underneath them.
Live tracking makes this a lot more noticeable than it used to be: the
selected vehicle's position can now update every few seconds instead of every
poll, so the map fights a manual pan far more often than before.

The fix is a "follow" state, separate from "selected": panning, zooming or
rotating the map by hand should disengage follow without deselecting the
vehicle - the info card stays, the marker stays highlighted, but position
updates stop forcing the camera back. A small button near the existing map
controls, in the spirit of the "recenter on my location" button in most map
apps, re-engages follow and jumps back to the vehicle; it could be a toggle
that also shows whether follow is currently on.

The main implementation question is telling a user gesture apart from the
component's own `easeTo()`/`jumpTo()` calls, since both fire the same camera
events. MapLibre's `movestart`/`dragstart`/`zoomstart`/`rotatestart` events
carry a real `originalEvent` (the underlying DOM event) only when a user
triggered them; a call to `easeTo()` that does not explicitly pass its own
`originalEvent` fires with `originalEvent: undefined`, which is what should
distinguish "the user moved the map" from "the map moved because a vehicle
did" without needing a manual flag around every future place code moves the
camera.

### Do not flash a skeleton for a vehicle info card that is never coming

`+page.svelte` shows `VehicleInfoCardSkeleton` while `vehicleState.loading`,
then `VehicleInfoCard` if `vehicleState.selectedVehicle`, otherwise nothing.
`loading` is only ever true once - the very first fetch of the vehicle list,
before `VehicleState` has resolved whether there is anything to select - so
whenever that first load ends with no selection (an empty fleet, today; any
other reason there is no selection, in the future), the skeleton has already
promised a card that then just disappears once loading finishes. The correct
first frame for "there is nothing to select" is no card at all, not a skeleton
that flashes and vanishes.

Since there is no way to know in advance, while that first fetch is still in
flight, whether it will end in a selection, the skeleton cannot be conditioned
on the eventual answer - it has to go. Showing nothing until
`vehicleState.selectedVehicle` actually resolves removes the flash entirely;
the map already carries its own "Loading map…" indicator, so the page is not
left silent while data loads. `vehicle-info-card-skeleton.svelte` has no other
consumer, so this would remove that component along with the branch in
`+page.svelte` that renders it.

### Validate vehicle summary rows with a zod schema

`getVehicleSummaries()` in `vehicle-service.ts` reads a raw SQL join and casts
the driver's rows to `VehicleSummaryRow` by assertion, then defends the
numeric fields with three hand-rolled `normalizeLatitude`, `normalizeLongitude`
and `normalizeBearing` functions that each repeat the same "finite and in
range, otherwise null" shape. `parseLiveSample` in `live-tracking.ts` does the
equivalent job with a zod schema instead, for data arriving from Valkey rather
than Postgres - the two would read the same way if the SQL row were validated
the same way.

This is not a correctness fix - the normalizers already reject the same bad
values a schema would - so it is worth doing for consistency, one validation
approach for data that crosses a boundary rather than two, not because
anything is broken today.

## Continuous integration

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

### Give pairing a real workflow instead of copy-by-eye

Registering a real device today means reading its UUID off the phone's screen -
`MainActivity` displays it as plain text, with no copy button, QR code or share
action - and retyping it into `www`'s "Add vehicle" dialog, whose `deviceId`
field accepts any string from 1 to 255 characters with no check that it looks
like a UUID at all. A typo registers a device that will never match what the
phone actually uploads, and nothing surfaces that mistake until telemetry
silently never arrives for it.

Came up while writing `tools/scripts/simulate-telemetry.js` to exercise live
tracking: the script deliberately does not register devices itself (that is
the web app's job), which makes the manual transcription step, and its lack of
validation, obvious. Worth deciding deliberately: validating the shape at
registration so a mistyped ID is rejected immediately rather than failing
silently later; giving the Android app a copy button or QR code for its device
ID; or reversing the flow so the phone requests to be added and an
administrator approves a pending device by name, transcribing nothing.

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
