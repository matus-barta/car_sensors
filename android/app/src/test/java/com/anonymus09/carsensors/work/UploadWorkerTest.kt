package com.anonymus09.carsensors.work

import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.anonymus09.carsensors.data.AppDatabase
import com.anonymus09.carsensors.data.DevicePairing
import com.anonymus09.carsensors.data.PairingRepository
import com.anonymus09.carsensors.data.SettingsRepository
import com.anonymus09.carsensors.data.TelemetryDao
import com.anonymus09.carsensors.data.TelemetrySampleEntity
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.zip.GZIPInputStream

/**
 * The worker's loop, against a server that answers however the case requires.
 *
 * How a response code is read is decided in `UploadOutcomeTest`; what the
 * worker does about that answer is decided here - which rows it marks, which it
 * counts an attempt against, and whether it comes back. Getting this wrong does
 * not throw: it either loses rows or wedges the queue behind ones that will
 * never be sent, and both were previously established only by having watched a
 * real backlog drain once.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UploadWorkerTest {

    private lateinit var server: MockWebServer
    private lateinit var dao: TelemetryDao

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }

        val context = RuntimeEnvironment.getApplication()

        // The address is a setting, so the worker can be pointed at the fake
        // server exactly the way a user points it at a real one.
        SettingsRepository(context).setServerBaseUrl("http://${server.hostName}:${server.port}")

        // An unpaired phone deliberately never uploads, so every case here
        // needs a credential to present before it can test anything else.
        PairingRepository(context).save(DevicePairing(TEST_DEVICE_ID, TEST_TOKEN))

        /*
         * The database is a process singleton, so it outlives a single test and
         * has to be emptied between them. Room refuses to clear on the main
         * thread, which is where JUnit runs @Before, hence the detour.
         */
        val database = AppDatabase.getInstance(context)
        Thread { database.clearAllTables() }.apply { start(); join() }

        dao = database.telemetryDao()
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `an empty backlog succeeds without troubling the server`() = runTest {
        assertEquals(ListenableWorker.Result.success(), runWorker())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a stored batch is marked and the backlog drains across requests`() = runTest {
        seed(count = 1_200)
        repeat(3) { server.enqueue(ok()) }

        assertEquals(ListenableWorker.Result.success(), runWorker())

        // 1,200 rows at a batch size of 500 is three requests, the last short.
        assertEquals(3, server.requestCount)
        assertEquals(0, dao.getPendingUploadCount(maxAttempts = 5))
    }

    @Test
    fun `too large halves the batch rather than giving up on it`() = runTest {
        seed(count = 600)
        server.enqueue(MockResponse().setResponseCode(413))
        repeat(3) { server.enqueue(ok()) }

        runWorker()

        val first = samplesIn(server.takeRequest())
        val second = samplesIn(server.takeRequest())

        assertEquals("the first attempt uses the full batch", 500, first)
        assertEquals("the refused batch is halved, not abandoned", 250, second)
    }

    @Test
    fun `a server having a bad time is retried and costs the rows nothing`() = runTest {
        seed(count = 10)
        server.enqueue(MockResponse().setResponseCode(503))

        assertEquals(ListenableWorker.Result.retry(), runWorker())

        // A server that is down says nothing about the rows, so counting it
        // would quarantine a perfectly good backlog for having waited.
        assertEquals(0, maxAttempts())
    }

    @Test
    fun `a body the server cannot parse counts against the rows`() = runTest {
        seed(count = 10)
        server.enqueue(MockResponse().setResponseCode(400))

        assertEquals(ListenableWorker.Result.failure(), runWorker())
        assertEquals(1, maxAttempts())
    }

    @Test
    fun `a wrong endpoint or unknown device costs the rows nothing either`() = runTest {
        seed(count = 10)
        server.enqueue(MockResponse().setResponseCode(404))

        assertEquals(ListenableWorker.Result.failure(), runWorker())

        // The case that ran for two months: counting it would have discarded a
        // backlog that a corrected address went on to send perfectly well.
        assertEquals(0, maxAttempts())
    }

    @Test
    fun `rows that exhausted their attempts stop holding up the ones behind them`() = runTest {
        seed(count = 1, attempts = 5, timestamp = 1)
        seed(count = 1, attempts = 0, timestamp = 2)
        server.enqueue(ok())

        runWorker()

        assertEquals("only the eligible row is sent", 1, samplesIn(server.takeRequest()))
    }

    private suspend fun runWorker(): ListenableWorker.Result =
        TestListenableWorkerBuilder<UploadWorker>(RuntimeEnvironment.getApplication())
            .build()
            .doWork()

    private suspend fun seed(count: Int, attempts: Int = 0, timestamp: Long = 0) {
        repeat(count) { i ->
            dao.insert(
                TelemetrySampleEntity(
                    event = "telemetry_sample",
                    timestamp = if (timestamp > 0) timestamp else i.toLong(),
                    payload = null,
                    charging = true,
                    powerSource = "USB",
                    latitude = 48.1, longitude = 17.1, altitude = null,
                    speedMps = null, speedKmh = null, bearing = null,
                    accuracyM = null, provider = "gps",
                    accelX = null, accelY = null, accelZ = null,
                    accelAccuracy = null, accelAccuracyLabel = null,
                    gyroX = null, gyroY = null, gyroZ = null,
                    gyroAccuracy = null, gyroAccuracyLabel = null,
                    magX = null, magY = null, magZ = null,
                    magnetAccuracy = null, magnetAccuracyLabel = null,
                    headingDeg = null, pressureHpa = null,
                    pressureAccuracy = null, pressureAccuracyLabel = null,
                    uploadAttemptCount = attempts
                )
            )
        }
    }

    private suspend fun maxAttempts(): Int = dao.getStats(maxAttempts = 99).maxUploadAttempts

    private fun ok() = MockResponse().setResponseCode(200)

    @Test
    fun `presents the identity and the bearer token`() = runTest {
        seed(count = 1)
        server.enqueue(ok())

        runWorker()

        val request = server.takeRequest()

        /*
         * The exact spelling matters more than it looks: `ingest` reads these
         * two headers and nothing else, and they are named the way RFC 6750
         * and RFC 6648 ask rather than the way this app used to name them.
         */
        assertEquals(TEST_DEVICE_ID, request.getHeader("Device-Id"))
        assertEquals("Bearer $TEST_TOKEN", request.getHeader("Authorization"))
        assertEquals(null, request.getHeader("X-Device-ID"))
    }

    @Test
    fun `a retired vehicle stops the run without blaming the rows`() = runTest {
        seed(count = 3)

        server.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setHeader(
                    "WWW-Authenticate",
                    """Bearer realm="telemetry", error="insufficient_scope""""
                )
        )

        val result = runWorker()

        /*
         * The rows are untouched. Counting an attempt against them would
         * quarantine perfectly good data for something the server decided about
         * the vehicle, and the screen is what offers to do anything about it.
         */
        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(0, maxAttempts())
        assertEquals(3, dao.getPendingUploadCount(99))
    }

    @Test
    fun `a refused credential stops the run without blaming the rows`() = runTest {
        seed(count = 2)

        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setHeader(
                    "WWW-Authenticate",
                    """Bearer realm="telemetry", error="invalid_token""""
                )
        )

        val result = runWorker()

        assertEquals(ListenableWorker.Result.failure(), result)
        assertEquals(0, maxAttempts())
        assertEquals(2, dao.getPendingUploadCount(99))
    }

    @Test
    fun `sends nothing at all while the phone is unpaired`() = runTest {
        PairingRepository(RuntimeEnvironment.getApplication()).clear()

        seed(count = 3)

        val result = runWorker()

        /*
         * Not a retry: no amount of waiting produces a credential, and pairing
         * enqueues this work itself. The rows stay untouched, because a
         * rejection the app caused must not count against them.
         */
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, server.requestCount)
        assertEquals(0, maxAttempts())
    }

    /** The body is gzipped, which is also worth knowing still happens. */
    private fun samplesIn(request: RecordedRequest): Int {
        assertTrue("body should be gzipped", request.getHeader("Content-Encoding") == "gzip")

        val json = GZIPInputStream(request.body.inputStream()).bufferedReader().readText()
        return Regex("\"event\":").findAll(json).count()
    }

    private companion object {
        const val TEST_DEVICE_ID = "61d96b75-a9fe-498d-9b7d-056ec07d5630"
        const val TEST_TOKEN = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
    }
}
