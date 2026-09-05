package com.anonymus09.carsensors.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HOUR = 60L * 60 * 1000
private const val DAY = 24 * HOUR

class UploadProgressTest {

    @Test
    fun `nothing pending is not a silence`() {
        val progress = UploadProgress(pendingRows = 0, lastUploadedAt = 1_000)

        assertNull(progress.waitingMs(now = 1_000 + DAY))
    }

    @Test
    fun `a successful upload is the clock while there has been one`() {
        val progress = UploadProgress(
            pendingRows = 10,
            lastUploadedAt = 5 * HOUR,
            // Older, and deliberately ignored: the path demonstrably works.
            oldestPendingAt = 1 * HOUR
        )

        assertEquals(2 * HOUR, progress.waitingMs(now = 7 * HOUR))
    }

    @Test
    fun `the oldest waiting row answers once uploaded rows have been pruned`() {
        /*
         * The retention sweep removes uploaded rows after a week, so a long
         * outage erases the very evidence of when uploading last worked. The
         * backlog itself still knows how long it has been waiting.
         */
        val progress = UploadProgress(
            pendingRows = 40_000,
            lastUploadedAt = null,
            oldestPendingAt = 10 * DAY
        )

        assertEquals(50 * DAY, progress.waitingMs(now = 60 * DAY))
    }

    @Test
    fun `a clock that went backwards reads as no wait rather than a negative one`() {
        val progress = UploadProgress(pendingRows = 1, lastUploadedAt = 10 * HOUR)

        assertEquals(0L, progress.waitingMs(now = 2 * HOUR))
    }

    @Test
    fun `pending rows with no timestamps at all cannot be timed`() {
        val progress = UploadProgress(pendingRows = 5)

        assertNull(progress.waitingMs(now = DAY))
    }
}

class UploadSilenceMessageTest {

    private val settings = TelemetrySettings(serverBaseUrl = "http://192.168.1.9:3000")

    private fun message(
        health: ServerHealth,
        settings: TelemetrySettings = this.settings,
        pendingRows: Int = 1_200,
        waitingMs: Long = 2 * DAY
    ) = uploadSilenceMessage(health, settings, pendingRows, waitingMs)

    @Test
    fun `the backlog and how long it has waited lead every message`() {
        assertTrue(
            message(ServerHealth.Unreachable)
                .startsWith("1200 samples waiting for 2 days.")
        )
    }

    @Test
    fun `a single sample is not pluralised`() {
        assertTrue(
            message(ServerHealth.Unreachable, pendingRows = 1, waitingMs = HOUR)
                .startsWith("1 sample waiting for 1 hour.")
        )
    }

    @Test
    fun `an unreachable server names the address to check`() {
        assertTrue(message(ServerHealth.Unreachable).contains("http://192.168.1.9:3000"))
    }

    @Test
    fun `an unregistered device is sent to the web application`() {
        // The remedy differs from an unreachable server, so the wording must.
        assertTrue(
            message(ServerHealth.DeviceUnknown).contains("Register it in the web application")
        )
    }

    @Test
    fun `a deactivated device is told it was deactivated`() {
        assertTrue(message(ServerHealth.DeviceDeactivated).contains("deactivated"))
    }

    @Test
    fun `a server fault carries its status code`() {
        assertTrue(message(ServerHealth.ServerFault(503)).contains("503"))
    }

    @Test
    fun `a reachable server names the conditions the upload is waiting on`() {
        val bothConstraints = message(
            ServerHealth.Ok,
            settings = settings.copy(wifiOnly = true, uploadOnBattery = false)
        )

        assertTrue(bothConstraints.contains("Wi-Fi and a charger"))
    }

    @Test
    fun `a reachable server names only the condition that applies`() {
        val wifiOnly = message(
            ServerHealth.Ok,
            settings = settings.copy(wifiOnly = true, uploadOnBattery = true)
        )

        assertTrue(wifiOnly.contains("waiting for Wi-Fi."))

        val chargerOnly = message(
            ServerHealth.Ok,
            settings = settings.copy(wifiOnly = false, uploadOnBattery = false)
        )

        assertTrue(chargerOnly.contains("waiting for a charger."))
    }

    @Test
    fun `a reachable server with nothing to wait for says so plainly`() {
        val unexplained = message(
            ServerHealth.Ok,
            settings = settings.copy(wifiOnly = false, uploadOnBattery = true)
        )

        assertTrue(unexplained.contains("not getting through"))
    }
}
