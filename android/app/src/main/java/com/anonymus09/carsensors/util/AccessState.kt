package com.anonymus09.carsensors.util

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject

/**
 * What the logger needs from the user's settings to do its job unattended, as
 * reported to the server.
 *
 * A phone in a car is never looked at, so the screen saying a permission is
 * missing helps nobody until somebody opens it. Rows reach the server whatever
 * these say, which makes them the one channel that does not depend on the
 * thing that is missing.
 *
 * Written under the same keys by `service_started` and by `access_changed`, so
 * a reader takes the newer of the two and needs to know nothing else.
 */
data class AccessState(
    /** Without background access, a logger restarted after a reboot gets no GPS. */
    val location: LocationAccess,

    /** Without notifications, the upload warning is never seen. */
    val notificationsEnabled: Boolean
) {
    fun putInto(payload: JSONObject): JSONObject = payload.apply {
        put("locationAccess", location.name)
        put("notificationsEnabled", notificationsEnabled)
    }

    companion object {
        fun of(context: Context) = AccessState(
            location = LocationAccess.of(context),
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        )
    }
}
