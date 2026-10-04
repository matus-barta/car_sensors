package com.anonymus09.carsensors.data

import com.anonymus09.carsensors.util.AppConfig.BATTERY_LOCATION_ONLY_PERCENT
import com.anonymus09.carsensors.util.AppConfig.BATTERY_PAUSE_UPLOAD_PERCENT
import com.anonymus09.carsensors.util.AppConfig.BATTERY_REDUCE_RATE_PERCENT
import com.anonymus09.carsensors.util.AppConfig.BATTERY_STOP_RECORDING_PERCENT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the logger gives up as the battery drains.
 *
 * Getting a tier wrong costs either the phone - recording on into a flat
 * battery - or the data, giving up while there was plenty left. The thresholds
 * themselves are settings in `AppConfig`; what is tested is that each one takes
 * effect at its own level and not one percent either side.
 */
class PowerStateTest {

    private fun onBattery(percent: Int) = PowerState(charging = false, levelPercent = percent).tier

    @Test
    fun `on power everything runs, however low the battery`() {
        assertEquals(PowerTier.FULL, PowerState(charging = true, levelPercent = 1).tier)
    }

    @Test
    fun `an unknown level is not a reason to give anything up`() {
        assertEquals(PowerTier.FULL, PowerState(charging = false, levelPercent = null).tier)
    }

    @Test
    fun `above the first threshold nothing is given up`() {
        assertEquals(PowerTier.FULL, onBattery(100))
        assertEquals(PowerTier.FULL, onBattery(BATTERY_PAUSE_UPLOAD_PERCENT + 1))
    }

    @Test
    fun `each tier starts exactly at its threshold`() {
        assertEquals(PowerTier.NO_UPLOAD, onBattery(BATTERY_PAUSE_UPLOAD_PERCENT))
        assertEquals(PowerTier.NO_UPLOAD, onBattery(BATTERY_REDUCE_RATE_PERCENT + 1))

        assertEquals(PowerTier.REDUCED_RATE, onBattery(BATTERY_REDUCE_RATE_PERCENT))
        assertEquals(PowerTier.REDUCED_RATE, onBattery(BATTERY_LOCATION_ONLY_PERCENT + 1))

        assertEquals(PowerTier.LOCATION_ONLY, onBattery(BATTERY_LOCATION_ONLY_PERCENT))
        assertEquals(PowerTier.LOCATION_ONLY, onBattery(BATTERY_STOP_RECORDING_PERCENT + 1))

        assertEquals(PowerTier.PAUSED, onBattery(BATTERY_STOP_RECORDING_PERCENT))
        assertEquals(PowerTier.PAUSED, onBattery(0))
    }

    /*
     * The service and the uploader compare tiers rather than naming them -
     * "REDUCED_RATE or worse" - so the order of the enum is part of what it
     * means, and the thresholds have to fall in the same order for every tier
     * to be reachable at all.
     */
    @Test
    fun `tiers are ordered by how much is given up, and so are their thresholds`() {
        assertEquals(
            listOf(
                PowerTier.FULL,
                PowerTier.NO_UPLOAD,
                PowerTier.REDUCED_RATE,
                PowerTier.LOCATION_ONLY,
                PowerTier.PAUSED
            ),
            PowerTier.entries.toList()
        )

        assertTrue(BATTERY_PAUSE_UPLOAD_PERCENT > BATTERY_REDUCE_RATE_PERCENT)
        assertTrue(BATTERY_REDUCE_RATE_PERCENT > BATTERY_LOCATION_ONLY_PERCENT)
        assertTrue(BATTERY_LOCATION_ONLY_PERCENT > BATTERY_STOP_RECORDING_PERCENT)
    }
}
