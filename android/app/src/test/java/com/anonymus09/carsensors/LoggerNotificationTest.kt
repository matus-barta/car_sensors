package com.anonymus09.carsensors

import android.location.Location
import com.anonymus09.carsensors.data.PowerTier
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * What the logger's notification says.
 *
 * On a phone in a car it is the one part of the app anybody is likely to
 * glance at, so it has to tell recording from waiting from cut back - being
 * cut back looks identical to being broken unless it is said.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LoggerNotificationTest {

    private val notification = LoggerNotification(RuntimeEnvironment.getApplication())

    private val fix = Location("gps").apply {
        latitude = 48.1486
        longitude = 17.1077
        speed = 13.9f
    }

    private val recording = LoggerNotification.Content(
        loggerState = LoggerState.RECORDING,
        powerTier = PowerTier.FULL,
        location = fix,
        charging = true,
        powerSource = "AC",
        headingDegrees = 89.6f
    )

    @Test
    fun `recording says where, how fast, on what power and which way`() {
        assertEquals(
            listOf(
                "Logging active",
                "GPS: 48.14860, 17.10770 | 50 km/h",
                "Power: AC",
                "Heading: 90°"
            ),
            notification.describe(recording)
        )
    }

    @Test
    fun `waiting for movement is told apart from recording`() {
        val lines = notification.describe(recording.copy(loggerState = LoggerState.ARMED))

        assertEquals("Waiting for movement", lines.first())
    }

    @Test
    fun `being cut back by the battery is said, not left to look broken`() {
        val lines = notification.describe(
            recording.copy(powerTier = PowerTier.REDUCED_RATE, charging = false)
        )

        assertEquals("Logging active (battery saving: reduced rate)", lines[0])
        assertEquals("Power: unplugged", lines[2])
    }

    @Test
    fun `without a current fix or a heading it says so rather than going blank`() {
        val lines = notification.describe(recording.copy(location = null, headingDegrees = null))

        assertEquals("GPS: waiting", lines[1])
        assertEquals("Heading: n/a", lines[3])
    }
}
