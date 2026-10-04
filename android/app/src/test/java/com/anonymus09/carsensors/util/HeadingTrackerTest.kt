package com.anonymus09.carsensors.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The heading from a phone lying flat, screen up, turned to face each way.
 *
 * Android reports the azimuth from -180 to 180; a heading runs 0 to 360, and a
 * phone facing west is where the two disagree. The field is Earth's as the
 * phone feels it: pointing north along the ground and down into it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HeadingTrackerTest {

    /** Gravity, as the accelerometer reports it for a phone flat on its back. */
    private val flat = floatArrayOf(0f, 0f, GRAVITY)

    /** The field when north is along the phone's own axes by [northX], [northY]. */
    private fun field(northX: Float, northY: Float) =
        floatArrayOf(northX * HORIZONTAL_UT, northY * HORIZONTAL_UT, -VERTICAL_UT)

    private fun headingFacing(northX: Float, northY: Float): Float? =
        HeadingTracker().apply { update(flat, field(northX, northY)) }.degrees

    @Test
    fun `nothing is known until both sensors have reported`() {
        val tracker = HeadingTracker()

        tracker.update(flat, null)
        assertNull(tracker.degrees)

        tracker.update(null, field(0f, 1f))
        assertNull(tracker.degrees)
    }

    @Test
    fun `facing north is zero`() {
        // The top of the phone points north, so north lies along +y.
        assertEquals(0f, headingFacing(northX = 0f, northY = 1f)!!, TOLERANCE)
    }

    @Test
    fun `facing east is ninety`() {
        // Top to the east puts north off the phone's left edge, along -x.
        assertEquals(90f, headingFacing(northX = -1f, northY = 0f)!!, TOLERANCE)
    }

    @Test
    fun `facing west is two hundred and seventy, not minus ninety`() {
        assertEquals(270f, headingFacing(northX = 1f, northY = 0f)!!, TOLERANCE)
    }

    @Test
    fun `a reading that cannot be resolved keeps the last heading`() {
        val tracker = HeadingTracker()
        tracker.update(flat, field(northX = 0f, northY = 1f))

        // In free fall there is no gravity to tell down from up.
        tracker.update(floatArrayOf(0f, 0f, 0f), field(northX = -1f, northY = 0f))

        assertEquals(0f, tracker.degrees!!, TOLERANCE)
    }

    private companion object {
        const val GRAVITY = 9.81f

        // Roughly central Europe, in microtesla.
        const val HORIZONTAL_UT = 20f
        const val VERTICAL_UT = 44f

        const val TOLERANCE = 0.5f
    }
}
