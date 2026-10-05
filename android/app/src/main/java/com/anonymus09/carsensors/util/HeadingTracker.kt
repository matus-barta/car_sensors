package com.anonymus09.carsensors.util

import android.hardware.SensorManager

/**
 * The compass heading, worked out from the latest accelerometer and
 * magnetometer readings.
 *
 * Updated on every event from either sensor - twenty times a second between
 * them - so the matrices are kept rather than allocated each time. Only the
 * sensor thread calls [update], so they need no synchronisation; [degrees] is
 * read from elsewhere and is volatile.
 */
class HeadingTracker {

    private val rotationMatrix = FloatArray(MATRIX_SIZE)
    private val inclinationMatrix = FloatArray(MATRIX_SIZE)
    private val orientationAngles = FloatArray(ANGLE_COUNT)

    /** Degrees clockwise from magnetic north, or null until both sensors have reported. */
    @Volatile
    var degrees: Float? = null
        private set

    /**
     * Keeps the previous heading when the readings cannot give one - free fall,
     * or a magnetic field too distorted to resolve.
     */
    fun update(accel: FloatArray?, magnet: FloatArray?) {
        if (accel == null || magnet == null) return

        val resolved = SensorManager.getRotationMatrix(
            rotationMatrix,
            inclinationMatrix,
            accel,
            magnet
        )

        if (!resolved) return

        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        val azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()

        // The azimuth comes back in -180..180; a heading is 0..360.
        degrees = (azimuth + FULL_TURN_DEGREES) % FULL_TURN_DEGREES
    }

    private companion object {
        const val MATRIX_SIZE = 9
        const val ANGLE_COUNT = 3
        const val FULL_TURN_DEGREES = 360f
    }
}
