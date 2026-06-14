package com.anonymus09.carsensors.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.anonymus09.carsensors.TelemetryForegroundService
import java.util.concurrent.TimeUnit

object WifiUploadScheduler {

    private const val UNIQUE_WORK_NAME = "telemetry_wifi_upload"

    fun enqueue(context: Context) {
        val requiresCharging =
            TelemetryForegroundService.isUploadOnlyWhenChargingEnabled(context)

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .apply {
                if (requiresCharging) setRequiresCharging(true)
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

    fun enqueueNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<UploadWorker>()
            .build()

        WorkManager.getInstance(context).enqueue(request)
    }

}
