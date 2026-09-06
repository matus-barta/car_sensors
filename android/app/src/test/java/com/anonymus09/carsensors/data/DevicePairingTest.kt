package com.anonymus09.carsensors.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The pairing payload is a contract with a server written in another language,
 * so what this accepts and refuses is worth pinning down.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DevicePairingTest {

    private val deviceId = "61d96b75-a9fe-498d-9b7d-056ec07d5630"
    private val token = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"

    @Test
    fun `reads the payload the web application encodes`() {
        val pairing = parsePairingPayload(
            """{"v":1,"deviceId":"$deviceId","token":"$token"}"""
        )

        assertEquals(DevicePairing(deviceId, token), pairing)
    }

    @Test
    fun `tolerates surrounding whitespace`() {
        assertEquals(
            DevicePairing(deviceId, token),
            parsePairingPayload("  {\"v\":1,\"deviceId\":\"$deviceId\",\"token\":\"$token\"}\n")
        )
    }

    @Test
    fun `refuses a payload version it does not understand`() {
        /*
         * The app ships separately from the server, so a newer payload has to
         * be refused rather than half-read - pairing with values pulled out of
         * a shape this build does not know would be worse than not pairing.
         */
        assertNull(parsePairingPayload("""{"v":2,"deviceId":"$deviceId","token":"$token"}"""))
    }

    @Test
    fun `refuses a payload with no version at all`() {
        assertNull(parsePairingPayload("""{"deviceId":"$deviceId","token":"$token"}"""))
    }

    @Test
    fun `refuses a payload missing the token`() {
        assertNull(parsePairingPayload("""{"v":1,"deviceId":"$deviceId"}"""))
    }

    @Test
    fun `refuses a payload missing the identity`() {
        assertNull(parsePairingPayload("""{"v":1,"token":"$token"}"""))
    }

    @Test
    fun `refuses a QR code that is not JSON at all`() {
        // Pointing the camera at a parcel label should say so, not pair.
        assertNull(parsePairingPayload("https://example.org/tracking/12345"))
    }

    @Test
    fun `builds a pairing from two pasted values`() {
        assertEquals(
            DevicePairing(deviceId, token),
            devicePairingOrNull("  $deviceId  ", "  $token  ")
        )
    }

    @Test
    fun `refuses pasted values when either is blank`() {
        assertNull(devicePairingOrNull(deviceId, "   "))
        assertNull(devicePairingOrNull("", token))
        assertNull(devicePairingOrNull(null, token))
    }
}
