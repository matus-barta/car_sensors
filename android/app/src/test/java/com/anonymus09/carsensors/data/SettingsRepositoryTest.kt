package com.anonymus09.carsensors.data

import android.content.Context
import androidx.core.content.edit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The migration is the reason these exist.
 *
 * "Stop when unplugged" and "upload only when charging" were renamed to say
 * what they allow rather than what they forbid, which inverts their sense.
 * Getting that wrong does not fail loudly - it leaves a device configured as
 * the opposite of what its owner chose, and nobody finds out until the phone
 * did not record something.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsRepositoryTest {

    private lateinit var context: Context

    private fun prefs() = context.getSharedPreferences("car_sensors_prefs", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        prefs().edit { clear() }
    }

    @Test
    fun `a fresh install gets the documented defaults`() {
        val settings = SettingsRepository(context).current()

        assertTrue(settings.autoStartOnBoot)
        assertTrue(settings.wakeOnMotion)
        assertTrue(settings.wifiOnly)
        assertFalse(settings.recordOnBattery)
        assertFalse(settings.uploadOnBattery)
        assertFalse(settings.liveUploadEnabled)
        assertFalse(settings.loggerEnabled)
    }

    @Test
    fun `stop-when-unplugged becomes its opposite, not its copy`() {
        prefs().edit { putBoolean("stop_when_unplugged", true) }

        assertFalse(SettingsRepository(context).current().recordOnBattery)
    }

    @Test
    fun `a device allowed to record unplugged keeps that permission`() {
        prefs().edit { putBoolean("stop_when_unplugged", false) }

        assertTrue(SettingsRepository(context).current().recordOnBattery)
    }

    @Test
    fun `upload-only-when-charging becomes its opposite too`() {
        prefs().edit { putBoolean("upload_only_when_charging", false) }

        assertTrue(SettingsRepository(context).current().uploadOnBattery)
    }

    @Test
    fun `a device held to charging keeps being held to it`() {
        prefs().edit { putBoolean("upload_only_when_charging", true) }

        assertFalse(SettingsRepository(context).current().uploadOnBattery)
    }

    @Test
    fun `migration does not overwrite a value already set under the new name`() {
        prefs().edit {
            putBoolean("stop_when_unplugged", true)
            putBoolean("record_on_battery", true)
        }

        assertTrue(SettingsRepository(context).current().recordOnBattery)
    }

    @Test
    fun `migration is idempotent across repeated construction`() {
        prefs().edit { putBoolean("stop_when_unplugged", false) }

        SettingsRepository(context)
        SettingsRepository(context).setRecordOnBattery(false)

        // The second construction must not resurrect the legacy value.
        assertFalse(SettingsRepository(context).current().recordOnBattery)
    }

    @Test
    fun `the upload url appends the api path the server actually serves`() {
        val settings = SettingsRepository(context)
        settings.setServerBaseUrl("https://example.org")

        assertEquals("https://example.org/api/telemetry/upload", settings.current().uploadUrl)
    }
}
