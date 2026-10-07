---
title: "Review the locations the tests use"
status: backlog
area: tools
depends_on: []
---

Nearly every position in the tests and examples is in Bratislava, most of them
its centre, 48.1486 N 17.1077 E. Nothing there is anyone's address, but
together they say where the project comes from, and the README's screenshots
already use made-up data in Lisbon instead. Worth deciding once, on purpose,
whether that stays.

Where the positions are:

- the Android tests:
  [`TelemetryRecordingTest.kt`](../../android/app/src/androidTest/java/com/anonymus09/carsensors/TelemetryRecordingTest.kt)
  and [`LoggerNotificationTest.kt`](../../android/app/src/test/java/com/anonymus09/carsensors/LoggerNotificationTest.kt),
  which also asserts the formatted text `48.14860, 17.10770`;
- `ingest`: the API example in
  [`telemetry_sample.rs`](../../ingest/src/models/telemetry_sample.rs), which
  [`openapi.json`](../api/openapi.json) is generated from, and
  [`tests/api.rs`](../../ingest/tests/api.rs);
- `www`: [`vehicles.e2e.ts`](../../www/e2e/vehicles.e2e.ts), which asserts the
  coordinates the vehicle card shows, and the unit tests
  [`vehicle-camera.spec.ts`](../../www/src/lib/map/vehicle-camera.spec.ts) and
  [`default-view.spec.ts`](../../www/src/lib/map/default-view.spec.ts), the
  second by time zone name, `Europe/Bratislava`;
- the telemetry simulator,
  [`simulate-telemetry.js`](../../tools/scripts/simulate-telemetry.js), which
  drives along Bratislava airport's runway.

A replacement has to be a real place, since the end-to-end tests and the
simulator show the positions on a real map; a public landmark or a city centre,
never a home or a workplace. Changing it means changing the coordinates and
the text asserted from them together, and regenerating `openapi.json`.

[`renovate.json`](../../renovate.json)'s `"timezone": "Europe/Bratislava"`
gives the same hint, but it is a setting rather than test data: it decides when
Renovate's schedule runs, so it changes only with a reason of its own.
