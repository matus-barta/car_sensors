package com.anonymus09.carsensors.util

import android.hardware.SensorManager

/** How a sensor rates its own reading, as the rows record it. */
fun sensorAccuracyLabel(accuracy: Int): String = when (accuracy) {
    SensorManager.SENSOR_STATUS_UNRELIABLE -> "UNRELIABLE"
    SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "LOW"
    SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "MEDIUM"
    SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "HIGH"
    else -> "UNKNOWN"
}
