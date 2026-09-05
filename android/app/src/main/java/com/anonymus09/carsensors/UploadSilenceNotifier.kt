package com.anonymus09.carsensors

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/**
 * Tells the user, on the notification shade, that telemetry has stopped
 * reaching the server.
 *
 * Its own channel rather than the logger's. That one is [IMPORTANCE_LOW] so
 * that an ongoing notification nobody needs to read stays quiet, and a warning
 * posted at that importance would be exactly as easy to miss as the panel it
 * exists to replace.
 *
 * Kept apart from the service so that noticing the silence and announcing it
 * are separable, and so the service does not grow another notification to
 * maintain.
 */
class UploadSilenceNotifier(context: Context) {

    private val appContext = context.applicationContext

    private val manager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun warn(message: String) {
        createChannel()

        val openApp = PendingIntent.getActivity(
            appContext,
            0,
            Intent(appContext, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setContentTitle(TITLE)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            // Renewals refresh the text without buzzing the phone again.
            .setOnlyAlertOnce(true)
            .build()

        manager.notify(NOTIFICATION_ID, notification)
    }

    /** Takes the warning down once telemetry is moving again. */
    fun clear() = manager.cancel(NOTIFICATION_ID)

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Upload problems",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Warns when recorded telemetry stops reaching the server"
        }

        manager.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "telemetry_upload_warning_channel"

        /** Distinct from the logger's, so this can come and go independently. */
        const val NOTIFICATION_ID = 1002

        const val TITLE = "Telemetry is not reaching the server"
    }
}
