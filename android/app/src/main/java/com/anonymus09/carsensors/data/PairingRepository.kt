package com.anonymus09.carsensors.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Remembers which vehicle this phone is paired with, if any.
 *
 * Deliberately separate storage from the identity the app used to invent for
 * itself. An upgraded install therefore starts unpaired rather than appearing
 * paired with an identity that has no token and would be refused on every
 * upload - the old value is useless under the new scheme, and pretending
 * otherwise would only produce a device that silently never uploads again.
 */
class PairingRepository(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The pairing in force, or null while the app has no identity at all. */
    fun current(): DevicePairing? = devicePairingOrNull(
        deviceId = prefs.getString(KEY_DEVICE_ID, null),
        token = prefs.getString(KEY_DEVICE_TOKEN, null)
    )

    /**
     * Reports the pairing now and again whenever it changes.
     *
     * The screen has to react to pairing and unpairing without being told to
     * look again, the same way it does for the settings.
     */
    fun observe(): Flow<DevicePairing?> = callbackFlow {
        trySend(current())

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_DEVICE_ID || key == KEY_DEVICE_TOKEN) {
                trySend(current())
            }
        }

        prefs.registerOnSharedPreferenceChangeListener(listener)

        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.conflate()

    /**
     * Adopts a pairing, replacing whatever was there.
     *
     * Written as one edit so the screen never observes an identity without its
     * token, which would read as a paired device that cannot authenticate.
     */
    fun save(pairing: DevicePairing) {
        prefs.edit {
            putString(KEY_DEVICE_ID, pairing.deviceId)
            putString(KEY_DEVICE_TOKEN, pairing.token)
        }
    }

    /** Returns the app to being unpaired. */
    fun clear() {
        prefs.edit {
            remove(KEY_DEVICE_ID)
            remove(KEY_DEVICE_TOKEN)
        }
    }

    private companion object {
        const val PREFS_NAME = "device_pairing_prefs"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_TOKEN = "device_token"
    }
}
