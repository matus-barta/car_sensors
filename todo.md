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

### Issue a per-device token instead of using the id as the credential

`X-Device-ID` both names a device and authorises it: the middleware checks the
value against an active row in `known_devices`, and nothing else is proven. The
id is a random UUIDv4, so it cannot be guessed, but it is the secret - visible on
the phone's screen, to anyone signed in to `www`, in the database and in service
logs. If it leaks, whoever holds it can post telemetry indistinguishable from the
real device, and revoking it means deactivating the device, which locks out the
genuine phone too.

Decided: the device should carry a real token, separate from the identity that
names it. Pairing is being rebuilt anyway, and the code that hands out an
identity is the code that would hand out a token, so doing this later means
writing that flow twice.

The scheme should be dull on purpose. `www` generates 32 random bytes, shows
them once and stores only their SHA-256 hash on the `known_devices` row; the
device sends the token and `ingest` hashes what it receives and compares in
constant time, inside the lookup the middleware already performs. That adds no
round trip and no measurable time to the upload path.

A password hash - Argon2, bcrypt - would be the wrong tool despite being the
usual advice. Those are deliberately slow because passwords have little entropy;
a 256-bit random token has plenty, and the slowness would land on every upload.
SHA-256 is also in the standard library of both languages involved, which
matters because `www` is TypeScript and `ingest` is Rust and anything more
exotic gets implemented twice.

The alternatives, for the record. A signed token - JWT or PASETO, via `jose` and
`josekit` - would let `ingest` verify without touching the database, but
revocation then needs a denylist that gives the statelessness back, expiry is
awkward for a device that may be offline for weeks, and `ingest` reads
`known_devices` regardless. Better Auth's API key plugin is already in the stack
for user authentication, but `ingest` would be depending on another framework's
storage format from another language, which is a poor trade for a hash
comparison. Mutual TLS remains the strongest answer and the most operational
work.

Worth being explicit that this stays shared state rather than becoming a
service. What multiple pieces need is the stored hash, not centrally executed
behaviour, and `db/migrations` already owns that contract - whereas an
authentication service would put a network dependency in the one path that must
never lose data.

### Let the server issue the identity and the phone scan it

The phone invents its own UUID on first run, and registering it means reading it
off the screen and retyping it into `www`'s "Add vehicle" dialog, whose
`deviceId` field takes any string from 1 to 255 characters without checking that
it even looks like a UUID. A typo registers a device that will never match what
the phone uploads, and nothing surfaces that until telemetry silently never
arrives.

The authority moves to the server. `www` generates the identity and the token
from the entry above, stores them in `known_devices` and shows both as a QR code
and as copyable text. The phone starts with nothing and offers to scan; typing
or pasting stays the fallback for when the camera will not cooperate.
`DeviceIdProvider` stops generating anything, which is a real behavioural change
- an unpaired app has no identity rather than an unregistered one, and every
screen that assumes one exists has to cope with its absence.

ZXing (`zxing-android-embedded`) reads the code without dragging in Play
Services, which is the better trade on the old handset this runs on.

Undecided, and worth settling before the web side is written: whether `www`
stops accepting a typed device id altogether once it generates them, or keeps
the field as an escape hatch. Removing it makes the server unambiguously the
authority; keeping it leaves a way to adopt a device whose identity came from
somewhere else. It changes how much of the existing dialog survives.

Pairing has to be repeatable, not a one-off. A phone moves between cars, a token
is rotated, an app is reinstalled - the flow that assigns an identity is the
flow that reassigns one, and it should be reachable from the screen at any time
rather than only when there is no identity.

Until an identity is present the app is not set up and should say so, at the top
of the screen alongside the logging state. It should still record - someone who
drives before pairing should not lose that - but it must not upload, because an
upload with no identity is a guaranteed rejection that would only burn attempts
against rows which have done nothing wrong.

An App Link that opens the app straight from `www` would be a nicer path than
scanning when the browser is on the phone itself, but it is worth less than it
looks: App Links need `assetlinks.json` served over HTTPS from the domain, which
a LAN address over plain HTTP cannot do, and a custom scheme any application can
claim would put a token into browser history. A convenience to add after the QR
flow works, not an alternative to it.

### Cover the four ways a phone and a car come together

Pairing is not a one-off, and the flow has to answer all four of these rather
than only the first.

**A new phone for a new car.** `www` creates the vehicle, mints its identity and
token, shows the QR once. The phone scans it and starts recording. Nothing
special.

**A new phone for a car that already exists.** Replacing, wiping or reinstalling
a handset is ordinary, and the vehicle should survive it with its identity and
its whole history. This collides with showing a token once and storing only its
hash, which is otherwise the right way to keep one: nobody can read it again, so
a wiped phone could never rejoin its car. Rotation resolves it. `www` gains a
"pair a new phone" action on the vehicle that mints a fresh token against the
same `device_id`, shows the new QR and invalidates the old hash. The car keeps
everything; only the credential changes. Locking the previous handset out is the
point, not a side effect.

**An existing phone moving to a different car.** The phone is holding rows that
belong to the car it is leaving, and they must not arrive under the name of the
car it is joining. This is the case the entry below exists for, and it needs
nothing from the server: `ingest` authenticates each request on its own, so
draining the old car's backlog with the old car's token while recording for the
new one is simply two uploads with two credentials.

**Banning a compromised identity.** Worth splitting, because the two halves want
different things. A phone that was lost while its car stays in service needs the
*credential* revoked, which is the rotation above - and that is the answer to the
long-standing complaint that revoking a device locks out the genuine phone too,
because with a token there is now something to revoke that is not the identity.
Only a vehicle genuinely being retired needs the `known_devices` row deactivated,
and `ingest` already answers 403 for that today.

What the phone does about that 403 is unfinished. It classifies the response as
`REFUSED` and deliberately does not count it against the rows, which is right
for a mistyped address and wrong for a device that has been banned for good: the
backlog then grows until the storage ceiling with no prospect of ever being
accepted. The state deserves saying plainly on screen, and the user deserves the
choice between discarding the rows and keeping them for export.

### Keep every sample with the identity it was recorded under

`TelemetrySampleEntity` has no device column. The identity is only ever an
`X-Device-ID` header applied at the moment of upload, so the rows waiting in the
database belong to nobody in particular - they belong to whoever the phone is
paired with when they finally go up. Move the phone to a second car and the
first car's unsent journey silently arrives as the second car's.

Stamping the row when it is written is what makes the third case above
answerable. A local `pairings` table - identity, token, when it was paired, a
label - and a nullable `pairing_id` on `telemetry_samples` pointing at it. An
integer rather than the UUID itself, because at two rows a second a
thirty-six-character string would cost several megabytes a day to repeat the
same fact.

Keeping the old *token*, and not merely the old identity, is what stops "do not
lose the data" and "do not misattribute the data" being a choice between two
losses. A phone that has moved can still prove it is the car it came from, so
that backlog goes where it belongs while the phone records for its new one. Once
a retired pairing has nothing left pending it has no further use, and offering
to forget it keeps the phone from hoarding credentials for cars it left long
ago.

What follows:

- Upload sends only rows whose pairing the phone still holds, so misattribution
  stops being possible rather than being avoided by care.
- Rotating a token leaves `device_id` alone, so existing rows still match their
  pairing and a replaced handset needs no questions asked.
- Rows recorded before any pairing carry none, and the moment worth asking about
  them is when an identity is finally assigned, since the user knows which car
  the phone was sitting in and nothing else does.

This is a Room migration on a table that already holds the only copy of
unuploaded telemetry, so it wants the same care as the last one: rows written
before it lands have no pairing and are indistinguishable from rows recorded
unpaired.

## Database

### Consider moving the existing tables into schemas of their own

Everything except the geocoder's cache lives in `public`. Nothing is wrong with
that today, and this is worth recording as an option rather than a fault to fix:
the value is in what it prevents later, not in anything it repairs now.

Postgres schemas are namespaces rather than walls, which is exactly why they
suit an arrangement of small services over one database. Joins, foreign keys and
transactions all work across them unchanged - it is still one database and the
planner does not care - so none of what makes a shared database pleasant is
given up. What is gained is a name for each boundary and, if it is wanted,
enforcement: privileges are granted per schema, so with each service connecting
as its own role, who may read what stops being a convention that has to be
remembered.

Naming them after domains rather than services would age better, since services
get renamed and split while domains do not:

| Schema      | Holds                     | Written by    |
| ----------- | ------------------------- | ------------- |
| `auth`      | the Better Auth tables    | `www`         |
| `fleet`     | devices, ownership        | `www`         |
| `telemetry` | samples                   | `ingest`      |
| `trips`     | trips                     | trip service  |
| `geocoder`  | the place cache           | geocoder      |

That also puts the one-writer rule into the structure instead of leaving it in
prose.

If users are ever associated with vehicles rather than only with sessions, the
relationship belongs on the domain side - `fleet.devices.owner_id` referencing
`auth."user"`, or a join table in `fleet` if it becomes many to many - so that
the dependency runs one way. The domain may reference `auth`; `auth` should know
nothing about vehicles, which keeps it a leaf that could be replaced without
disturbing anything pointing out of it. The friction to expect is that a foreign
key into Better Auth's `user` table makes a later upgrade that alters or
recreates it a coordinated change rather than a local one. Those migrations are
already written by hand here rather than by Better Auth's own migrator, so the
timing is under control, but it stops being free.

The move itself is cheap - `ALTER TABLE ... SET SCHEMA` is metadata only, with
no rewrite - but it is not free downstream. The generated Drizzle schema has to
be regenerated through `./tools/scripts/sync-www-db-schema.sh`, `pnpm db:check`
fails on drift, and any raw SQL in `www` needs its names qualified, so the
migration and the generated output have to land together. Drizzle itself copes
perfectly well by way of `pgSchema`.

One honest trade. Schemas make a future split into separate databases easier,
because moving `geocoder.*` elsewhere is far simpler than extracting it from a
shared `public`. A foreign key across schemas is precisely what would have to be
broken to do that. Referential integrity and splittability pull against each
other here, and at this size integrity is the better buy - but it is a choice
rather than a free lunch.

## Trips

### Derive trips on the server from the uploaded track

`www` sees an undifferentiated stream of samples and cannot answer "show me
yesterday's drive". The device knows more than it says - the armed and recording
states bracket a journey almost exactly - but deriving this on the server
instead means it also works for data already collected, and for a device whose
motion detection misfired.

A separate service reading `telemetry_samples` and writing a `trips` table fits
how the pieces here already talk to each other: through Postgres, with
`db/migrations` owning the schema.

"Trip" over "drive" or "journey" - it is the ordinary term in vehicle telematics,
and `trips` and `trip_id` read naturally as columns.

The shape of the algorithm is a departure, an arrival, and a test of whether
what lies between them was worth calling a trip. Departure is movement past some
distance from where the vehicle had been resting; arrival is having stayed still
for long enough; and a candidate qualifies on a minimum duration and distance,
which is what stops a shuffle across a car park becoming a trip. Every one of
those four numbers wants choosing deliberately and writing down.

Three things the data will do that a first attempt usually does not expect. A
parked car's position drifts, so `accuracy_m` and the speed have to be
consulted or the drift invents trips that never happened. A stale fix now
produces a row with no position at all, so gaps are explicit and must not read
as arrivals. And uploads are store and forward, so samples arrive late and out
of order and a trip may only become computable days after it happened - which
means recomputing over a window rather than streaming forward, with upserts
keyed so that reprocessing the same span twice changes nothing.

### Name a trip after where it started and ended

A trip named by its endpoints is far easier to find than one named by a
timestamp. Within a town that means the street it started on and the street it
ended on; between towns, their names; between countries, the countries as well.

The lookups themselves belong to the geocoding service below rather than here.
What stays with trips is the rule: which component to use at which scale, and
what to fall back on. Store the structured pieces the service returns - road,
town, country for each end - rather than only the rendered name, because the
rule is presentation and will be adjusted, and keeping the components means
adjusting it without asking anybody to resolve the same place twice.

Worth deciding what a trip is called when the rule cannot be applied: a motorway
slip road with no street name, a geocoder that is down, coordinates in the
middle of nowhere. A trip with no name is worse than a trip named after its
coordinates.

## Geocoding

### Put reverse geocoding behind a service of its own

Trips need to know what to call their endpoints, and that will not be the only
thing that does. Showing a vehicle's current position as a street rather than a
pair of coordinates is an obvious second consumer, and it sits in `www` - which
is TypeScript, where the trip service is Rust.

That language boundary is what settles the shape. Ordinarily this would be a
module inside the trip service, extracted if a second caller ever appeared; here
"extracted later" is not available, because the second caller cannot import
Rust. Its choices would be to call a service or to write the client, the cache,
the rate limiting and the User-Agent a second time in another language. Sharing
the cache table instead does not rescue it either: a second consumer needs to
resolve points nobody has looked up yet, and shared state only works when the
state is already complete.

Worth naming what this changes. It would be the first service here whose
contract is an API rather than a table - `ingest` and `www` deliberately never
call each other - and it introduces a runtime dependency where there was none.
That is acceptable precisely because geocoding is enrichment rather than
gating: if it is down, a trip is named later and the web application shows
coordinates, where an outage in the upload path would lose data instead. The
same argument would not justify, say, an authentication service.

Scope it tightly or it will grow into a general "location service". It owns the
cache, the rate limiting, the User-Agent, retry and backoff, and which provider
is in use. It does not own how a trip is named, which is presentation belonging
to trips. Its contract is the HTTP interface and not its tables: callers reading
the cache directly would couple to the schema and still be unable to resolve
anything new. And it should answer with structured components shaped closely on
what the provider returns, so that changing provider does not change the
contract.

Its tables belong in a Postgres schema of their own - `geocoder` - from the
first migration rather than in `public` alongside everything else. That is
cheap when there is nothing to move and awkward afterwards, and it is what makes
"the contract is the API, not the tables" something more than an intention: with
each service connecting as its own role, a `GRANT` on that schema decides who
may read the cache rather than a note asking politely that nobody does. It also
means this piece could be lifted into a database of its own later without first
being disentangled from the others.

Note that a bare `CREATE TABLE` in a migration lands wherever `search_path`
points, which is normally `public`. Qualify the name, or set the search path at
the top of the migration, or the tables quietly appear in the wrong place.

### Meet the Nominatim usage policy in one place

Concentrating this in the geocoding service is most of the reason to have one -
every obligation lands once instead of in every consumer.

The public Nominatim instance is very likely the right provider rather than the
one to avoid. Its policy allows one request a second in general, and four a
minute for a script that runs repeatedly or longer than a day, which is the
bucket this falls into. Two lookups per trip and perhaps ten trips a day is
twenty requests against a budget of 5,760 - room for a few hundred vehicles
before the limit comes into view, before any cache hits at all. Nor is this what
the policy objects to: it forbids auto-complete, systematic queries such as
grids and complete listings, scraping and reselling. Asking where the two ends
of a journey happen to be is the sparse, occasional lookup the service exists to
answer.

The obligations, which are obligations and not suggestions. Caching, which the
policy requires outright, since repeating a query is grounds for being blocked.
Attribution wherever results are shown, under ODbL. A provider that can be
changed on request *without a software update*, so it belongs in configuration
rather than in the source. And a User-Agent identifying the application, because
an HTTP library's default explicitly will not do.

That last one wants care, because this is software other people can run.
Nominatim can block on address or on User-Agent; an address is one operator's
problem, but a User-Agent shared by every deployment is everybody's - one
careless instance would take the rest down and nobody could tell themselves
apart. So the header should name the software and its version, and carry a
contact belonging to whoever runs that copy:

    car-sensors-geocoder/0.1.0 (+https://example.org/contact)

The mechanism matters more than the format: the contact should be required
configuration that the service refuses to start without, rather than a default
that quietly works, because a default that works is a default nobody replaces.
The convention for redistributable software talking to Nominatim is exactly this
- force the operator to set their own, and point them at the policy - and the
reward is that a contactable operator receives an email where an anonymous one
receives a block. It only helps if the address is theirs. The policy is explicit
that a User-Agent is required and silent on whether it should distinguish
deployments; that reasoning follows from how blocking works rather than from a
written rule.

Self-hosting stays the answer if the fleet grows by orders of magnitude or
depending on a free service becomes uncomfortable. Photon is the lighter thing
to host - Nominatim wants a region extract and a great deal of memory - and
Komoot's public Photon instance is a middle option, though it publishes no
numbers, only a request to be fair. Keeping the provider configurable makes that
a later decision rather than a rewrite.

On the cache itself: there is no established project worth depending on. The one
purpose-built thing that exists has no users to speak of, and the generic answer
- nginx `proxy_cache` with `limit_req`, or Varnish - only caches identical URLs,
which reverse geocoding rarely produces. Since the results are being stored
anyway, for the structured components trips keep, the cache is that table rather
than a component in front of it. Matching on proximity rather than exact
coordinates would hit far more often, since a car never parks in quite the same
spot twice, but at this volume that is an optimisation rather than a
requirement.

## Android app

### Allow cleartext to a private address, and only to a private address

Release builds refuse `http://` outright, which was the right instinct and the
wrong rule. The ordinary way this is used is a phone on the same network as the
server - parked on the drive within reach of the house Wi-Fi, or carried indoors
- uploading to a machine that has no certificate and no name on the public
internet. Demanding HTTPS there asks someone to run a certificate authority for
a server only they can reach.

The relaxation should be an advanced option, and it should relax the rule rather
than remove it: `https://` anywhere, `http://` only to an address that cannot
leave the local network. Public addresses stay refused whatever the option says,
so the setting cannot be turned into "send my credential to anyone".

Judging that by the *destination* is what makes it sound. Asking whether the
device itself holds a public address answers a different question and answers it
wrongly - behind a hotspot, behind carrier-grade NAT, on a guest network, the
device has a private address and a perfectly good route to the internet. A
destination in 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, 127.0.0.0/8 or
169.254.0.0/16, or their IPv6 counterparts `::1`, `fc00::/7` and `fe80::/10`, is
unroutable across the public internet as a matter of fact rather than of
configuration.

A hostname should be allowed for the sake of not typing an address, but it has
to be judged on what it resolves to rather than how it is spelled, and resolved
again when the upload is actually made rather than only when the setting is
saved. A name that resolved privately yesterday can resolve publicly today. That
gap cannot be closed completely without connecting by address and carrying the
name in a header, which is more than this is worth - but the check belongs at
the point of use, not only at the point of entry.

The awkward part is that Android's control over cleartext is app-wide.
`usesCleartextTraffic` sits in the manifest, and while a network security
configuration can permit cleartext for named domains, those names are static
resources compiled into the package and cannot express a range, let alone one
the user chooses at runtime. So the platform's own backstop has to come off and
the rule has to live in `ServerUrl` instead. That is a real loss of a guarantee
and worth being deliberate about: what stops a credential going out in clear is
then the app's own arithmetic and nothing beneath it.

Worth doing after the per-device token rather than before. What travels over
cleartext today is the device id, which is also the identity and cannot be
changed without abandoning the vehicle's history; what would travel afterwards
is a token that can be rotated the moment it is suspected. The same relaxation
costs considerably less once there is something revocable to lose.

### Clear the paired identity from the app

A small button next to the identity, behind a confirmation dialog.
`DeviceIdProvider.resetDeviceId()` already exists and has no caller.

With the server issuing identities, this no longer regenerates anything - it
returns the app to being unpaired, and it can only be undone by scanning or
entering a new identity from `www`. The dialog should say that, because
"reset" reads like something recoverable.

It also has to say what happens to data. `TelemetrySampleEntity` carries no
device id: the identity is only ever an `X-Device-ID` header applied at upload
time. So every row still waiting to be uploaded would go up under whatever
identity is paired next - arriving on the server attributed to the wrong
vehicle, or refused outright. Either refuse while rows are pending, or say so
plainly first.

### Keep unuploaded data until the storage runs out, and say so first

`deleteUploadedOlderThan` bounds only the rows that have been uploaded. Nothing
bounds the rest, and the rest is the part that matters: the `/api` bug alone
built up 22,866 rows, and a month of driving with no reachable server would be
far larger. A phone that fills its storage stops being a logger.

The policy that follows from what the data is worth. Postgres is the record once
a row has arrived there, so an uploaded row has no reason to stay on the phone
at all and can go promptly rather than after seven days. An unuploaded row is
the only copy in existence and should survive as long as there is room for it.
Only under real storage pressure should any be dropped, and then the oldest
first, because the recent ones describe where the vehicle is now.

Deleting the only copy of something should never be the first the user hears of
it. The warning belongs well before the threshold - see the entry below - not at
the moment data is discarded.

### Warn when nothing has reached the server

Nothing tells the user that uploads have stopped. The `/api` bug ran for two
months and the only evidence was a number on a diagnostics panel nobody had
reason to look at.

A notification once the newest successful upload is older than some threshold,
and a second, more insistent one as the unuploaded backlog approaches the
storage ceiling above, so that discarding data is never the first notice of a
problem. The figures are already to hand: `TelemetryStats` carries
`lastUploadTime` and `pendingUpload`, and the service already measures the
backlog on a throttle.

The wording should distinguish the two cases the health check draws apart -
unreachable server against unregistered device - because what the user has to do
about them is different.

### Split the foreground service up

detekt records four findings in its baseline rather than at the current
threshold, and three of them are the same observation: `TelemetryForegroundService`
is a large class, with too many functions, containing one long method. They are
baselined rather than configured away because they are true.

The service does several separable jobs. It owns the armed and recording state
machine; it registers and reads sensors; it listens to power and decides which
tier of work the battery still justifies; it assembles and writes samples; and
it maintains a notification. The state machine in particular wants lifting out
into something that takes charge, battery level, whether movement was confirmed
and how long ago as arguments and returns the state that should follow - which
would also make it decidable in a plain JVM test, where today it needs a device.

Nothing is broken, so this is not urgent. It is recorded because the baseline
would otherwise be the only trace of the decision, and a baseline entry read
years later looks like something that was ignored rather than something that
was weighed.

### Cover the upload drain against a fake server

The uploader's own decisions are tested - which response code means what - but
the loop around them is not. Whether it really stops at twenty batches, halves
the batch on a 413 and counts an attempt only where it should, is currently
established by having watched it drain a real backlog once.

None of that needs a device. `work-testing`'s `TestListenableWorkerBuilder`
runs the worker directly, and the uploader speaks `HttpURLConnection` and does
not care who answers, so a fake server such as MockWebServer can play the part
of `ingest` and hand back whichever status the case under test wants. That
covers the retry and quarantine behaviour, which is the part with real
consequences: getting it wrong either loses rows or wedges the queue.

### Do not let the logger state outlive the service that reports it

`LoggerState` lives in a companion object, so it is process-wide rather than
tied to the service instance. Almost always that is right - a restart under
`START_STICKY` sets it again, and a process death resets it to `OFF` - but a
service killed while its process survives would leave the screen reporting
`RECORDING` for something that stopped.

Reading it back from whether the service is really running would mean binding to
it, which is more machinery than the fault deserves. A cheaper answer is to
treat it as a claim rather than a fact: have the service refresh a heartbeat
while it records, and let the screen say so once the claim has gone stale. Worth
doing only if this is ever seen in practice - it is written down so that a
screen insisting on `RECORDING` while nothing is recorded is recognised rather
than puzzled over.

### Find a way to test on API 28 when something depends on it

The instrumented tests run on an `aosp-atd` image at API 30, because Automated
Test Devices exist only at that level, and the handset this is written for runs
API 28. That gap does not matter for a Room migration - SQLite and the
framework behave the same - but it would matter the moment something under test
depends on platform behaviour that changed between the two, and the failure
would be an absence: the emulator would pass and the phone would not.

There is no shortage of ways to close it, only a cost to each. A second managed
device with `apiLevel = 28` and `systemImageSource = "aosp"` needs one
declaration, and Gradle can be told to run a group across both - but a non-ATD
image carries the apps and services ATD strips out, so it boots slower and
wants more memory. Firebase Test Lab would put the tests on real hardware at a
chosen level, at the cost of a Google project and credentials in CI. And a
handset over adb remains the most faithful answer of all, which is what makes
it the local path already; it is only CI that cannot have one.

The thing to avoid is running everything twice by default. Whatever is added
should be reached for when a change actually touches version-dependent
behaviour - a `foregroundServiceType`, a permission model, a storage API - and
not on every pull request, or the emulator that was carefully kept to
migrations alone will quietly become the slowest part of every run.

### Declare a foreground service type before raising the target SDK

`targetSdk` is 28, and that is what keeps several things simple: a foreground
service needs no declared type, background location needs no separate
permission, and cleartext is a manifest attribute rather than a negotiation.
Staying off the Play Store is what makes it tenable, since Play enforces a
minimum target version and nothing else does.

Should the target ever be raised - a newer handset, or a Play listing after all
- the service will need `android:foregroundServiceType="location"` in the
manifest and the `FOREGROUND_SERVICE_LOCATION` permission, or on API 34 and
above it will not be allowed to start at all. API 29 also splits background
location into a permission of its own, which changes what has to be asked for
and when. None of this is work today; all of it is work on the day that number
changes, and it is better known in advance than discovered by a service that
refuses to start.

## Distribution

### Publish signed builds to GitHub Releases for Obtainium

Every install so far has been `adb install` from a workstation, which does not
scale past one phone and gives no way to notice that an update exists.

CI publishes a signed APK to a GitHub release and Obtainium on the phone watches
the repository and offers the update. It needs no server work, and it makes the
app installable by anyone who wants it without anything being pushed on them -
they point Obtainium at the repository or they do not.

One prerequisite regardless: the release build type has no signing configuration
and everything installed so far is debug-signed. Moving to a release key means
the first such install cannot upgrade what is there and has to replace it, which
deletes the database - so the backlog has to be uploaded before that switch, not
after. The keystore then lives as a CI secret, and losing it means no existing
install can ever be upgraded again.

Worth noting that staying off Play is what keeps `targetSdk = 28` tenable at
all, since Play enforces a minimum target version and nothing else does. That is
an argument for this route rather than a consequence of it.
