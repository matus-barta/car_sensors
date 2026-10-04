package com.anonymus09.carsensors.util

import android.os.Build
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationAccessTest {

    @Test
    fun `no foreground location is no location at all`() {
        assertEquals(
            LocationAccess.NONE,
            LocationAccess.from(Build.VERSION_CODES.TIRAMISU, foreground = false, background = true)
        )
    }

    /*
     * The handset this app was written for. There is no background permission
     * to hold before Android 10, and a reboot there gets GPS regardless.
     */
    @Test
    fun `foreground location is all there is before Android 10`() {
        assertEquals(
            LocationAccess.ALWAYS,
            LocationAccess.from(Build.VERSION_CODES.P, foreground = true, background = false)
        )
    }

    @Test
    fun `foreground location alone is lost after a reboot from Android 10`() {
        assertEquals(
            LocationAccess.WHILE_IN_USE,
            LocationAccess.from(Build.VERSION_CODES.Q, foreground = true, background = false)
        )
    }

    @Test
    fun `background location covers every start of the logger`() {
        assertEquals(
            LocationAccess.ALWAYS,
            LocationAccess.from(Build.VERSION_CODES.TIRAMISU, foreground = true, background = true)
        )
    }
}
