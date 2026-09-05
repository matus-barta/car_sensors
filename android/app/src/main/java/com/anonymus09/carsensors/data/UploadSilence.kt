package com.anonymus09.carsensors.data

import java.util.concurrent.TimeUnit

/**
 * What to tell the user about telemetry that is not going anywhere.
 *
 * The remedy differs by cause, so the cause is what the message leads with. An
 * address nothing answers at is the user's to correct; a device the server does
 * not know has to be registered in the web application; and a server that is
 * perfectly well means the upload is waiting on a condition the user chose,
 * which is worth saying rather than leaving them to hunt for a fault that is
 * not there.
 */
fun uploadSilenceMessage(
    health: ServerHealth,
    settings: TelemetrySettings,
    pendingRows: Int,
    waitingMs: Long
): String {
    val samples = if (pendingRows == 1) "sample" else "samples"

    return "$pendingRows $samples waiting for ${formatWaiting(waitingMs)}. " +
        reasonFor(health, settings)
}

private fun reasonFor(health: ServerHealth, settings: TelemetrySettings): String = when (health) {
    is ServerHealth.Unreachable ->
        "Nothing answered at ${settings.serverBaseUrl}. Check the address and that the " +
            "server is running."

    is ServerHealth.NotTheApi ->
        "${settings.serverBaseUrl} answered, but not as the telemetry API. Check the address."

    is ServerHealth.DeviceUnknown ->
        "The server does not recognise this device. Register it in the web application."

    is ServerHealth.DeviceDeactivated ->
        "This device has been deactivated on the server."

    is ServerHealth.ServerFault ->
        "The server answered with an error (${health.code})."

    is ServerHealth.Ok -> waitingOnAChoice(settings)

    ServerHealth.Unknown, ServerHealth.Checking ->
        "The server could not be checked."
}

/**
 * The server is there and will take this device, so what is left is a condition
 * the upload is waiting on. Naming the setting is the difference between a
 * warning that can be acted on and one that only worries.
 */
private fun waitingOnAChoice(settings: TelemetrySettings): String = when {
    settings.wifiOnly && !settings.uploadOnBattery ->
        "The server is reachable: uploads are waiting for Wi-Fi and a charger."

    settings.wifiOnly ->
        "The server is reachable: uploads are waiting for Wi-Fi."

    !settings.uploadOnBattery ->
        "The server is reachable: uploads are waiting for a charger."

    else ->
        "The server is reachable, but uploads are not getting through."
}

private fun formatWaiting(waitingMs: Long): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(waitingMs)
    val hours = TimeUnit.MILLISECONDS.toHours(waitingMs)
    val days = TimeUnit.MILLISECONDS.toDays(waitingMs)

    return when {
        days >= 1 -> "$days ${if (days == 1L) "day" else "days"}"
        hours >= 1 -> "$hours ${if (hours == 1L) "hour" else "hours"}"
        else -> "$minutes ${if (minutes == 1L) "minute" else "minutes"}"
    }
}
