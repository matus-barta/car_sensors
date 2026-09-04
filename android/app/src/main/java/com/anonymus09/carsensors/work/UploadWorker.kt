package com.anonymus09.carsensors.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.anonymus09.carsensors.data.AppDatabase
import com.anonymus09.carsensors.data.TelemetryUploader
import com.anonymus09.carsensors.util.AppConfig.BATCH_SIZE
import com.anonymus09.carsensors.util.AppConfig.UPLOADED_ROW_RETENTION_MS
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_MAX_ATTEMPTS
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_MAX_BATCHES_PER_RUN
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_MIN_BATCH_SIZE

/**
 * Drains the backlog oldest first, under WorkManager's constraints.
 *
 * The live push in the foreground service sends the newest position instead,
 * and the two share [TelemetryUploader] for everything between building a body
 * and reading the response.
 */
class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "UploadWorker"
    }

    private val dao = AppDatabase.getInstance(context).telemetryDao()
    private val uploader = TelemetryUploader.create(context)

    override suspend fun doWork(): Result = uploader.exclusively { drain() }

    private suspend fun drain(): Result {
        var batchSize = BATCH_SIZE
        var batchesUploaded = 0

        while (batchesUploaded < UPLOAD_MAX_BATCHES_PER_RUN) {
            val batch = dao.getPendingBatch(batchSize, UPLOAD_MAX_ATTEMPTS)

            if (batch.isEmpty()) {
                Log.i(TAG, "Backlog cleared after $batchesUploaded batch(es)")
                return finish(batchesUploaded)
            }

            when (uploader.send(batch)) {
                TelemetryUploader.Outcome.STORED -> {
                    batchesUploaded++
                    Log.i(TAG, "Uploaded ${batch.size} rows")
                }

                TelemetryUploader.Outcome.TOO_LARGE -> {
                    if (batchSize <= UPLOAD_MIN_BATCH_SIZE) {
                        /*
                         * Not the size, then. Counting it lets these rows
                         * eventually stop blocking the ones behind them.
                         */
                        dao.incrementUploadAttempts(batch.map { it.id })
                        Log.e(TAG, "Server rejects even $batchSize rows as too large")
                        return Result.failure()
                    }

                    batchSize /= 2
                    Log.w(TAG, "Batch too large, retrying with $batchSize rows")
                }

                TelemetryUploader.Outcome.TRANSIENT -> {
                    Log.w(TAG, "Upload deferred, will retry with backoff")
                    return Result.retry()
                }

                TelemetryUploader.Outcome.MALFORMED -> {
                    Log.e(TAG, "Server could not parse the batch, giving up this run")
                    return Result.failure()
                }

                TelemetryUploader.Outcome.REFUSED -> {
                    Log.e(
                        TAG,
                        "Server refused the request - check the endpoint and that " +
                            "this device is registered"
                    )
                    return Result.failure()
                }
            }
        }

        /*
         * The backlog outlasted this run. Stopping here keeps one pass inside
         * WorkManager's execution window; whatever remains is picked up by the
         * next enqueue, which the service raises once the backlog rebuilds.
         */
        Log.i(TAG, "Stopped after $batchesUploaded batches, more rows still pending")
        return finish(batchesUploaded)
    }

    private suspend fun finish(batchesUploaded: Int): Result {
        if (batchesUploaded > 0) {
            dao.deleteUploadedOlderThan(System.currentTimeMillis() - UPLOADED_ROW_RETENTION_MS)
        }

        return Result.success()
    }
}
