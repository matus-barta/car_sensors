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

Those 32 bytes are rendered as unpadded base64url, which is the ordinary form
for an opaque bearer token and the one RFC 6750's `b64token` syntax expects. It
is worth naming because it is a wire decision rather than a presentation one -
`ingest` hashes the string it receives, so the encoding is part of what the
hash is of, and changing it later means re-pairing every device. An encoding
chosen instead for being read aloud, such as Crockford Base32, was considered
and dropped: it only paid for itself while typing a token by hand was a
supported path, and it no longer is.

The token does not expire. It is valid until it is rotated or the device is
deactivated, and `token_rotated_at` records when it last changed rather than
setting a deadline. An expiry bounds the damage from a credential that cannot
be withdrawn, which is the situation a signed token creates and not this one:
the hash sits on a row `ingest` already reads on every request, so withdrawing
it is a database write that takes effect at once. Against that, a lifetime buys
nothing and introduces a way to lock out a phone that has done nothing wrong -
one parked offline for a fortnight would come back to a credential that had
lapsed, with no way to renew it that does not need the server it cannot reach.

The caveat is the cache rather than the scheme. While the lookup is served from
Valkey a withdrawn token keeps working until that entry expires, so "at once"
is true of the database and only true of the running system once `www` clears
the key - which is the same point the trap above makes.

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

No compatibility window when this ships. The obvious caution would be a nullable
`token_hash` that `ingest` accepts as "the identity alone is enough" until every
device has been re-paired, but that transitional path is the very hole this
entry exists to close, and it would outlive the migration that justified it.
The deployment is one server and one handset in the same hands, so the column,
the server and the app land together and the phone is re-paired by hand. The
token is required from the first request that reaches the new `ingest`.

One trap in the middleware as it stands. `require_known_device` caches
`known_device:{id}` as a bare boolean for five minutes, so a cache hit answers
"this device is known and active" without the database being consulted at all.
Adding a token check naively would leave that hit short-circuiting the
comparison, and a request bearing the wrong token would be accepted for as long
as the entry lives. The cached value has to become the stored hash rather than a
boolean, so that the comparison happens on every request whether or not the
lookup was served from Valkey.

Rotation then needs deciding alongside it: either `www` deletes the cache key
when it mints a new token - it already talks to Valkey for live tracking - or a
window of up to `KNOWN_DEVICE_CACHE_TTL_SECS` in which the old token still works
is accepted and written down. Silently inheriting the second is the outcome to
avoid, given that locking the previous handset out is the point of rotating.

### Let the server issue the identity and the phone scan it

The phone invents its own UUID on first run, and registering it means reading it
off the screen and retyping it into `www`'s "Add vehicle" dialog, whose
`deviceId` field takes any string from 1 to 255 characters without checking that
it even looks like a UUID. A typo registers a device that will never match what
the phone uploads, and nothing surfaces that until telemetry silently never
arrives.

The authority moves to the server. `www` generates the identity and the token
from the entry above, stores them in `known_devices` and shows both as a QR code
and as copyable text. The phone starts with nothing and offers to scan.

The fallback when the camera will not cooperate is copy and paste, not typing:
`www` is on the same network, so the phone opens it in a browser and copies the
values across. Typing them by hand is not a path worth supporting - an identity
and a token together are around ninety characters of random data, and nobody
transcribes that correctly. Signing in to `www` on the phone to reach them
means a password already known rather than a secret being transcribed, which is
the whole difference.

If pairing ever has to work where that is not available - the audience widening
past a single operator, which the Obtainium entry contemplates - the standard
answer is a short single-use code redeemed over HTTP, the shape RFC 8628 uses
for televisions and command-line tools: `www` shows something like `K7M2-9QXA`,
the phone sends it back and receives the real credential. That keeps what is
typed to a few characters without weakening the token behind it. It is a
convenience rather than a requirement, and it is a credential-issuing endpoint
that would need to be single-use, short-lived and rate-limited, so it is not
worth building before somebody actually needs it.
`DeviceIdProvider` stops generating anything, which is a real behavioural change
- an unpaired app has no identity rather than an unregistered one, and every
screen that assumes one exists has to cope with its absence.

ZXing (`zxing-android-embedded`) reads the code without dragging in Play
Services, which is the better trade on the old handset this runs on.

Decided: `www` stops accepting a typed device id altogether, and the field goes.
It had looked as though an escape hatch was needed to adopt devices paired under
the old flow, but it is not - `www` already owns every one of those rows, so
giving an existing vehicle a token is a rotation against an id it already holds.
The id does not change, the history is preserved, and nothing is typed. The same
is true of a replacement handset and of a phone moving between cars, which
leaves no ordinary case that wants the field, and one real hazard that goes with
it: `deviceId` accepts any string of 1 to 255 characters, so a typo registers a
vehicle that silently never receives telemetry.

The one case it does not cover is a phone holding an identity `www` has no row
for at all - a lost or restored database. A free-text id would not rescue that
either, since the hash cannot be recovered without the token, so it wants a
deliberate "adopt this device" flow taking both values rather than the field
that exists today. Not worth building until it happens.

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

The token travels as `Authorization: Bearer <token>` and a `Device-Id` header
keeps carrying the identity, which is the ordinary division rather than an
invention:
a bearer token is what RFC 6750 describes, logging and proxy tooling already
knows to redact that header, and the identity is deliberately not a secret and
stays a plain greppable value that `ingest` looks the row up by. Folding both
into HTTP Basic as an id and password pair would also be standard, but it would
repurpose a header that currently means something clearer.

The `X-` prefix goes at the same time: RFC 6648 deprecated it for new headers
long ago, and `Device-Id` is the current spelling of the same idea. It is a
protocol break, which is exactly why it belongs here - the token already
requires both sides to ship together, so the rename costs nothing on top,
whereas doing it alone later would mean breaking a working deployment for
tidiness. Five places name the header today: the middleware in
`ingest/src/helpers/middleware.rs`, the two Android callers in
`TelemetryUploader` and `ServerHealth`, the request builders in
`ingest/tests/api.rs`, and the protocol description in `ingest/README.md`.

Follow RFC 6750 rather than only borrowing its header. Keeping `Device-Id`
alongside is not a departure from it - the specification governs how a bearer
token travels and is silent on identity, which belongs to the pairing flow -
but its error responses are a part currently missing, and the useful part. A
rejection should carry `WWW-Authenticate: Bearer` with a reason: `invalid_token`
for a credential that is wrong, revoked or malformed, against a bare 401 when
none was presented at all. That is worth having because the phone cannot
presently tell those apart. `UploadOutcome.forResponseCode` collapses 401 and
403 alike into `REFUSED`, which is the reason the question of what to do about
a banned device is still open below - a machine-readable reason answers it
without inventing a private convention.

One requirement will be broken deliberately. Section 5.1 says a bearer token
MUST be sent over TLS, and the Android entry below plans to allow cleartext to
a private address precisely because a LAN server has no certificate. That is a
real exception rather than an oversight, and the trade is the one that entry
already argues: a token that can be rotated the moment it is suspected is what
makes the exposure affordable, which is also why the cleartext relaxation is
sequenced after this work rather than before it.

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

Recording while unpaired is a feature rather than a state to be tolerated. Hand
somebody a spare phone with no server and no account, let them drive, and decide
afterwards what the trip was - which car it belonged to, or whether to keep it
at all. That only works if the untagged rows survive until someone says
otherwise, so an unpaired app must record freely and simply never upload.

What makes it safe is asking at the one moment the answer is known. When pairing
starts, the phone checks whether untagged rows exist and, if they do, asks
before going any further: attach them to the pairing being set up, or discard
them. Both answers are reasonable - a phone that has been sitting in the car all
along should hand its journeys over, and a phone that recorded somebody else's
trip should not - and the user is the only party who knows which. What must not
happen is the question going unasked, because then the rows are silently
adopted by whichever car the phone is pointed at next, which is precisely the
misattribution the pairing column exists to prevent.

Worth deciding at the same time whether discarding offers to export first. The
rows are the only copy, and "wipe them" is an irreversible answer to a question
asked in passing during setup.

This is a Room migration on a table that already holds the only copy of
unuploaded telemetry, so it wants the same care as the last one: rows written
before it lands have no pairing and are indistinguishable from rows recorded
unpaired - which is the same question the pairing prompt above already has to
answer, and it can be left to it.

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
it. `UploadSilenceNotifier` already warns once telemetry has gone unsent for
`UPLOAD_SILENCE_WARNING_MS`, which covers the ordinary case of a server that
has stopped accepting anything. What a storage ceiling adds is a second, more
insistent warning as the backlog approaches it, because a phone that is about
to start discarding rows deserves more notice than one that is merely behind.
That one belongs here, with the threshold it is measured against.

Worth knowing about the existing warning when this is written: it is raised
from the foreground service's throttled backlog check, so it is only evaluated
while the logger is actually recording. A phone that is driven regularly hears
within a day; one that is parked for a month hears nothing until it next
records. That is the right trade for a warning about data still being
collected, but a warning about storage pressure may not want to inherit it.

### Work out why the API 28 managed device will not set itself up

The `api28` device is declared and grouped with `api30atd`, but running it on a
runner fails inside AGP's own `ManagedDeviceInstrumentationTestSetupTask` with
"Cannot query the value of this property because it has no value available".
Only the ATD device runs in CI as a result, so the level this app actually
targets is covered locally - the handset is API 28 - and not automatically,
which was the whole point of adding it.

Two things about the failure are worth keeping, because they narrow it. The
system image installs perfectly well first, so resolving `systemImageSource =
"aosp"` to `system-images;android-28;default;x86` is not the problem; whatever
is unset is needed after that, while the device itself is being created. And
AGP reports that the device "does not specify a testedAbi" when
`testedAbi = "x86"` is plainly set on it and the same setting on `api30atd`
silenced the identical warning there. Something is not reading that device's
configuration, and the missing property is likely the same fault seen from the
other end.

Nothing was found searching for the combination, so this is not a well-trodden
path. Things to try, cheapest first: `systemImageSource = "google"` instead of
`"aosp"`, in case the `default` image family is what is unhandled; a device
profile other than `Pixel 2`; and API 29, which would still be below the ATD
floor of 30 while being a more travelled configuration. The job now runs with
`--stacktrace`, so the next failure should name the property outright.

This cannot be reproduced on the workstation - an arm64 machine cannot run
these x86 images - so each attempt costs a CI run, which is the main reason to
have a theory before trying one.

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

### Give the web application a container image

The Compose file starts PostgreSQL, Valkey, pgAdmin and `ingest`, and stops
there. `www` has no image, so a deployment is only half a deployment: the
README has to tell an operator to build it themselves and run `node build`
beside the stack. It already uses `adapter-node`, so the missing pieces are a
multi-stage Dockerfile, a build workflow shaped like `ingest-build.yml`, and
the service added to `docker-compose.yml`.

One thing to settle first, because it decides whether a published image is
worth publishing. The map's tile and style URLs are read through
`$env/static/public`, which SvelteKit inlines into the bundle at build time
rather than reading at startup. An image built once therefore carries whichever
tile server was configured when it was built, and an operator cannot point it
at their own with an environment variable - they would have to rebuild, which
is most of the reason to have an image gone. Moving those two to
`$env/dynamic/public` is what makes one image serve every deployment. There is
a fallback to the public OpenStreetMap vector server already, so an image built
with neither set does work; it just works one way only.

The secrets are the other half. `DATABASE_URL`, `ORIGIN` and
`BETTER_AUTH_SECRET` are read at runtime and validated at startup, so they are
ordinary environment variables - but `BETTER_AUTH_SECRET` must not acquire a
default, and the Compose file should make its absence a failure rather than
quietly starting with something predictable.

Worth doing before the Android release work rather than after: between them
they are the point at which this project starts having versions, and a badge or
a release note has something true to say only once both pieces ship the same
way.

### Publish signed builds to GitHub Releases for Obtainium

Every install so far has been `adb install` from a workstation, which does not
scale past one phone and gives no way to notice that an update exists.

CI publishes a signed APK to a GitHub release and Obtainium on the phone watches
the repository and offers the update. It needs no server work, and it makes the
app installable by anyone who wants it without anything being pushed on them -
they point Obtainium at the repository or they do not.

The workflow should call the two validation workflows rather than repeat them,
the way "Ingest - build" already calls "Ingest - validation" so that an image is
only built once its checks have passed. Both already expose `workflow_call` for
it. Calling "Android - migration tests" matters most: a release is the last
point at which an unusual failure can be caught before it reaches a phone, and
it is the only place where waiting for the slower API 28 device costs nobody
anything, because nobody is waiting on a release the way they wait on a pull
request.

One prerequisite regardless: the release build type has no signing configuration
and everything installed so far is debug-signed. Moving to a release key means
the first such install cannot upgrade what is there and has to replace it, which
deletes the database - so the backlog has to be uploaded before that switch, not
after. The keystore then lives as a CI secret, and losing it means no existing
install can ever be upgraded again.

Worth noting that staying off Play is what keeps `targetSdk = 28` tenable at
all, since Play enforces a minimum target version and nothing else does. That is
an argument for this route rather than a consequence of it.
