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

    /** The pairing together with whatever the server last said about it. */
    fun status(): PairingStatus = PairingStatus(
        pairing = current(),
        rejection = prefs.getString(KEY_REJECTION, null)?.let {
            /*
             * An unrecognised value means the enum changed under a stored
             * string. Reading it as "nothing is wrong" is the safe way round:
             * the next upload settles it either way.
             */
            runCatching { PairingRejection.valueOf(it) }.getOrNull()
        }
    )

    /**
     * Records why the server turned this phone away, or clears it with null.
     *
     * Written from the upload path, which is the only thing that finds out.
     * Persisted rather than held in memory because the uploader runs long after
     * the screen has gone, and "this vehicle has been retired" is exactly the
     * kind of thing that must survive to be read later.
     */
    fun recordRejection(rejection: PairingRejection?) {
        if (rejection == status().rejection) return

        prefs.edit {
            if (rejection == null) remove(KEY_REJECTION) else putString(KEY_REJECTION, rejection.name)
        }
    }

    /**
     * Reports the pairing now and again whenever it changes.
     *
     * The screen has to react to pairing and unpairing without being told to
     * look again, the same way it does for the settings.
     */
    fun observe(): Flow<PairingStatus> = callbackFlow {
        trySend(status())

        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_DEVICE_ID || key == KEY_DEVICE_TOKEN || key == KEY_REJECTION) {
                trySend(status())
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

            // A fresh credential has not been refused yet.
            remove(KEY_REJECTION)
        }
    }

    /** Returns the app to being unpaired. */
    fun clear() {
        prefs.edit {
            remove(KEY_DEVICE_ID)
            remove(KEY_DEVICE_TOKEN)
            remove(KEY_REJECTION)
        }
    }

    private companion object {
        const val PREFS_NAME = "device_pairing_prefs"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_TOKEN = "device_token"
        const val KEY_REJECTION = "credential_rejection"
    }
}
