package com.anonymus09.carsensors

/**
 * What the logger is doing.
 *
 * [ARMED] is the parked state: the service stays alive so that nothing has to
 * wake it, but the sensors and GPS are unregistered and only the hardware
 * significant-motion trigger is listening. It costs almost nothing and is what
 * lets a phone live in a car unattended without either recording a stationary
 * vehicle around the clock or needing to be restarted by hand.
 */
enum class LoggerState { OFF, ARMED, RECORDING }

/** The latest fix as the screen shows it, published by the logger. */
data class TelemetryLocationStatus(
    val hasFix: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val speedKmh: Int? = null,
    val provider: String? = null,
    val accuracy: Float? = null
)
