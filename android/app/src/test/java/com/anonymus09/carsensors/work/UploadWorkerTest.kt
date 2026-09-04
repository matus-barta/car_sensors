package com.anonymus09.carsensors.work

import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.anonymus09.carsensors.data.AppDatabase
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

    /** The body is gzipped, which is also worth knowing still happens. */
    private fun samplesIn(request: RecordedRequest): Int {
        assertTrue("body should be gzipped", request.getHeader("Content-Encoding") == "gzip")

        val json = GZIPInputStream(request.body.inputStream()).bufferedReader().readText()
        return Regex("\"event\":").findAll(json).count()
    }
}
