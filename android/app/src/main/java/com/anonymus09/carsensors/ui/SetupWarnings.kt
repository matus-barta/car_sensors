package com.anonymus09.carsensors.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.anonymus09.carsensors.util.AccessState
import com.anonymus09.carsensors.util.LastExit
import com.anonymus09.carsensors.util.LocationAccess
import com.anonymus09.carsensors.util.ManufacturerGuide

/**
 * What the phone is not letting the logger do, each with the way to fix it.
 *
 * Nothing is shown while all is well. Each warning says what goes wrong in
 * practice rather than which setting is off, because "battery optimization"
 * means nothing to somebody who only wants the car's trips recorded.
 */
@Composable
fun SetupWarnings(
    access: AccessState,
    locationRefused: Boolean,
    stoppedByUser: LastExit?,
    uploadsMayUseMobileData: Boolean,
    onFix: (SetupFix) -> Unit
) {
    stoppedByUser?.let { StoppedByUserWarning(it) }

    if (access.location == LocationAccess.NONE && locationRefused) {
        Warning(
            "Location is not allowed, so logging cannot start. In the app's settings, " +
                "open Permissions, then Location.",
            "Open app settings" to SetupFix.APP_SETTINGS,
            onFix = onFix
        )
    }

    if (access.backgroundRestricted) {
        Warning(
            "Android restricts this app's battery use, so it may not run in the " +
                "background: logging stops and cannot start again after a reboot. In the " +
                "app's settings, open Battery and choose Unrestricted.",
            "Open app settings" to SetupFix.APP_SETTINGS,
            manufacturerGuide(),
            onFix = onFix
        )
    }

    if (access.location == LocationAccess.WHILE_IN_USE) {
        Warning(
            "Location is allowed only while this app is open. After a reboot the logger " +
                "records without GPS.",
            "Allow location all the time" to SetupFix.BACKGROUND_LOCATION,
            onFix = onFix
        )
    }

    if (access.location != LocationAccess.NONE && !access.preciseLocation) {
        Warning(
            "Location is approximate, so recorded positions can be off by a kilometre " +
                "or more.",
            "Allow precise location" to SetupFix.PRECISE_LOCATION,
            onFix = onFix
        )
    }

    // A restriction says all this and more, so it is not said twice.
    if (!access.batteryUnrestricted && !access.backgroundRestricted) {
        Warning(
            "Battery optimization can pause logging and uploads while the phone is idle, " +
                "and lets Android restrict the app if it judges it uses too much.",
            "Allow unrestricted battery use" to SetupFix.BATTERY_OPTIMIZATION,
            manufacturerGuide(),
            onFix = onFix
        )
    }

    if (!access.notificationsEnabled) {
        Warning(
            "Notifications are off, so the app cannot warn you when uploads stop " +
                "reaching the server.",
            "Turn on notifications" to SetupFix.NOTIFICATIONS,
            onFix = onFix
        )
    }

    if (access.dataSaverRestricted && uploadsMayUseMobileData) {
        Warning(
            "Data Saver is on, so uploads wait for Wi-Fi even though mobile data is " +
                "allowed for them.",
            "Allow background data" to SetupFix.DATA_SAVER,
            onFix = onFix
        )
    }
}

/**
 * Said because the logger cannot say it: stopped this way, the app is killed
 * and Android does not restart it, so it only comes back with a reboot or a
 * press of the button above.
 */
@Composable
private fun StoppedByUserWarning(exit: LastExit) {
    WarningText(
        "Logging was stopped from Android on ${formatTimestamp(exit.atMs, "an unknown date")} " +
            "- from Active apps in the notification panel, or with Force stop. It stays " +
            "stopped until the phone restarts or it is started again here."
    )
}

/** The manufacturer guide as an action, for the warnings that need it. */
fun manufacturerGuide(): Pair<String, SetupFix> =
    ManufacturerGuide.label to SetupFix.MANUFACTURER_GUIDE

@Composable
private fun Warning(
    text: String,
    vararg actions: Pair<String, SetupFix>,
    onFix: (SetupFix) -> Unit
) {
    WarningText(text)

    actions.forEach { (label, fix) ->
        OutlinedButton(onClick = { onFix(fix) }, modifier = Modifier.fillMaxWidth()) {
            Text(label)
        }
    }
}

@Composable
private fun WarningText(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.error
)
