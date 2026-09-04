package com.anonymus09.carsensors.util

object AppConfig {
    // Only the starting point: the address is a setting now, so it can be
    // corrected on the device instead of in a rebuild.
    const val DEFAULT_SERVER_BASE_URL = "http://192.168.22.141:3000"

    // ingest nests every route under /api. Without the prefix an upload posts
    // to a path that does not exist and comes back 404, which is what had been
    // happening: the backlog only ever grew.
    const val TELEMETRY_UPLOAD_PATH = "/api/telemetry/upload"

    const val DB_NAME = "car_sensors.db"
    const val BATCH_SIZE = 500

    // A run stops after this many batches and leaves the rest to the next one,
    // so a large backlog cannot push a single pass past WorkManager's execution
    // window. At BATCH_SIZE this drains 10,000 rows a run.
    const val UPLOAD_MAX_BATCHES_PER_RUN = 20

    // The batch is halved each time the server answers 413, down to this floor.
    // Below it the body is not what is too large, and retrying will not help.
    const val UPLOAD_MIN_BATCH_SIZE = 25

    // How many outright refusals a row survives before it stops being retried.
    // Only a refusal counts, never an unreachable server, so a long outage does
    // not burn through this.
    const val UPLOAD_MAX_ATTEMPTS = 5

    // Live upload sends at most this many of the newest located rows. It only
    // has to move the map, and the batch path carries everything else.
    const val LIVE_PUSH_MAX_ROWS = 10

    // A floor under "as soon as the position changes". GPS reports about once a
    // second, and without this a drive would be one request per second for its
    // whole length.
    const val LIVE_PUSH_MIN_INTERVAL_MS = 2_000L

    // How long an uploaded row is kept before it is deleted.
    const val UPLOADED_ROW_RETENTION_MS = 7L * 24 * 60 * 60 * 1000

    // 10 Hz
    const val SENSOR_SAMPLING_US = 100_000
    // Write one merged sample every 500 ms
    const val FLUSH_INTERVAL_MS = 500L

    // Wake the uploader once this many rows are waiting to be sent.
    const val UPLOAD_TRIGGER_PENDING_ROWS = 200

    // How often that backlog is measured, counted in written samples. At a
    // 500 ms flush interval this is one COUNT(*) a minute rather than two a
    // second.
    const val UPLOAD_CHECK_EVERY_N_SAMPLES = 120

    const val DB_STATS_REFRESH_RATE = 5
}