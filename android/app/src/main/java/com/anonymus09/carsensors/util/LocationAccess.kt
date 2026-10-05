package com.anonymus09.carsensors.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * How much location the logger is allowed, as far as recording is concerned.
 *
 * What matters is not the permission names but which starts of the logger get
 * GPS. From Android 11, a foreground service started from the background - by
 * `BootReceiver` after a reboot, or by the system restarting it - receives
 * location only with background access, whatever the app targets. Without it
 * the logger still runs and still writes rows, only with no position in them,
 * so nothing on the phone looks wrong.
 */
enum class LocationAccess {
    /** No location at all. Starting the logger asks for it. */
    NONE,

    /** Only while the app is in front: lost after a reboot. */
    WHILE_IN_USE,

    /** Every start of the logger gets GPS. */
    ALWAYS;

    companion object {
        /**
         * Below Android 10 there is no separate background permission, and
         * foreground location is all the access there is.
         */
        fun from(sdkInt: Int, foreground: Boolean, background: Boolean): LocationAccess = when {
            !foreground -> NONE
            sdkInt < Build.VERSION_CODES.Q || background -> ALWAYS
            else -> WHILE_IN_USE
        }

        fun of(context: Context): LocationAccess {
            fun granted(permission: String) =
                ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED

            val foreground = granted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                granted(Manifest.permission.ACCESS_COARSE_LOCATION)

            val background = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

            return from(Build.VERSION.SDK_INT, foreground, background)
        }
    }
}
