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

    // optional payload (used by events)
    val payload: String?,

    // Power
    val charging: Boolean,
    val powerSource: String?,

    // GPS
    val latitude: Double?,
    val longitude: Double?,
    val altitude: Double?,
    val speedMps: Float?,
    val speedKmh: Float?,
    val bearing: Float?,
    val accuracyM: Float?,
    val provider: String?,

    // Sensors
    val accelX: Float?,
    val accelY: Float?,
    val accelZ: Float?,
    val accelAccuracy: Int?,
    val accelAccuracyLabel: String?,

    val gyroX: Float?,
    val gyroY: Float?,
    val gyroZ: Float?,
    val gyroAccuracy: Int?,
    val gyroAccuracyLabel: String?,

    val magX: Float?,
    val magY: Float?,
    val magZ: Float?,
    val magnetAccuracy: Int?,
    val magnetAccuracyLabel: String?,

    val headingDeg: Float?,

    val pressureHpa: Float?,
    val pressureAccuracy: Int?,
    val pressureAccuracyLabel: String?,


    // Upload state
    val uploaded: Boolean = false,
    val uploadedAt: Long? = null,
    val uploadAttemptCount: Int = 0

)