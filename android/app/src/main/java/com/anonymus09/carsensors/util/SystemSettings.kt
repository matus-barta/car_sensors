package com.anonymus09.carsensors.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.net.toUri

/**
 * The places outside the app where the user changes what it is allowed to do.
 *
 * Each goes as close to the one switch that matters as Android lets an app go.
 * Some go straight to it; for the rest, the app's own page is as near as an app
 * may reach, and the screen names the taps from there.
 */
object SystemSettings {

    /*
     * The app's own page in the system's settings, where its permissions and
     * its battery use are.
     *
     * Also the answer once Android stops showing a permission dialog: asking
     * then comes back refused without the user seeing anything, and this page
     * is the only place left to change the answer. Not its location permission
     * page, because that one cannot be opened by an ordinary app.
     */
    fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))

    /*
     * Straight to the switch: Android lets an app open its own notification
     * settings, and on Android 13 that switch is the permission.
     *
     * Rather than asking with a dialog first, because the dialog may no longer
     * be shown - asking would then come back refused without the user seeing
     * anything.
     */
    fun notifications(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /*
     * Android's own one-tap dialog, the same choice as "Unrestricted" under the
     * app's battery settings.
     *
     * Lint discourages it because Google Play only allows it for a few kinds of
     * app. This app is not on Play, and a logger that runs unattended for weeks
     * is the case the exemption exists for.
     */
    @SuppressLint("BatteryLife")
    fun batteryExemption(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context))

    /** Straight to this app's exemption from Data Saver. */
    fun dataSaverExemption(context: Context) =
        Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, packageUri(context))

    /** The manufacturer's page on dontkillmyapp.com, in the browser. */
    fun manufacturerGuide() = Intent(Intent.ACTION_VIEW, ManufacturerGuide.url.toUri())

    private fun packageUri(context: Context): Uri = Uri.fromParts("package", context.packageName, null)
}
