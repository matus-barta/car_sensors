package com.anonymus09.carsensors.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.anonymus09.carsensors.util.AccessState
import com.anonymus09.carsensors.util.LastExit
import com.anonymus09.carsensors.util.LocationAccess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Which warnings the screen shows for which state of the phone.
 *
 * The rules are where the judgement is: a warning that repeats another says
 * nothing new, one shown when it does not apply teaches people to ignore the
 * rest, and one missing is a logger failing quietly. The words themselves are
 * matched only by their opening, so rewording one does not break this.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SetupWarningsTest {

    @get:Rule
    val compose = createComposeRule()

    private val fixes = mutableListOf<SetupFix>()

    private val allWell = AccessState(
        location = LocationAccess.ALWAYS,
        preciseLocation = true,
        notificationsEnabled = true,
        batteryUnrestricted = true,
        backgroundRestricted = false,
        dataSaverRestricted = false
    )

    private fun show(
        access: AccessState = allWell,
        locationRefused: Boolean = false,
        stoppedByUser: LastExit? = null,
        uploadsMayUseMobileData: Boolean = false
    ) = compose.setContent {
        // In a column, as on the screen; laid on top of each other, a click on
        // one button lands on whichever was drawn last.
        Column {
            SetupWarnings(
                access = access,
                locationRefused = locationRefused,
                stoppedByUser = stoppedByUser,
                uploadsMayUseMobileData = uploadsMayUseMobileData,
                onFix = { fixes += it }
            )
        }
    }

    private fun shown(opening: String): Boolean =
        compose.onAllNodesWithText(opening, substring = true).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun `nothing is said while all is well`() {
        show()

        assertEquals(
            "no warnings and no buttons",
            0,
            compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        )
    }

    @Test
    fun `location allowed only while open warns about reboots, and asks for more`() {
        show(access = allWell.copy(location = LocationAccess.WHILE_IN_USE))

        assertTrue(shown("Location is allowed only while"))

        compose.onNodeWithText("Allow location all the time").performClick()
        assertEquals(listOf(SetupFix.BACKGROUND_LOCATION), fixes)
    }

    /*
     * No location is ordinary until somebody presses Start - the button asks
     * for it then. Only a refusal needs saying.
     */
    @Test
    fun `no location is only a warning once it has been refused`() {
        show(access = allWell.copy(location = LocationAccess.NONE, preciseLocation = false))

        assertFalse(shown("Location is not allowed"))
        assertFalse(shown("Location is approximate"))
    }

    @Test
    fun `a refused location sends the user to the app's settings`() {
        show(
            access = allWell.copy(location = LocationAccess.NONE, preciseLocation = false),
            locationRefused = true
        )

        assertTrue(shown("Location is not allowed"))

        compose.onNodeWithText("Open app settings").performClick()
        assertEquals(listOf(SetupFix.APP_SETTINGS), fixes)
    }

    @Test
    fun `approximate location asks for precise`() {
        show(access = allWell.copy(preciseLocation = false))

        assertTrue(shown("Location is approximate"))

        compose.onNodeWithText("Allow precise location").performClick()
        assertEquals(listOf(SetupFix.PRECISE_LOCATION), fixes)
    }

    @Test
    fun `an optimized battery offers Android's own exemption dialog`() {
        show(access = allWell.copy(batteryUnrestricted = false))

        assertTrue(shown("Battery optimization can pause"))

        compose.onNodeWithText("Allow unrestricted battery use").performClick()
        assertEquals(listOf(SetupFix.BATTERY_OPTIMIZATION), fixes)
    }

    // A restriction says everything optimization does and more.
    @Test
    fun `a restricted app is told so once, not twice`() {
        show(access = allWell.copy(batteryUnrestricted = false, backgroundRestricted = true))

        assertTrue(shown("Android restricts this app"))
        assertFalse(shown("Battery optimization can pause"))
    }

    @Test
    fun `notifications off offers their switch`() {
        show(access = allWell.copy(notificationsEnabled = false))

        assertTrue(shown("Notifications are off"))

        compose.onNodeWithText("Turn on notifications").performClick()
        assertEquals(listOf(SetupFix.NOTIFICATIONS), fixes)
    }

    @Test
    fun `data saver matters only when uploads may use mobile data`() {
        show(access = allWell.copy(dataSaverRestricted = true), uploadsMayUseMobileData = false)

        assertFalse(shown("Data Saver is on"))
    }

    @Test
    fun `data saver with mobile uploads allowed offers the exemption`() {
        show(access = allWell.copy(dataSaverRestricted = true), uploadsMayUseMobileData = true)

        assertTrue(shown("Data Saver is on"))

        compose.onNodeWithText("Allow background data").performClick()
        assertEquals(listOf(SetupFix.DATA_SAVER), fixes)
    }

    @Test
    fun `a stop from Android is explained`() {
        show(stoppedByUser = LastExit("USER_REQUESTED", null, 0))

        assertTrue(shown("Logging was stopped from Android"))
    }
}
