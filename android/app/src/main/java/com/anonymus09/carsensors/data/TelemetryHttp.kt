package com.anonymus09.carsensors.data

import com.anonymus09.carsensors.util.AppConfig.USER_AGENT
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPOutputStream

/**
 * How this app speaks to `ingest`'s upload endpoint, in one place.
 *
 * Both the uploader and the server health check post to it, and what `ingest`
 * reads - the headers, the compression, the credential - has to be identical in
 * both or the check would pass for a request the uploader never makes.
 */
internal object TelemetryHttp {

    /** A connection with the timeouts given, identifying the app and its version. */
    fun open(url: String, connectTimeoutMs: Int, readTimeoutMs: Int): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("User-Agent", USER_AGENT)
        }

    /**
     * Makes [connection] a gzipped JSON upload from the paired phone.
     *
     * The identity names the row; the bearer token proves the request came from
     * this phone. Spelled the way RFC 6750 and RFC 6648 ask for, which is also
     * what `ingest` reads.
     */
    fun prepareUpload(connection: HttpURLConnection, pairing: DevicePairing) {
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
        connection.setRequestProperty("Content-Encoding", "gzip")
        connection.setRequestProperty("Device-Id", pairing.deviceId)
        connection.setRequestProperty("Authorization", "Bearer ${pairing.token}")
    }

    /** The body as `Content-Encoding: gzip` promises it. */
    fun gzip(text: String): ByteArray {
        val bytes = ByteArrayOutputStream()

        GZIPOutputStream(bytes).use { it.write(text.toByteArray(Charsets.UTF_8)) }

        return bytes.toByteArray()
    }
}
