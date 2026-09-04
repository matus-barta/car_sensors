package com.anonymus09.carsensors.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GpsClockTest {

    private var elapsed = 1_000L
    private var system = 5_000L

    private val clock = GpsClock(elapsedRealtime = { elapsed }, systemTime = { system })

    @Test
    fun `falls back to the system clock until a fix has been seen`() {
        assertFalse(clock.isDisciplined)
        assertEquals(5_000L, clock.nowMs())
    }

    @Test
    fun `takes its correction from a gps fix`() {
        // Satellite time is 9,000 while the monotonic clock reads 1,000, so the
        // offset is 8,000 and the system clock's 5,000 is discarded.
        clock.discipline("gps", fixTimeMs = 9_000, fixElapsedMs = 1_000)

        assertTrue(clock.isDisciplined)
        assertEquals(9_000L, clock.nowMs())
    }

    @Test
    fun `carries the correction forward as time passes`() {
        clock.discipline("gps", fixTimeMs = 9_000, fixElapsedMs = 1_000)
        elapsed += 30_000

        // The point of holding an offset rather than the fix's own timestamp:
        // it keeps advancing between fixes instead of freezing at the last one.
        assertEquals(39_000L, clock.nowMs())
    }

    @Test
    fun `ignores a network fix, whose time is only the system clock again`() {
        clock.discipline("network", fixTimeMs = 9_000, fixElapsedMs = 1_000)

        assertFalse(clock.isDisciplined)
        assertEquals(5_000L, clock.nowMs())
    }

    @Test
    fun `corrects a system clock running ahead`() {
        // The case that matters to ingest: a device whose own clock is fast
        // would otherwise publish a position no later one could displace.
        system = 5 * 60 * 1000
        clock.discipline("gps", fixTimeMs = 10_000, fixElapsedMs = 1_000)

        assertEquals(10_000L, clock.nowMs())
    }

    @Test
    fun `a later fix replaces an earlier correction`() {
        clock.discipline("gps", fixTimeMs = 9_000, fixElapsedMs = 1_000)
        clock.discipline("gps", fixTimeMs = 12_500, fixElapsedMs = 1_000)

        assertEquals(12_500L, clock.nowMs())
    }
}
