package com.anonymus09.carsensors

import android.app.Application
import android.content.Intent
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.anonymus09.carsensors.data.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Whether the logger comes back after a reboot, and only when it should.
 *
 * A phone left in a car gets rebooted with nobody looking, so this is the only
 * thing standing between a restart and a week of nothing recorded. It should
 * come back only when the user both allowed auto-start and left the logger on -
 * a logger switched off by hand must stay off however often the phone restarts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BootReceiverTest {

    private lateinit var context: Application
    private lateinit var settings: SettingsRepository

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        settings = SettingsRepository(context)

        // The receiver schedules an upload, which needs WorkManager to exist.
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
    }

    private fun receive(action: String?) =
        BootReceiver().onReceive(context, action?.let { Intent(it) })

    private fun startedService(): Intent? = shadowOf(context).nextStartedService

    private fun uploadsScheduled(): Int = WorkManager.getInstance(context)
        .getWorkInfosForUniqueWork(UPLOAD_WORK_NAME)
        .get()
        .size

    private fun leaveLogger(autoStart: Boolean, enabled: Boolean) {
        settings.setAutoStartOnBoot(autoStart)
        settings.setLoggerEnabled(enabled)
    }

    @Test
    fun `a reboot restores a logger that was left on`() {
        leaveLogger(autoStart = true, enabled = true)

        receive(Intent.ACTION_BOOT_COMPLETED)

        assertEquals(
            TelemetryForegroundService::class.java.name,
            startedService()?.component?.className
        )
        assertEquals("the backlog gets a chance to go up too", 1, uploadsScheduled())
    }

    @Test
    fun `an update restores it the same way`() {
        leaveLogger(autoStart = true, enabled = true)

        receive(Intent.ACTION_MY_PACKAGE_REPLACED)

        assertEquals(
            TelemetryForegroundService::class.java.name,
            startedService()?.component?.className
        )
    }

    @Test
    fun `a logger switched off by hand stays off`() {
        leaveLogger(autoStart = true, enabled = false)

        receive(Intent.ACTION_BOOT_COMPLETED)

        assertNull(startedService())
        assertEquals(0, uploadsScheduled())
    }

    @Test
    fun `without auto-start nothing comes back`() {
        leaveLogger(autoStart = false, enabled = true)

        receive(Intent.ACTION_BOOT_COMPLETED)

        assertNull(startedService())
    }

    @Test
    fun `any other broadcast is ignored`() {
        leaveLogger(autoStart = true, enabled = true)

        receive(Intent.ACTION_POWER_CONNECTED)
        receive(null)

        assertNull(startedService())
    }

    private companion object {
        /*
         * Restated rather than read from WifiUploadScheduler, which keeps it
         * private: a test that read it would agree with the scheduler however
         * the scheduler changed.
         */
        const val UPLOAD_WORK_NAME = "telemetry_wifi_upload"
    }
}
