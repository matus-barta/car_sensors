package com.anonymus09.carsensors.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.anonymus09.carsensors.data.SettingsRepository
import java.util.concurrent.TimeUnit

object WifiUploadScheduler {

    private const val UNIQUE_WORK_NAME = "telemetry_wifi_upload"

    fun enqueue(context: Context) {
        val settings = SettingsRepository(context).current()

        /*
         * The network requirement used to be UNMETERED unconditionally, with no
         * way to allow mobile data for a device that never sees Wi-Fi.
         */
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .apply {
                if (!settings.uploadOnBattery) setRequiresCharging(true)
            }
            .build()

        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1,
                TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
    }

    /**
     * Uploads now, ignoring the network and charging constraints.
     *
     * Shares [UNIQUE_WORK_NAME] with [enqueue] deliberately. Enqueued outside
     * it, as this was, a forced upload ran alongside the scheduled one: both
     * drew the same pending rows, both counted an attempt against them and both
     * sent them. Replacing is safe because the server discards a batch it has
     * already stored.
     */
    fun enqueueNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                1,
                TimeUnit.MINUTES
            )
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
    }

}
