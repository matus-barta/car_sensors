package com.anonymus09.carsensors.util

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The names the server reads the phone's state by.
 *
 * `service_started` and `access_changed` carry these into `telemetry_samples`,
 * and the web application is to read them back by name - see
 * `docs/tasks/show-on-the-vehicle-card-what-the-phone-is-not-allowed-to-do.md`. Renaming one
 * here would break that silently, on the server, long after the phone was
 * updated, so the names are restated rather than read from the code under test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ReportedFieldsTest {

    @Test
    fun `access is reported under the names the server reads`() {
        val payload = AccessState(
            location = LocationAccess.WHILE_IN_USE,
            preciseLocation = false,
            notificationsEnabled = true,
            batteryUnrestricted = false,
            backgroundRestricted = true,
            dataSaverRestricted = false
        ).putInto(JSONObject())

        assertEquals(
            setOf(
                "locationAccess",
                "preciseLocation",
                "notificationsEnabled",
                "batteryUnrestricted",
                "backgroundRestricted",
                "dataSaverRestricted"
            ),
            payload.keys().asSequence().toSet()
        )

        // The enum's name is the value, so renaming a constant is a change too.
        assertEquals("WHILE_IN_USE", payload.getString("locationAccess"))
        assertEquals(false, payload.getBoolean("preciseLocation"))
        assertEquals(true, payload.getBoolean("backgroundRestricted"))
    }

    @Test
    fun `location access keeps the three values the server expects`() {
        assertEquals(
            listOf("NONE", "WHILE_IN_USE", "ALWAYS"),
            LocationAccess.entries.map { it.name }
        )
    }

    @Test
    fun `the previous exit is reported under the names the server reads`() {
        val payload = LastExit(
            reason = "USER_REQUESTED",
            description = "fully stop by user request",
            atMs = 1_791_123_467_720
        ).putInto(JSONObject())

        assertEquals(
            setOf("previousExitReason", "previousExitDescription", "previousExitAt"),
            payload.keys().asSequence().toSet()
        )
        assertEquals("USER_REQUESTED", payload.getString("previousExitReason"))
        assertEquals(1_791_123_467_720, payload.getLong("previousExitAt"))
    }

    @Test
    fun `a stop from Active apps or Force stop is the one the screen explains`() {
        assertEquals(true, LastExit("USER_REQUESTED", null, 0).stoppedByUser)
        assertEquals(false, LastExit("LOW_MEMORY", null, 0).stoppedByUser)
        assertEquals(false, LastExit("CRASH", null, 0).stoppedByUser)
    }
}
