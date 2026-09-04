package com.anonymus09.carsensors.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlTest {

    private fun valid(input: String, allowCleartext: Boolean = true): String {
        val result = ServerUrl.validate(input, allowCleartext)
        assertTrue("expected $input to be valid, got $result", result is ServerUrl.Result.Valid)
        return (result as ServerUrl.Result.Valid).normalized
    }

    private fun invalid(input: String, allowCleartext: Boolean = true): String {
        val result = ServerUrl.validate(input, allowCleartext)
        assertTrue("expected $input to be rejected", result is ServerUrl.Result.Invalid)
        return (result as ServerUrl.Result.Invalid).reason
    }

    @Test
    fun `accepts an https address`() {
        assertEquals("https://example.org", valid("https://example.org"))
    }

    @Test
    fun `accepts an address with a port`() {
        assertEquals("http://192.168.1.5:3000", valid("http://192.168.1.5:3000"))
    }

    @Test
    fun `trims surrounding space and a trailing slash`() {
        assertEquals("https://example.org", valid("  https://example.org/  "))
    }

    @Test
    fun `rejects an empty address`() {
        invalid("   ")
    }

    @Test
    fun `rejects a scheme that is not http or https`() {
        invalid("ftp://example.org")
    }

    @Test
    fun `rejects an address with no scheme at all`() {
        invalid("example.org:3000")
    }

    @Test
    fun `rejects an address carrying a path`() {
        // The upload path is appended by the app, so a typed one would double up.
        invalid("https://example.org/api")
    }

    @Test
    fun `rejects cleartext when the build does not permit it`() {
        invalid("http://192.168.1.5:3000", allowCleartext = false)
    }

    @Test
    fun `still accepts https when cleartext is forbidden`() {
        assertEquals("https://example.org", valid("https://example.org", allowCleartext = false))
    }
}
