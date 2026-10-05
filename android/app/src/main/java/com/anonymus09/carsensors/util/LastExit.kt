package com.anonymus09.carsensors.util

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import org.json.JSONObject

/**
 * How the app's previous process ended, as Android recorded it.
 *
 * Mostly for the server: a logger that went quiet for a week looks the same
 * whether it crashed, was killed for memory, or was stopped by somebody, and
 * only the phone knows which. One case is also said on the screen - stopping
 * the app from Android 13's Active apps list, or by force-stopping it, ends
 * the logger for good and nothing restarts it, so the app should say why it
 * is not running when it is next opened.
 *
 * Android keeps this history from Android 11; before that there is none.
 */
data class LastExit(val reason: String, val description: String?, val atMs: Long) {
    /** Stopped on purpose from the system: Active apps, or Force stop. */
    val stoppedByUser: Boolean get() = reason == "USER_REQUESTED"

    fun putInto(payload: JSONObject): JSONObject = payload.apply {
        put("previousExitReason", reason)
        put("previousExitDescription", description)
        put("previousExitAt", atMs)
    }

    companion object {
        fun of(context: Context): LastExit? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

            val info = context.getSystemService(ActivityManager::class.java)
                .getHistoricalProcessExitReasons(context.packageName, 0, 1)
                .firstOrNull() ?: return null

            return LastExit(
                reason = reasonName(info.reason),
                description = info.description?.take(MAX_DESCRIPTION_LENGTH),
                atMs = info.timestamp
            )
        }

        // Anything newer than these, or unknown, keeps its number, which still says it.
        @RequiresApi(Build.VERSION_CODES.R)
        private fun reasonName(reason: Int): String = mapOf(
            ApplicationExitInfo.REASON_ANR to "ANR",
            ApplicationExitInfo.REASON_CRASH to "CRASH",
            ApplicationExitInfo.REASON_CRASH_NATIVE to "CRASH_NATIVE",
            ApplicationExitInfo.REASON_DEPENDENCY_DIED to "DEPENDENCY_DIED",
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE to "EXCESSIVE_RESOURCE_USAGE",
            ApplicationExitInfo.REASON_EXIT_SELF to "EXIT_SELF",
            ApplicationExitInfo.REASON_INITIALIZATION_FAILURE to "INITIALIZATION_FAILURE",
            ApplicationExitInfo.REASON_LOW_MEMORY to "LOW_MEMORY",
            ApplicationExitInfo.REASON_OTHER to "OTHER",
            ApplicationExitInfo.REASON_PERMISSION_CHANGE to "PERMISSION_CHANGE",
            ApplicationExitInfo.REASON_SIGNALED to "SIGNALED",
            ApplicationExitInfo.REASON_USER_REQUESTED to "USER_REQUESTED",
            ApplicationExitInfo.REASON_USER_STOPPED to "USER_STOPPED"
        )[reason] ?: "REASON_$reason"

        /** Enough to tell "remove task" from "permissions revoked". */
        private const val MAX_DESCRIPTION_LENGTH = 200
    }
}
