package com.anonymus09.carsensors.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.anonymus09.carsensors.util.AppConfig.DEFAULT_SERVER_BASE_URL
import com.anonymus09.carsensors.util.AppConfig.TELEMETRY_UPLOAD_PATH
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** The switches on the main screen, plus whether the logger was left on. */
data class TelemetrySettings(
    val autoStartOnBoot: Boolean = true,
    val wakeOnMotion: Boolean = true,
    val recordOnBattery: Boolean = false,
    val uploadOnBattery: Boolean = false,
    val wifiOnly: Boolean = true,
    val liveUploadEnabled: Boolean = false,
    val serverBaseUrl: String = DEFAULT_SERVER_BASE_URL,
    /**
     * Whether the user last left the logger switched on.
     *
     * Not a preference but remembered state, so a reboot or a process death can
     * put the logger back the way it was found rather than leaving a phone in a
     * car recording nothing.
     */
    val loggerEnabled: Boolean = false
) {
    /** Where an upload is actually posted. */
    val uploadUrl: String get() = serverBaseUrl + TELEMETRY_UPLOAD_PATH
}

/**
 * Reads and writes the user's settings, and reports when they change.
 *
 * These were static accessors on TelemetryForegroundService, which gave the UI
 * no way to hear about a change: it read each one once into `remember` and kept
 * that value for the life of the composition.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateInvertedKeys()
    }

    /**
     * Carries the two inverted settings over to their new names.
     *
     * "Stop when unplugged" and "upload only when charging" were renamed to say
     * what they allow rather than what they forbid, which flips their sense. A
     * plain rename would silently reset a device to the opposite of what its
     * owner chose, so the old value is read once and written the other way up.
     */
    private fun migrateInvertedKeys() {
        if (prefs.contains(LEGACY_KEY_STOP_WHEN_UNPLUGGED) &&
            !prefs.contains(KEY_RECORD_ON_BATTERY)
        ) {
            val stopWhenUnplugged = prefs.getBoolean(LEGACY_KEY_STOP_WHEN_UNPLUGGED, true)
            prefs.edit { putBoolean(KEY_RECORD_ON_BATTERY, !stopWhenUnplugged) }
        }

        if (prefs.contains(LEGACY_KEY_UPLOAD_ONLY_WHEN_CHARGING) &&
            !prefs.contains(KEY_UPLOAD_ON_BATTERY)
        ) {
            val onlyWhenCharging = prefs.getBoolean(LEGACY_KEY_UPLOAD_ONLY_WHEN_CHARGING, true)
            prefs.edit { putBoolean(KEY_UPLOAD_ON_BATTERY, !onlyWhenCharging) }
        }
    }

    fun current(): TelemetrySettings = TelemetrySettings(
        autoStartOnBoot = prefs.getBoolean(KEY_AUTO_START_ON_BOOT, true),
        wakeOnMotion = prefs.getBoolean(KEY_WAKE_ON_MOTION, true),
        recordOnBattery = prefs.getBoolean(KEY_RECORD_ON_BATTERY, false),
        uploadOnBattery = prefs.getBoolean(KEY_UPLOAD_ON_BATTERY, false),
        wifiOnly = prefs.getBoolean(KEY_WIFI_ONLY, true),
        liveUploadEnabled = prefs.getBoolean(KEY_LIVE_UPLOAD, false),
        serverBaseUrl = prefs.getString(KEY_SERVER_BASE_URL, null) ?: DEFAULT_SERVER_BASE_URL,
        loggerEnabled = prefs.getBoolean(KEY_LOGGER_ENABLED, false)
    )

    fun setAutoStartOnBoot(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_AUTO_START_ON_BOOT, enabled) }

    fun setLoggerEnabled(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_LOGGER_ENABLED, enabled) }

    fun setWakeOnMotion(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_WAKE_ON_MOTION, enabled) }

    fun setRecordOnBattery(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_RECORD_ON_BATTERY, enabled) }

    fun setWifiOnly(enabled: Boolean) = prefs.edit { putBoolean(KEY_WIFI_ONLY, enabled) }

    fun setUploadOnBattery(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_UPLOAD_ON_BATTERY, enabled) }

    fun setLiveUploadEnabled(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_LIVE_UPLOAD, enabled) }

    /** Expects an address already through [com.anonymus09.carsensors.util.ServerUrl]. */
    fun setServerBaseUrl(baseUrl: String) =
        prefs.edit { putString(KEY_SERVER_BASE_URL, baseUrl) }

    /**
     * Emits the settings now, and again whenever any of them is written -
     * including from the service or the boot receiver, not just from the UI.
     */
    fun observe(): Flow<TelemetrySettings> = callbackFlow {
        trySend(current())

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            trySend(current())
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    companion object {
        private const val PREFS_NAME = "car_sensors_prefs"
        private const val KEY_AUTO_START_ON_BOOT = "auto_start_on_boot"
        private const val KEY_LOGGER_ENABLED = "logger_enabled"
        private const val KEY_WAKE_ON_MOTION = "wake_on_motion"
        private const val KEY_RECORD_ON_BATTERY = "record_on_battery"
        private const val KEY_UPLOAD_ON_BATTERY = "upload_on_battery"
        private const val KEY_WIFI_ONLY = "wifi_only"

        // What the two above were called when they meant the opposite.
        private const val LEGACY_KEY_STOP_WHEN_UNPLUGGED = "stop_when_unplugged"
        private const val LEGACY_KEY_UPLOAD_ONLY_WHEN_CHARGING = "upload_only_when_charging"
        private const val KEY_LIVE_UPLOAD = "live_upload_enabled"
        private const val KEY_SERVER_BASE_URL = "server_base_url"
    }
}
