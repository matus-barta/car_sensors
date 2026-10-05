package com.anonymus09.carsensors.data

import com.anonymus09.carsensors.util.AppConfig.TELEMETRY_UPLOAD_PATH
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.zip.GZIPInputStream

/**
 * What "Test connection" reports, against a server that answers however the
 * case requires.
 *
 * Every answer here is a different remedy on the screen - a wrong address, a
 * server having a bad time, a phone to pair again - so reading one as another
 * sends somebody to fix the wrong thing. The probe it posts is also meant to be
 * the uploader's request exactly, or a passing check would vouch for a request
 * the uploader never makes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ServerHealthTest {

    private lateinit var server: MockWebServer

    private val pairing = DevicePairing(TEST_DEVICE_ID, TEST_TOKEN)

    private fun baseUrl() = "http://${server.hostName}:${server.port}"

    private suspend fun check(paired: Boolean = true) =
        ServerHealthChecker { pairing.takeIf { paired } }.check(baseUrl())

    private fun answer(code: Int) = server.enqueue(MockResponse().setResponseCode(code))

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `a server that is there and accepts the device is ok`() = runTest {
        answer(200)
        answer(200)

        assertEquals(ServerHealth.Ok, check())
    }

    @Test
    fun `the probe is the uploader's request, posting an empty batch`() = runTest {
        answer(200)
        answer(200)

        check()

        val health = server.takeRequest()
        assertEquals("GET", health.method)
        assertEquals("/api/health", health.path)

        val probe = server.takeRequest()
        assertEquals("POST", probe.method)
        assertEquals(TELEMETRY_UPLOAD_PATH, probe.path)
        assertEquals(TEST_DEVICE_ID, probe.getHeader("Device-Id"))
        assertEquals("Bearer $TEST_TOKEN", probe.getHeader("Authorization"))
        assertEquals("gzip", probe.getHeader("Content-Encoding"))

        // Stored as nothing, which is what makes it safe to ask at any time.
        val body = GZIPInputStream(probe.body.inputStream()).bufferedReader().readText()
        assertEquals("[]", body)
    }

    @Test
    fun `nothing answering is unreachable`() = runTest {
        val address = baseUrl()
        server.shutdown()

        assertEquals(
            ServerHealth.Unreachable,
            ServerHealthChecker { pairing }.check(address)
        )
    }

    @Test
    fun `no health endpoint means the address is not this API`() = runTest {
        answer(404)

        assertEquals(ServerHealth.NotTheApi, check())
        assertEquals("the device is not asked about", 1, server.requestCount)
    }

    @Test
    fun `a failing health endpoint is a server fault, with its code`() = runTest {
        answer(503)

        assertEquals(ServerHealth.ServerFault(503), check())
    }

    @Test
    fun `an unpaired phone is only asked whether the server is there`() = runTest {
        answer(200)

        assertEquals(ServerHealth.NotPaired, check(paired = false))
        assertEquals("no credential to present, so no probe", 1, server.requestCount)
    }

    @Test
    fun `a refused credential means pairing again`() = runTest {
        answer(200)
        answer(401)

        assertEquals(ServerHealth.DeviceUnknown, check())
    }

    @Test
    fun `a forbidden device has been deactivated`() = runTest {
        answer(200)
        answer(403)

        assertEquals(ServerHealth.DeviceDeactivated, check())
    }

    @Test
    fun `a missing upload endpoint is not this API either`() = runTest {
        answer(200)
        answer(404)

        assertEquals(ServerHealth.NotTheApi, check())
    }

    @Test
    fun `a failing upload endpoint is a server fault, with its code`() = runTest {
        answer(200)
        answer(500)

        assertEquals(ServerHealth.ServerFault(500), check())
    }

    private companion object {
        const val TEST_DEVICE_ID = "test-device"
        const val TEST_TOKEN = "test-token"
    }
}
