package com.anonymus09.carsensors.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadOutcomeTest {

    private fun assertMaps(code: Int, expected: UploadOutcome) =
        assertEquals("HTTP $code", expected, UploadOutcome.forResponseCode(code))

    private fun assertMaps(code: Int, challenge: String?, expected: UploadOutcome) =
        assertEquals("HTTP $code / $challenge", expected, UploadOutcome.forResponseCode(code, challenge))

    /*
     * Copied from what `ingest` actually sends, trailing comma and all, rather
     * than from what RFC 6750 allows. The two services agree on these strings
     * or the phone reads every refusal as the same thing.
     */
    private val invalidToken =
        """Bearer realm="telemetry", error="invalid_token", error_description="the device token was not accepted""""

    private val insufficientScope =
        """Bearer realm="telemetry", error="insufficient_scope", error_description="the device has been deactivated""""

    private val noErrorCode = """Bearer realm="telemetry""""

    @Test
    fun `any success stores the batch`() {
        assertMaps(200, UploadOutcome.STORED)
        assertMaps(201, UploadOutcome.STORED)
        assertMaps(204, UploadOutcome.STORED)
    }

    @Test
    fun `an unparseable body is the batch's own fault`() {
        assertMaps(400, UploadOutcome.MALFORMED)
        assertMaps(422, UploadOutcome.MALFORMED)
    }

    @Test
    fun `an unknown device, a banned one and a wrong path are not the rows' fault`() {
        // These refuse every batch alike, so counting them against the rows
        // would quarantine good data over a configuration mistake.
        assertMaps(401, UploadOutcome.REFUSED)
        assertMaps(403, UploadOutcome.REFUSED)
        assertMaps(404, UploadOutcome.REFUSED)
    }

    @Test
    fun `too large asks for a smaller batch rather than giving up`() {
        assertMaps(413, UploadOutcome.TOO_LARGE)
    }

    @Test
    fun `timeouts and throttling are worth retrying`() {
        assertMaps(408, UploadOutcome.TRANSIENT)
        assertMaps(429, UploadOutcome.TRANSIENT)
    }

    @Test
    fun `server faults are transient`() {
        assertMaps(500, UploadOutcome.TRANSIENT)
        assertMaps(502, UploadOutcome.TRANSIENT)
        assertMaps(503, UploadOutcome.TRANSIENT)
    }

    @Test
    fun `anything unrecognised is assumed transient rather than permanent`() {
        // Better to retry something harmless than to discard a batch over a
        // response nobody anticipated.
        assertMaps(100, UploadOutcome.TRANSIENT)
        assertMaps(302, UploadOutcome.TRANSIENT)
    }

    @Test
    fun `a refused credential is told apart from a retired vehicle`() {
        // Pairing again fixes the first and will never fix the second, so the
        // phone has to be able to say which happened.
        assertMaps(401, invalidToken, UploadOutcome.CREDENTIAL_REJECTED)
        assertMaps(403, insufficientScope, UploadOutcome.DEVICE_RETIRED)
    }

    @Test
    fun `an unexplained 401 or 403 claims nothing about the pairing`() {
        /*
         * A proxy or a wrong address produces these, and telling somebody their
         * vehicle has been retired on that evidence would be worse than saying
         * nothing. Only an explicit error code from our own server is acted on.
         */
        assertMaps(401, null, UploadOutcome.REFUSED)
        assertMaps(403, null, UploadOutcome.REFUSED)
        assertMaps(401, noErrorCode, UploadOutcome.REFUSED)
        assertMaps(403, "Basic realm=\"proxy\"", UploadOutcome.REFUSED)
    }

    @Test
    fun `a challenge for another scheme is ignored`() {
        assertMaps(401, "Basic realm=\"proxy\", error=\"invalid_token\"", UploadOutcome.REFUSED)
    }

    @Test
    fun `the error code is read wherever it sits in the challenge`() {
        // The specification fixes no order for the parameters.
        assertMaps(
            401,
            """Bearer error="invalid_token", realm="telemetry"""",
            UploadOutcome.CREDENTIAL_REJECTED
        )
    }

    @Test
    fun `a code that is not an authentication answer is unaffected by a challenge`() {
        assertMaps(413, invalidToken, UploadOutcome.TOO_LARGE)
        assertMaps(200, insufficientScope, UploadOutcome.STORED)
    }
}
