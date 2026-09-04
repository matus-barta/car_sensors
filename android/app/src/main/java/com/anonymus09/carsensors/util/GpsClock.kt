package com.anonymus09.carsensors.util

import android.location.Location
import android.location.LocationManager
import android.os.SystemClock

/**
 * Wall-clock time, disciplined against GPS once a fix has been seen.
 *
 * `System.currentTimeMillis()` comes from the cellular network or NTP - Android
 * never sets it from GPS - so a device that has been offline can be minutes
 * wrong. That is not merely untidy: `ingest` picks the live position by
 * timestamp and keeps a monotonic guard, so one sample from a clock running
 * ahead suppresses every genuine position after it until the key expires.
 *
 * Rather than stamping a sample with the fix's own time, this keeps the offset
 * between satellite time and the monotonic clock and extrapolates from it.
 * Using `Location.getTime()` directly would stamp every row written during a
 * GPS outage with the same increasingly stale moment, because the last known
 * fix keeps being returned; an offset carries the correction forward while
 * still advancing in real time.
 */
class GpsClock {

    /** Satellite time minus the monotonic clock, once GPS has told us. */
    @Volatile
    private var offsetMs: Long? = null

    val isDisciplined: Boolean get() = offsetMs != null

    /**
     * Takes the correction from a fix, if that fix can supply one.
     *
     * Only the GPS provider carries satellite time. A network fix's `time` is
     * the system clock the provider read, so disciplining against it would just
     * copy back the clock this exists to distrust.
     */
    fun discipline(location: Location) {
        if (location.provider != LocationManager.GPS_PROVIDER) return

        offsetMs = location.time - location.elapsedRealtimeMs()
    }

    /** The current time, GPS-derived where possible. */
    fun nowMs(): Long {
        val offset = offsetMs ?: return System.currentTimeMillis()

        return offset + SystemClock.elapsedRealtime()
    }
}

/** When this fix was taken, on the monotonic clock. */
fun Location.elapsedRealtimeMs(): Long = elapsedRealtimeNanos / 1_000_000

/**
 * How long ago this fix was taken.
 *
 * Measured against the monotonic clock rather than [Location.getTime], so it
 * stays right across a clock correction.
 */
fun Location.ageMs(): Long = SystemClock.elapsedRealtime() - elapsedRealtimeMs()
