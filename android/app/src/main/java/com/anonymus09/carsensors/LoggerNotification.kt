package com.anonymus09.carsensors

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.location.Location
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import com.anonymus09.carsensors.data.PowerTier
import com.anonymus09.carsensors.util.AppConfig.MPS_TO_KMH
import java.util.Locale
import kotlin.math.roundToInt

/**
 * The logger's ongoing notification: what it is doing, where it is, and on
 * what power.
 *
 * Every foreground service needs one, and on a phone in a car it is the only
 * part of the app anybody is likely to glance at. Its channel is
 * [NotificationManager.IMPORTANCE_LOW] so that something updated this often
 * never makes a sound; the upload warning has a louder channel of its own, see
 * [UploadSilenceNotifier].
 */
class LoggerNotification(context: Context) {

    /** Everything the notification says, gathered by the service. */
    data class Content(
        val loggerState: LoggerState,
        val powerTier: PowerTier,
        /** Null when there is no fix current enough to call a position. */
        val location: Location?,
        val charging: Boolean,
        val powerSource: String,
        val headingDegrees: Float?
    )

    private val appContext = context.applicationContext

    private val manager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** What `startForeground` is given, before the logger knows anything. */
    fun starting(): Notification {
        createChannel()

        return build(listOf("Starting telemetry logger..."))
    }

    fun show(content: Content) {
        manager.notify(ID, build(describe(content)))
    }

    /** The notification's lines, top to bottom. Kept apart from posting it, to be testable. */
    @VisibleForTesting
    internal fun describe(content: Content): List<String> {
        val base = when (content.loggerState) {
            LoggerState.RECORDING -> "Logging active"
            LoggerState.ARMED -> "Waiting for movement"
            LoggerState.OFF -> "Stopped"
        }

        // Being cut back looks identical to being broken unless it is said.
        val status = if (content.powerTier == PowerTier.FULL) {
            base
        } else {
            "$base (battery saving: ${content.powerTier.name.lowercase().replace('_', ' ')})"
        }

        val gps = content.location?.let {
            val speedKmh = (it.speed * MPS_TO_KMH).roundToInt()

            "GPS: ${"%.5f".format(Locale.US, it.latitude)}, " +
                "${"%.5f".format(Locale.US, it.longitude)} | $speedKmh km/h"
        } ?: "GPS: waiting"

        val power = if (content.charging) "Power: ${content.powerSource}" else "Power: unplugged"

        val heading = content.headingDegrees?.let { "Heading: ${it.roundToInt()}°" }
            ?: "Heading: n/a"

        return listOf(status, gps, power, heading)
    }

    /** The first two lines when collapsed, all of them when expanded. */
    private fun build(lines: List<String>): Notification =
        NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle("Car telemetry logger")
            .setContentText(lines.take(2).joinToString(" "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Telemetry Logger",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Logs location and sensor data while driving"
        }

        manager.createNotificationChannel(channel)
    }

    companion object {
        /** Distinct from the upload warning's, so the two come and go independently. */
        const val ID = 1001

        private const val CHANNEL_ID = "telemetry_logger_channel"
    }
}
