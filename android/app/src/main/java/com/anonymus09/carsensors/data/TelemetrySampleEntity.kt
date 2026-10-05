package com.anonymus09.carsensors.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "telemetry_samples",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["uploaded"])
    ]
)
data class TelemetrySampleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val event: String,
    val timestamp: Long,

    /*
     * Everything from here to the upload state is optional, and defaults to
     * absent so that an event row - which has no position and no sensor
     * readings - names only what it carries. Only the Kotlin constructor
     * changes; the columns and the exported schema do not.
     */

    // optional payload (used by events)
    val payload: String? = null,

    // Power
    val charging: Boolean,
    val powerSource: String? = null,

    // GPS
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val speedMps: Float? = null,
    val speedKmh: Float? = null,
    val bearing: Float? = null,
    val accuracyM: Float? = null,
    val provider: String? = null,

    // Sensors
    val accelX: Float? = null,
    val accelY: Float? = null,
    val accelZ: Float? = null,
    val accelAccuracy: Int? = null,
    val accelAccuracyLabel: String? = null,

    val gyroX: Float? = null,
    val gyroY: Float? = null,
    val gyroZ: Float? = null,
    val gyroAccuracy: Int? = null,
    val gyroAccuracyLabel: String? = null,

    val magX: Float? = null,
    val magY: Float? = null,
    val magZ: Float? = null,
    val magnetAccuracy: Int? = null,
    val magnetAccuracyLabel: String? = null,

    val headingDeg: Float? = null,

    val pressureHpa: Float? = null,
    val pressureAccuracy: Int? = null,
    val pressureAccuracyLabel: String? = null,

    // Upload state
    val uploaded: Boolean = false,
    val uploadedAt: Long? = null,
    val uploadAttemptCount: Int = 0

)
