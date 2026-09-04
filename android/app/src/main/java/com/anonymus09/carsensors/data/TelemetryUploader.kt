package com.anonymus09.carsensors.data

import android.util.Log
import com.anonymus09.carsensors.util.DeviceIdProvider
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream

/**
 * Sends rows to the server and records what became of them.
 *
 * Shared by the two things that upload: [com.anonymus09.carsensors.work.UploadWorker],
 * which drains the backlog under WorkManager's constraints, and the foreground
 * service, which pushes the newest position the moment it has one. They differ
 * only in which rows they choose and what they do about a failure - everything
 * from building the body to interpreting the response is here.
 */
class TelemetryUploader(
    private val dao: TelemetryDao,
    private val settings: SettingsRepository,
    private val loadDeviceId: () -> String
) {

    /** What the server made of a batch, and so what the caller should do next. */
    enum class Outcome {
        /** Stored. The rows are marked uploaded before this returns. */
        STORED,

        /** The server or the network is having a bad time; the rows will do later. */
        TRANSIENT,

        /** Too big to accept. The same rows may fit in a smaller batch. */
        TOO_LARGE,

        /**
         * The request could not be accepted at all - wrong endpoint, unknown or
         * deactivated device. This says nothing about the rows themselves, so
         * nothing is counted against them.
         */
        REFUSED,

        /** The server understood the request and rejected this body. */
        MALFORMED
    }

    /**
     * Sends [rows], marking them uploaded if the server took them.
     *
     * Bookkeeping that follows from the response alone happens here, so both
     * callers agree on it: a stored batch is marked, a malformed one counts
     * against its rows. Anything else is policy for the caller, which knows
     * whether it can afford to retry.
     */
    suspend fun send(rows: List<TelemetrySampleEntity>): Outcome {
        if (rows.isEmpty()) return Outcome.STORED

        val ids = rows.map { it.id }

        val outcome = try {
            post(buildJsonPayload(rows))
        } catch (e: Exception) {
            Log.e(TAG, "Upload attempt failed", e)
            Outcome.TRANSIENT
        }

        when (outcome) {
            Outcome.STORED -> dao.markUploaded(ids, System.currentTimeMillis())
            Outcome.MALFORMED -> dao.incrementUploadAttempts(ids)
            else -> Unit
        }

        return outcome
    }

    /**
     * Runs [block] only if no other upload is in flight, and reports whether it
     * did.
     *
     * The live push uses this rather than waiting its turn: a backlog drain can
     * hold the connection for many seconds, and a position that arrives behind
     * it is no longer live by the time it would be sent. Skipping costs
     * nothing, because the row is already in the database and the batch path
     * will carry it.
     */
    suspend fun ifIdle(block: suspend () -> Unit): Boolean {
        if (!uploadLock.tryLock()) return false

        return try {
            block()
            true
        } finally {
            uploadLock.unlock()
        }
    }

    /** Runs [block] once any in-flight upload has finished. */
    suspend fun <T> exclusively(block: suspend () -> T): T = uploadLock.withLock { block() }

    private fun post(payload: String): Outcome {
        val uploadUrl = settings.current().uploadUrl

        val connection = (URL(uploadUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Content-Encoding", "gzip")
            setRequestProperty("User-Agent", "CarSensors/1.0")
            setRequestProperty("X-Device-ID", loadDeviceId())
        }

        return try {
            connection.outputStream.use { output ->
                output.write(gzipCompress(payload))
                output.flush()
            }

            val code = connection.responseCode
            Log.i(TAG, "$uploadUrl answered $code")
            classify(code)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Maps a response code onto what should be done about it.
     *
     * `ingest` answers 401 for a device it does not know, 403 for one that has
     * been deactivated, 400 for a body it cannot parse and 413 for one that
     * outgrew its limits. It draws those distinctions on purpose - "send
     * smaller batches" and "this will never be accepted" call for different
     * things - and a wrong endpoint answers 404 for every batch alike, which is
     * the reason that is separated from the rows being at fault.
     */
    private fun classify(code: Int): Outcome = when {
        code in 200..299 -> Outcome.STORED
        code == HttpURLConnection.HTTP_ENTITY_TOO_LARGE -> Outcome.TOO_LARGE
        code == HttpURLConnection.HTTP_CLIENT_TIMEOUT -> Outcome.TRANSIENT
        code == HTTP_TOO_MANY_REQUESTS -> Outcome.TRANSIENT
        code == HttpURLConnection.HTTP_BAD_REQUEST -> Outcome.MALFORMED
        code == HTTP_UNPROCESSABLE_ENTITY -> Outcome.MALFORMED
        code in 400..499 -> Outcome.REFUSED
        else -> Outcome.TRANSIENT
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
     * over a single bad reading, so it is sent as null instead. Omitting the
     * key is what a null does here, and the server reads a missing field as
     * None already.
     */
    private fun JSONObject.putFinite(name: String, value: Float?) {
        put(name, value?.takeIf { it.isFinite() })
    }

    private fun JSONObject.putFinite(name: String, value: Double?) {
        put(name, value?.takeIf { it.isFinite() })
    }

    private fun gzipCompress(input: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gzip -> gzip.write(input.toByteArray(Charsets.UTF_8)) }
        return bos.toByteArray()
    }

    companion object {
        private const val TAG = "TelemetryUploader"

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000

        /** No constants for these two in [HttpURLConnection]. */
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_UNPROCESSABLE_ENTITY = 422

        /*
         * One upload at a time across the process. The batch worker and the
         * live push draw from the same rows, and without this they would send
         * and mark the same ones concurrently.
         */
        private val uploadLock = Mutex()

        fun create(context: android.content.Context): TelemetryUploader {
            val appContext = context.applicationContext

            return TelemetryUploader(
                dao = AppDatabase.getInstance(appContext).telemetryDao(),
                settings = SettingsRepository(appContext),
                loadDeviceId = { DeviceIdProvider.getOrCreateDeviceId(appContext) }
            )
        }
    }
}
