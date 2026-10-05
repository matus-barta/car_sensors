---
title: "Keep every sample with the identity it was recorded under"
status: backlog
area: device-auth
depends_on: []
---

`TelemetrySampleEntity` has no device column. The identity is only ever the
`Device-Id` header and bearer token applied at the moment of upload, so the
rows waiting in the database belong to nobody in particular - they belong to
whoever the phone is paired with when they finally go up. Move the phone to a
second car and the first car's unsent journey silently arrives as the second
car's.

Stamping the row when it is written is what makes a phone moving between cars
answerable. The token scheme has shipped, so a vehicle now keeps its identity
while its credential is withdrawn, and pairing already asks what to do with
rows recorded before an identity existed - but that question can only be
answered one way at a time, because the phone holds a single pairing. Draining
one car's backlog with its own credential while recording for another still
needs somewhere to keep both: a local `pairings` table - identity, token, when
it was paired, a label - and a nullable `pairing_id` on `telemetry_samples`
pointing at it. An integer rather than the UUID itself, because at two rows a
second a thirty-six-character string would cost several megabytes a day to
repeat the same fact.

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
  pairing and a replaced handset needs no questions asked. This already holds.
- Rows recorded before any pairing carry none, and the moment worth asking about
  them is when an identity is finally assigned, since the user knows which car
  the phone was sitting in and nothing else does. The prompt that asks this
  exists; what it cannot yet offer is keeping the rows under the pairing they
  were actually recorded under, because there is nowhere to record that.

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
