package com.anonymus09.carsensors

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Whether the warning actually reaches the shade, which needs a device.
 *
 * The decision to warn is settled without one - see `UploadSilenceTest` - but
 * the point of this warning is that it is seen, and everything deciding that
 * is the platform's: whether the channel was created, at what importance, and
 * whether a notification posted to it survives. A warning as quiet as the
 * diagnostics panel it replaces would be no warning at all.
 */
@RunWith(AndroidJUnit4::class)
class UploadSilenceNotifierTest {

    private val context: Context =
        InstrumentationRegistry.getInstrumentation().targetContext

    private val manager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private val notifier = UploadSilenceNotifier(context)

    private fun posted() = manager.activeNotifications.firstOrNull {
        it.id == WARNING_NOTIFICATION_ID
    }

    /*
     * Posting crosses a binder into system_server, so the shade catches up a
     * moment after `notify` returns rather than during it. Polling for the
     * outcome keeps these tests from racing that, which they lost about half
     * the time when they read `activeNotifications` straight away.
     */
    private fun awaitPosted(): android.service.notification.StatusBarNotification? =
        awaitNotification { it != null }

    private fun awaitCleared() = awaitNotification { it == null }

    private fun awaitNotification(
        settled: (android.service.notification.StatusBarNotification?) -> Boolean
    ): android.service.notification.StatusBarNotification? {
        val deadline = System.currentTimeMillis() + SETTLE_TIMEOUT_MS

        while (System.currentTimeMillis() < deadline) {
            val current = posted()

            if (settled(current)) return current

            Thread.sleep(SETTLE_POLL_MS)
        }

        return posted()
    }

    @Before
    fun setUp() = notifier.clear()

    @After
    fun tearDown() = notifier.clear()

    @Test
    fun postsTheWarningOnItsOwnChannel() {
        notifier.warn("1200 samples waiting for 2 days. Nothing answered at http://10.0.0.1:3000.")

        val notification = awaitPosted()

        assertNotNull("the warning should be in the shade", notification)
        assertEquals(WARNING_CHANNEL_ID, notification!!.notification.channelId)
    }

    @Test
    fun theChannelIsLoudEnoughToBeNoticed() {
        notifier.warn("anything")
        awaitPosted()

        val channel = manager.getNotificationChannel(WARNING_CHANNEL_ID)

        assertNotNull("the channel should have been created", channel)

        /*
         * The logger's own channel is IMPORTANCE_LOW so that an ongoing
         * notification stays silent. This one has to do better than that or it
         * is exactly as easy to miss as the panel nobody opened.
         */
        assertTrue(
            "importance was ${channel!!.importance}",
            channel.importance >= NotificationManager.IMPORTANCE_DEFAULT
        )
    }

    @Test
    fun carriesTheWholeMessageWhereALongOneWouldBeTruncated() {
        val message = "40000 samples waiting for 50 days. The server does not recognise this " +
            "device. Register it in the web application."

        notifier.warn(message)

        val extras = awaitPosted()!!.notification.extras

        // The remedy is at the end of the sentence, so it has to survive collapsing.
        assertEquals(message, extras.getCharSequence("android.bigText")?.toString())
    }

    @Test
    fun clearsTheWarningOnceTelemetryMovesAgain() {
        notifier.warn("anything")
        assertNotNull(awaitPosted())

        notifier.clear()

        assertNull("the warning should not outlive the problem", awaitCleared())
    }

    private companion object {
        /*
         * Deliberately restated rather than exposed: these are what the running
         * app is already using, and a test that read them from the notifier
         * would agree with it however wrong both were.
         */
        const val WARNING_CHANNEL_ID = "telemetry_upload_warning_channel"
        const val WARNING_NOTIFICATION_ID = 1002

        const val SETTLE_TIMEOUT_MS = 5_000L
        const val SETTLE_POLL_MS = 50L
    }
}
