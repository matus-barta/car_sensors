---
title: "Put reverse geocoding behind a service of its own"
status: backlog
area: geocoding
depends_on: []
---

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
