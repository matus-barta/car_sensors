package com.anonymus09.carsensors.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.anonymus09.carsensors.data.AppDatabase
import com.anonymus09.carsensors.data.TelemetrySampleEntity
import com.anonymus09.carsensors.util.AppConfig.BATCH_SIZE
import com.anonymus09.carsensors.util.AppConfig.TELEMETRY_UPLOAD_URL
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
    }

    private val dao = AppDatabase.getInstance(context).telemetryDao()

    override suspend fun doWork(): Result {
        val batch = dao.getPendingBatch(BATCH_SIZE)

        if (batch.isEmpty()) {
            Log.i(TAG, "No pending rows to upload")
            return Result.success()
        }

        return try {
            val ids = batch.map { it.id }
            dao.incrementUploadAttempts(ids)

            val payload = buildJsonPayload(batch)
            val uploadOk = uploadToServer(payload)

            if (uploadOk) {
                dao.markUploaded(ids, System.currentTimeMillis())

                // optional cleanup: keep uploaded rows only for 7 days
                val sevenDaysAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                dao.deleteUploadedOlderThan(sevenDaysAgo)

                Log.i(TAG, "Uploaded ${ids.size} rows successfully")
                Result.success()
            } else {
                Log.w(TAG, "Upload failed, retry requested")
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Upload worker exception", e)
            Result.retry()
        }
    }

    private fun buildJsonPayload(batch: List<TelemetrySampleEntity>): String {
        Log.i(TAG, "Building Payload")
        val array = JSONArray()

        batch.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("event", item.event)
                put("timestamp", item.timestamp)
                put("payload", item.payload)

                put("charging", item.charging)
                put("powerSource", item.powerSource)

                put("latitude", item.latitude)
                put("longitude", item.longitude)
                put("altitude", item.altitude)
                put("speedMps", item.speedMps)
                put("speedKmh", item.speedKmh)
                put("bearing", item.bearing)
                put("accuracyM", item.accuracyM)
                put("provider", item.provider)

                put("accelX", item.accelX)
                put("accelY", item.accelY)
                put("accelZ", item.accelZ)
                put("accelAccuracy", item.accelAccuracy)
                put("accelAccuracyLabel", item.accelAccuracyLabel)

                put("gyroX", item.gyroX)
                put("gyroY", item.gyroY)
                put("gyroZ", item.gyroZ)
                put("gyroAccuracy", item.gyroAccuracy)
                put("gyroAccuracyLabel", item.gyroAccuracyLabel)

                put("magX", item.magX)
                put("magY", item.magY)
                put("magZ", item.magZ)
                put("magnetAccuracy", item.magnetAccuracy)
                put("magnetAccuracyLabel", item.magnetAccuracyLabel)

                put("pressureHpa", item.pressureHpa)
                put("pressureAccuracy", item.pressureAccuracy)
                put("pressureAccuracyLabel", item.pressureAccuracyLabel)

                put("headingDeg", item.headingDeg)
            }
            array.put(obj)
        }

        return array.toString()
    }

    private fun uploadToServer(payload: String): Boolean {
        Log.i(TAG, "Uploading to: $TELEMETRY_UPLOAD_URL")
        val url = URL(TELEMETRY_UPLOAD_URL)

        val connection = (url.openConnection() as HttpURLConnection).apply {
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
            code in 200..299
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

    private fun plainPayload(input: String): ByteArray {
        return input.toByteArray(Charsets.UTF_8)
    }


}
