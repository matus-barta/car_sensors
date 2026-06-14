
package com.anonymus09.carsensors.util

import android.content.Context
import androidx.core.content.edit
import java.util.UUID

object DeviceIdProvider {

    private const val PREFS_NAME = "device_id_prefs"
    private const val KEY_DEVICE_ID = "device_id"

    /**
     * Returns a persistent unique ID for this app installation.
     * Generates it on first call, then reuses it.
     */
    fun getOrCreateDeviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) {
            return existing
        }

        val newId = UUID.randomUUID().toString()

        prefs.edit {
            putString(KEY_DEVICE_ID, newId)
        }

        return newId
    }

    /**
     * Optional: force reset (useful for debugging)
     */
    fun resetDeviceId(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit {
                remove(KEY_DEVICE_ID)
            }
    }
}
