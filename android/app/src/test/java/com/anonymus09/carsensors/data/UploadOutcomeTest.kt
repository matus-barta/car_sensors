package com.anonymus09.carsensors.data

import org.junit.Assert.assertEquals
import org.junit.Test

class UploadOutcomeTest {

    private fun assertMaps(code: Int, expected: UploadOutcome) =
        assertEquals("HTTP $code", expected, UploadOutcome.forResponseCode(code))

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
}
