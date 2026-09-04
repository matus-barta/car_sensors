package com.anonymus09.carsensors.util

object AppConfig {
    const val SERVER_BASE_URL = "http://192.168.22.141:3000"
    const val TELEMETRY_UPLOAD_URL = "$SERVER_BASE_URL/telemetry/upload"

    const val DB_NAME = "car_sensors.db"
    const val BATCH_SIZE = 500

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