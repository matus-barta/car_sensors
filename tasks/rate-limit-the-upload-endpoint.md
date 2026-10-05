---
title: "Rate limit the upload endpoint"
status: backlog
area: protocol
depends_on: []
---

There is no request limiting in `ingest` at all. A request is bounded in size -
4 MiB on the wire, 32 MiB expanded - and in nothing else, so a paired device may
post as fast as it can open connections. Authentication narrows who can do that
to devices holding a valid token, which is most of the protection and is why
this is not urgent, but it does not narrow how often.

Two cases it would cover. A phone whose uploader misbehaves - a retry loop that
lost its backoff, say - hammering the service with nobody noticing, which is the
likelier of the two by a distance. And a token that has leaked, where rotation
is the real answer but a limit bounds the damage until somebody notices.

The shape to reach for is a token bucket keyed on the device rather than the
address, since every device behind one home connection shares an address and a
phone on mobile data does not keep one. `tower-governor` is the usual answer for
an axum service and would sit as a layer on the protected routes, which is also
the argument for doing it here rather than in a proxy: the identity to key on is
one the middleware has already established, and a reverse proxy in front knows
only the address. Answering 429 is already understood by the phone, which maps
it to `TRANSIENT` and retries with backoff.

Worth settling when it is written: what the limit should be. The live push is
capped at one request every two seconds per device, and a backlog drain sends up
to twenty batches in a run, so the ceiling has to sit clear of both or it will
punish an ordinary catch-up after an outage.
