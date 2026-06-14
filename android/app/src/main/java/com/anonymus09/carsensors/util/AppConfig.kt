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

    const val DB_STATS_REFRESH_RATE = 5
}