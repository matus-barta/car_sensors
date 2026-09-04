package com.anonymus09.carsensors.data

import android.util.Log
import com.anonymus09.carsensors.util.AppConfig.TELEMETRY_UPLOAD_PATH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream

/**
 * What a check of the configured address found.
 *
 * Two questions, because they have different answers and different remedies:
 * whether the server is there at all, and whether it will accept this device.
 */
sealed interface ServerHealth {
    data object Unknown : ServerHealth
    data object Checking : ServerHealth

    /** Reachable, and this device may upload to it. */
    data object Ok : ServerHealth

    /** Nothing answered - wrong host or port, or no network. */
    data object Unreachable : ServerHealth

    /** Something answered but it is not this API - usually the wrong address. */
    data object NotTheApi : ServerHealth

    /** The API is there and does not know this device. */
    data object DeviceUnknown : ServerHealth

    /** The API knows this device and has deactivated it. */
    data object DeviceDeactivated : ServerHealth

    data class ServerFault(val code: Int) : ServerHealth
}

/**
 * Asks the configured server whether it is there and whether it wants us.
 *
 * The endpoint being wrong is not hypothetical: uploads went to a path that did
 * not exist for two months, and the only symptom was a backlog that quietly
 * grew. `/api/health` needs no credentials and answers the first question; an
 * empty batch posted to the upload endpoint answers the second, storing nothing
 * whatever the reply.
 */
class ServerHealthChecker(
    private val settings: SettingsRepository,
    private val loadDeviceId: () -> String
) {

    suspend fun check(): ServerHealth = withContext(Dispatchers.IO) {
        val baseUrl = settings.current().serverBaseUrl

        val reachable = try {
            statusOf("$baseUrl$HEALTH_PATH") { it.requestMethod = "GET" }
        } catch (e: Exception) {
            Log.w(TAG, "Server not reachable at $baseUrl", e)
            return@withContext ServerHealth.Unreachable
        }

        if (reachable == HttpURLConnection.HTTP_NOT_FOUND) return@withContext ServerHealth.NotTheApi
        if (reachable !in 200..299) return@withContext ServerHealth.ServerFault(reachable)

        val accepted = try {
            statusOf("$baseUrl$TELEMETRY_UPLOAD_PATH") { connection ->
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                connection.setRequestProperty("Content-Encoding", "gzip")
                connection.setRequestProperty("X-Device-ID", loadDeviceId())
                connection.outputStream.use { it.write(gzip(EMPTY_BATCH)) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Upload endpoint not reachable at $baseUrl", e)
            return@withContext ServerHealth.Unreachable
        }

        when (accepted) {
            in 200..299 -> ServerHealth.Ok
            HttpURLConnection.HTTP_UNAUTHORIZED -> ServerHealth.DeviceUnknown
            HttpURLConnection.HTTP_FORBIDDEN -> ServerHealth.DeviceDeactivated
            HttpURLConnection.HTTP_NOT_FOUND -> ServerHealth.NotTheApi
            else -> ServerHealth.ServerFault(accepted)
        }
    }

    private fun statusOf(url: String, prepare: (HttpURLConnection) -> Unit): Int {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", "CarSensors/1.0")
        }

        return try {
            prepare(connection)
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    private fun gzip(input: String): ByteArray {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(input.toByteArray(Charsets.UTF_8)) }
        return bos.toByteArray()
    }

    private companion object {
        const val TAG = "ServerHealth"
        const val HEALTH_PATH = "/api/health"
        const val TIMEOUT_MS = 8_000

        /** Accepted and stored as nothing, so this can be asked at any time. */
        const val EMPTY_BATCH = "[]"
    }
}
