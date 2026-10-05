package com.anonymus09.carsensors.util

import android.Manifest
import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject

/**
 * What the logger needs from the phone's settings to do its job unattended.
 *
 * Every one of these is the user's to give or Android's to take away, and none
 * of them stops the app from starting - they decide whether what it records is
 * any use, or whether it keeps running at all. So the screen says when one is
 * missing, and the server is told too: a phone in a car is never looked at,
 * and rows reach the server whatever these say.
 *
 * Written under the same keys by `service_started` and by `access_changed`, so
 * a reader takes the newer of the two and needs to know nothing else.
 */
data class AccessState(
    /** Without background access, a logger restarted after a reboot gets no GPS. */
    val location: LocationAccess,

    /**
     * Whether location is precise. From Android 12 the user may allow only an
     * approximate one, which can be off by a kilometre or more - a track drawn
     * from it is no track at all.
     */
    val preciseLocation: Boolean,

    /** Without notifications, the upload warning is never seen. */
    val notificationsEnabled: Boolean,

    /**
     * Exempt from battery optimization ("Unrestricted"). Optimized, Doze may
     * hold back the logger's work while the phone is idle, and Android is free
     * to judge it a heavy app and restrict it.
     */
    val batteryUnrestricted: Boolean,

    /**
     * Background work forbidden outright - the user chose "Restricted" for the
     * app's battery use, or Android put it in the restricted standby group by
     * itself. From Android 13 a restricted app may not run a foreground service
     * at all, so the logger is stopped and cannot start after a reboot.
     */
    val backgroundRestricted: Boolean,

    /**
     * Data Saver is on and this app is not exempt, so nothing it sends in the
     * background goes over mobile data. Only matters when uploads are allowed
     * off Wi-Fi.
     */
    val dataSaverRestricted: Boolean
) {
    fun putInto(payload: JSONObject): JSONObject = payload.apply {
        put("locationAccess", location.name)
        put("preciseLocation", preciseLocation)
        put("notificationsEnabled", notificationsEnabled)
        put("batteryUnrestricted", batteryUnrestricted)
        put("backgroundRestricted", backgroundRestricted)
        put("dataSaverRestricted", dataSaverRestricted)
    }

    companion object {
        fun of(context: Context) = AccessState(
            location = LocationAccess.of(context),
            preciseLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED,
            notificationsEnabled = NotificationManagerCompat.from(
                context
            ).areNotificationsEnabled(),
            batteryUnrestricted = context.getSystemService(PowerManager::class.java)
                .isIgnoringBatteryOptimizations(context.packageName),
            backgroundRestricted = isBackgroundRestricted(context),
            dataSaverRestricted = context.getSystemService(ConnectivityManager::class.java)
                .restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        )

        /*
         * Two ways to the same outcome. The user's "Restricted" choice shows in
         * isBackgroundRestricted; Android placing the app in the restricted
         * standby group by its own judgement - from Android 11, by default from
         * 12 - shows only in the bucket.
         */
        private fun isBackgroundRestricted(context: Context): Boolean {
            val activityManager = context.getSystemService(ActivityManager::class.java)

            if (activityManager.isBackgroundRestricted) return true

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false

            return context.getSystemService(UsageStatsManager::class.java).appStandbyBucket ==
                UsageStatsManager.STANDBY_BUCKET_RESTRICTED
        }
    }
}
