package com.anonymus09.carsensors.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/** The switches on the main screen. */
data class TelemetrySettings(
    val autoStartOnBoot: Boolean = true,
    val stopWhenUnplugged: Boolean = true,
    val uploadOnlyWhenCharging: Boolean = true
)

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

    fun current(): TelemetrySettings = TelemetrySettings(
        autoStartOnBoot = prefs.getBoolean(KEY_AUTO_START_ON_BOOT, true),
        stopWhenUnplugged = prefs.getBoolean(KEY_STOP_WHEN_UNPLUGGED, true),
        uploadOnlyWhenCharging = prefs.getBoolean(KEY_UPLOAD_ONLY_WHEN_CHARGING, true)
    )

    fun setAutoStartOnBoot(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_AUTO_START_ON_BOOT, enabled) }

    fun setStopWhenUnplugged(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_STOP_WHEN_UNPLUGGED, enabled) }

    fun setUploadOnlyWhenCharging(enabled: Boolean) =
        prefs.edit { putBoolean(KEY_UPLOAD_ONLY_WHEN_CHARGING, enabled) }

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
        private const val KEY_STOP_WHEN_UNPLUGGED = "stop_when_unplugged"
        private const val KEY_UPLOAD_ONLY_WHEN_CHARGING = "upload_only_when_charging"
    }
}
