package com.anonymus09.carsensors.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.anonymus09.carsensors.data.AppDatabase
import com.anonymus09.carsensors.data.TelemetrySampleEntity
import com.anonymus09.carsensors.util.AppConfig.BATCH_SIZE
import com.anonymus09.carsensors.util.AppConfig.TELEMETRY_UPLOAD_URL
import com.anonymus09.carsensors.util.AppConfig.UPLOADED_ROW_RETENTION_MS
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_MAX_ATTEMPTS
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_MAX_BATCHES_PER_RUN
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_MIN_BATCH_SIZE
import com.anonymus09.carsensors.util.DeviceIdProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream

class UploadWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "UploadWorker"

        /** No constants for these two in [HttpURLConnection]. */
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_UNPROCESSABLE_ENTITY = 422
    }

    private val dao = AppDatabase.getInstance(context).telemetryDao()

    /** What the server made of one batch, and so what to do with it next. */
    private enum class UploadOutcome {
        /** Stored. Mark the rows and move on to the next batch. */
        STORED,

        /** The server or the network is having a bad time; the batch will do later. */
        TRANSIENT,

        /** Too big to accept. The same rows may fit in a smaller batch. */
        TOO_LARGE,

        /**
         * The request could not be accepted at all - wrong endpoint, unknown or
         * deactivated device. This says nothing about the rows themselves.
         */
        REFUSED,

        /** The server understood the request and rejected this body. */
        MALFORMED
    }

    override suspend fun doWork(): Result {
        var batchSize = BATCH_SIZE
        var batchesUploaded = 0

        while (batchesUploaded < UPLOAD_MAX_BATCHES_PER_RUN) {
            val batch = dao.getPendingBatch(batchSize, UPLOAD_MAX_ATTEMPTS)

            if (batch.isEmpty()) {
                Log.i(TAG, "Backlog cleared after $batchesUploaded batch(es)")
                return finish(batchesUploaded)
            }

            val ids = batch.map { it.id }

            val outcome = try {
                uploadToServer(buildJsonPayload(batch))
            } catch (e: Exception) {
                Log.e(TAG, "Upload attempt failed", e)
                UploadOutcome.TRANSIENT
            }

            when (outcome) {
                UploadOutcome.STORED -> {
                    dao.markUploaded(ids, System.currentTimeMillis())
                    batchesUploaded++
                    Log.i(TAG, "Uploaded ${ids.size} rows")
                }

                UploadOutcome.TOO_LARGE -> {
                    if (batchSize <= UPLOAD_MIN_BATCH_SIZE) {
                        /*
                         * Not the size, then. Counting it lets these rows
                         * eventually stop blocking the ones behind them.
                         */
                        dao.incrementUploadAttempts(ids)
                        Log.e(TAG, "Server rejects even $batchSize rows as too large")
                        return Result.failure()
                    }

                    batchSize /= 2
                    Log.w(TAG, "Batch too large, retrying with $batchSize rows")
                }

                UploadOutcome.TRANSIENT -> {
                    /*
                     * Deliberately not counted against the rows. A server that
                     * is down says nothing about the batch, and counting it
                     * would quarantine a perfectly good backlog for the sole
                     * reason that the server was away for a while.
                     */
                    Log.w(TAG, "Upload deferred, will retry with backoff")
                    return Result.retry()
                }

                UploadOutcome.MALFORMED -> {
                    // The body is the problem, so this is the batch's own fault.
                    dao.incrementUploadAttempts(ids)
                    Log.e(TAG, "Server could not parse the batch, giving up this run")
                    return Result.failure()
                }

                UploadOutcome.REFUSED -> {
                    /*
                     * Not counted: a wrong endpoint or an unregistered device
                     * refuses every batch alike, and quarantining perfectly good
                     * rows over a configuration mistake would lose data that a
                     * corrected setting would have sent.
                     */
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
            val cutoff = System.currentTimeMillis() - UPLOADED_ROW_RETENTION_MS
            dao.deleteUploadedOlderThan(cutoff)
        }

        return Result.success()
    }

    /**
     * Maps a response code onto what the worker should do about it.
     *
     * `ingest` answers 401 for a device it does not know, 403 for one that has
     * been deactivated, 400 for a body it cannot parse and 413 for one that
     * outgrew its limits. It draws that last distinction on purpose - one says
     * "send smaller batches", the other "this will never be accepted" - and
     * this used to collapse all of them into an endless retry.
     */
    private fun classify(code: Int): UploadOutcome = when {
        code in 200..299 -> UploadOutcome.STORED
        code == HttpURLConnection.HTTP_ENTITY_TOO_LARGE -> UploadOutcome.TOO_LARGE
        code == HttpURLConnection.HTTP_CLIENT_TIMEOUT -> UploadOutcome.TRANSIENT
        code == HTTP_TOO_MANY_REQUESTS -> UploadOutcome.TRANSIENT
        code == HttpURLConnection.HTTP_BAD_REQUEST -> UploadOutcome.MALFORMED
        code == HTTP_UNPROCESSABLE_ENTITY -> UploadOutcome.MALFORMED
        code in 400..499 -> UploadOutcome.REFUSED
        else -> UploadOutcome.TRANSIENT
    }

    private fun buildJsonPayload(batch: List<TelemetrySampleEntity>): String {
        val array = JSONArray()

        batch.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("event", item.event)
                put("timestamp", item.timestamp)
                put("payload", item.payload)

                put("charging", item.charging)
                put("powerSource", item.powerSource)

                putFinite("latitude", item.latitude)
                putFinite("longitude", item.longitude)
                putFinite("altitude", item.altitude)
                putFinite("speedMps", item.speedMps)
                putFinite("speedKmh", item.speedKmh)
                putFinite("bearing", item.bearing)
                putFinite("accuracyM", item.accuracyM)
                put("provider", item.provider)

                putFinite("accelX", item.accelX)
                putFinite("accelY", item.accelY)
                putFinite("accelZ", item.accelZ)
                put("accelAccuracy", item.accelAccuracy)
                put("accelAccuracyLabel", item.accelAccuracyLabel)

                putFinite("gyroX", item.gyroX)
                putFinite("gyroY", item.gyroY)
                putFinite("gyroZ", item.gyroZ)
                put("gyroAccuracy", item.gyroAccuracy)
                put("gyroAccuracyLabel", item.gyroAccuracyLabel)

                putFinite("magX", item.magX)
                putFinite("magY", item.magY)
                putFinite("magZ", item.magZ)
                put("magnetAccuracy", item.magnetAccuracy)
                put("magnetAccuracyLabel", item.magnetAccuracyLabel)

                putFinite("pressureHpa", item.pressureHpa)
                put("pressureAccuracy", item.pressureAccuracy)
                put("pressureAccuracyLabel", item.pressureAccuracyLabel)

                putFinite("headingDeg", item.headingDeg)
            }
            array.put(obj)
        }

        return array.toString()
    }

    /*
     * JSONObject refuses a non-finite number, and a sensor that has gone
     * unreliable does hand out NaN. Letting that throw would fail the whole
     * batch - and go on failing it, since the same rows come back next run -
     * over a single bad reading, so it is stored as null instead. Omitting the
     * key is what a null does here, and the server reads a missing field as
     * None already.
     */
    private fun JSONObject.putFinite(name: String, value: Float?) {
        put(name, value?.takeIf { it.isFinite() })
    }

    private fun JSONObject.putFinite(name: String, value: Double?) {
        put(name, value?.takeIf { it.isFinite() })
    }

    private fun uploadToServer(payload: String): UploadOutcome {
        val connection = (URL(TELEMETRY_UPLOAD_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 15000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Content-Encoding", "gzip")
            setRequestProperty("User-Agent", "CarSensors/1.0")
            setRequestProperty(
                "X-Device-ID",
                DeviceIdProvider.getOrCreateDeviceId(applicationContext)
            )
        }

        return try {
            connection.outputStream.use { output ->
                output.write(gzipCompress(payload))
                output.flush()
            }

            val code = connection.responseCode
            Log.i(TAG, "$TELEMETRY_UPLOAD_URL answered $code")
            classify(code)
        } finally {
            connection.disconnect()
        }
    }

    private fun gzipCompress(input: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip ->
            gzip.write(input.toByteArray(Charsets.UTF_8))
        }
        return bos.toByteArray()
    }
}
