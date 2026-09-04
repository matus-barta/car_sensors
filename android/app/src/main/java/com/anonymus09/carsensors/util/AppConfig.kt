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

    // How long a session has to show real movement before it counts as a
    // journey. Significant motion also fires for a door slamming or the phone
    // being picked up, so GPS has to second the motion sensor's opinion; until
    // it does, a session is on this much shorter leash.
    const val MOTION_CONFIRM_WINDOW_MS = 90 * 1000L

    // How long the vehicle must be still before recording gives way to waiting
    // for it to move again. Long enough to sit at a level crossing or in traffic
    // without the session being torn down and rebuilt.
    const val MOTION_IDLE_TIMEOUT_MS = 5 * 60 * 1000L

    // Above this the vehicle counts as moving. Roughly 5 km/h, high enough that
    // GPS noise while parked does not read as movement.
    const val MOVEMENT_SPEED_MPS = 1.4f

    // Past this, a fix is no longer taken to be where the vehicle is. The last
    // known one keeps being returned after GPS drops out, so without this a
    // phone parked in a tunnel goes on reporting the position it left.
    const val MAX_LOCATION_AGE_MS = 15_000L

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