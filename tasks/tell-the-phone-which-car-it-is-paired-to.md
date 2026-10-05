---
title: "Tell the phone which car it is paired to"
status: backlog
area: device-auth
depends_on: []
---

The app shows the raw identity - `f4075710-dae3-420e-89b7-bd8f3e382d9f` - and
that is the one thing about the pairing nobody can read. Somebody holding the
handset wants to see "Škoda Fabia", which is what the vehicle is called
everywhere else, and a UUID answers a question only the server is asking.

The name can travel with the credential. The pairing payload is already a
versioned JSON object carrying `deviceId` and `token`, so adding a `name`
alongside them costs nothing and needs no version bump - a build that does not
know the field ignores it, and one that does falls back to the identity when it
is absent. `PairingRepository` would keep it beside the other two, and the
screen would show the name with the identity underneath in a smaller face,
since the identity still has to be readable when something needs diagnosing.

Worth being clear that this is a label and not a fact. A vehicle renamed in the
web application does not tell the phones paired with it, so what is displayed is
whatever the name was at the moment of pairing. That is acceptable for something
whose purpose is recognition rather than identification, but it should not be
used for anything that has to be current, and re-pairing is what refreshes it.

The same payload could reasonably carry the server address too, which would
remove the other thing that has to be typed into a fresh phone by hand. It is
not free the way the name is: `www` knows its own origin but not where `ingest`
listens, so something would have to be configured for it to send - probably an
`INGEST_PUBLIC_URL` that an operator sets alongside the rest. Worth doing
together with the name if it is done at all, since both change the same payload
and the same screen.

**Related.** [Keep every sample with the identity it was recorded
under](keep-every-sample-with-the-identity-it-was-recorded-under.md) plans a
local `pairings` table with a label, which is where this name would end up.
