---
title: "Derive trips on the server from the uploaded track"
status: backlog
area: trips
depends_on: []
---

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
