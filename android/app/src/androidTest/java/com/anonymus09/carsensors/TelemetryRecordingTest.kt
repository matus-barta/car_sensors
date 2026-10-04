package com.anonymus09.carsensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.anonymus09.carsensors.data.AppDatabase
import com.anonymus09.carsensors.data.SettingsRepository
import com.anonymus09.carsensors.data.TelemetrySettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import kotlin.math.abs

/**
 * Whether the logger, started the way the app starts it, turns GPS fixes into
 * stored samples on the Android version it is running on.
 *
 * Every rule that decides this belongs to the platform rather than to this
 * code: whether a foreground service may start at all, whether it may read
 * location once it has, and whether updates keep arriving. Those rules change
 * between Android versions whatever the app targets, which is why this runs on
 * every managed device rather than on one.
 *
 * Fixes come from a mock GPS provider rather than from the emulator's own, so
 * the test decides where the vehicle is and can recognise its own positions in
 * the database afterwards.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class TelemetryRecordingTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val packageName = context.packageName

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val settings = SettingsRepository(context)
    private val database = AppDatabase.getInstance(context).openHelper.readableDatabase

    private lateinit var previousSettings: TelemetrySettings
    private var mockProviderAdded = false

    @Before
    fun setUp() {
        /*
         * Emulators only. On a real handset this would feed invented positions
         * into the backlog that the uploader later sends to the server, and
         * nothing here can tell those rows apart from a real journey once they
         * are there.
         */
        assumeTrue("runs on an emulator only", Build.HARDWARE in EMULATOR_HARDWARE)

        previousSettings = settings.current()

        /*
         * Recording from the first moment. Waiting for movement would hang on
         * the significant motion sensor, which an emulator may or may not
         * offer, and recording on battery takes the emulated charger out of
         * the question.
         */
        settings.setWakeOnMotion(false)
        settings.setRecordOnBattery(true)
        settings.setLiveUploadEnabled(false)

        // What the user grants from the dialog, and nothing more.
        instrumentation.uiAutomation.grantRuntimePermission(
            packageName,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        instrumentation.uiAutomation.grantRuntimePermission(
            packageName,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        shell("appops set $packageName android:mock_location allow")
        addMockGpsProvider()
    }

    @After
    fun tearDown() {
        if (!::previousSettings.isInitialized) return

        TelemetryForegroundService.stopService(context)
        awaitLoggerState(LoggerState.OFF)

        if (mockProviderAdded) locationManager.removeTestProvider(LocationManager.GPS_PROVIDER)
        shell("appops set $packageName android:mock_location default")

        settings.setWakeOnMotion(previousSettings.wakeOnMotion)
        settings.setRecordOnBattery(previousSettings.recordOnBattery)
        settings.setLiveUploadEnabled(previousSettings.liveUploadEnabled)
        settings.setLoggerEnabled(previousSettings.loggerEnabled)
    }

    /**
     * The button on the screen: the app is in front when the logger starts, so
     * "while using the app" should be all the location access it needs.
     */
    @Test
    fun recordsGpsWhenStartedFromTheScreen() {
        /*
         * The background test grants more than this one may have, and nothing
         * can take it back: revoking a permission kills the app's process, and
         * the test runs inside it. So this one runs first, and checks that it
         * did - passing with background access held would prove nothing.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertEquals(
                "background location is already granted, so this test cannot show it is not needed",
                PackageManager.PERMISSION_DENIED,
                context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            )
        }

        ActivityScenario.launch(MainActivity::class.java).use {
            TelemetryForegroundService.startService(context)

            assertRecordsTheFixesGpsReports()
        }
    }

    /**
     * After a reboot, `BootReceiver` starts the logger with nothing on screen.
     *
     * From Android 11, a foreground service started from the background gets
     * location only if the app holds background location - "Allow all the
     * time" - and whatever the app targets. Without it the service runs and
     * writes samples, but no fix ever reaches it. That is granted here, so this
     * covers the background path working rather than the permission missing;
     * the app does not yet ask for it - see `todo.md`.
     */
    @Test
    fun recordsGpsWhenStartedInTheBackground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            instrumentation.uiAutomation.grantRuntimePermission(
                packageName,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        }

        TelemetryForegroundService.startService(context)

        assertRecordsTheFixesGpsReports()
    }

    @Test
    fun stopsWhenSwitchedOff() {
        TelemetryForegroundService.startService(context)
        awaitLoggerState(LoggerState.RECORDING)

        TelemetryForegroundService.stopService(context)

        assertEquals(
            "the logger should have stopped",
            LoggerState.OFF,
            awaitLoggerState(LoggerState.OFF)
        )
    }

    private fun assertRecordsTheFixesGpsReports() {
        val before = lastRowId()

        assertEquals(
            "the logger should be recording",
            LoggerState.RECORDING,
            awaitLoggerState(LoggerState.RECORDING)
        )

        val sent = mutableListOf<Location>()
        var stored: RecordedPosition? = null
        val deadline = SystemClock.elapsedRealtime() + RECORD_TIMEOUT_MS

        /*
         * Fixes keep arriving for as long as it takes, the way they do in a
         * moving car. A single fix would race the service's request for
         * updates, and anything sent before that request lands is never
         * delivered.
         */
        while (stored == null && SystemClock.elapsedRealtime() < deadline) {
            val fix = fixAt(step = sent.size)
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, fix)
            sent += fix

            Thread.sleep(FIX_INTERVAL_MS)

            stored = recordedPositions(after = before).firstOrNull { row ->
                sent.any { it.isAt(row) }
            }
        }

        assertNotNull("none of ${sent.size} fixes reached the database", stored)
        assertEquals(LocationManager.GPS_PROVIDER, stored!!.provider)
        assertEquals(DRIVING_SPEED_MPS, stored.speedMps!!, SPEED_TOLERANCE_MPS)

        assertTrue(
            "the screen should report a fix",
            TelemetryForegroundService.locationStatus.value.hasFix
        )
    }

    /*
     * Replaces the emulator's own GPS for the length of the test. It has to be
     * in place before the service starts: the service asks whether GPS is
     * enabled when it begins recording and does not ask again.
     */
    @Suppress("DEPRECATION")
    private fun addMockGpsProvider() {
        locationManager.addTestProvider(
            LocationManager.GPS_PROVIDER,
            false,
            true,
            false,
            false,
            true,
            true,
            true,
            Criteria.POWER_LOW,
            Criteria.ACCURACY_FINE
        )
        mockProviderAdded = true

        locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)

        assertTrue(
            "location is switched off on this device, so no provider can be enabled",
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        )
    }

    /** A point on a straight road heading east, one step further along each time. */
    private fun fixAt(step: Int) = Location(LocationManager.GPS_PROVIDER).apply {
        latitude = START_LATITUDE
        longitude = START_LONGITUDE + step * STEP_DEGREES
        altitude = ALTITUDE_M
        accuracy = ACCURACY_M
        speed = DRIVING_SPEED_MPS
        bearing = EAST_DEGREES
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    private fun Location.isAt(row: RecordedPosition) =
        abs(latitude - row.latitude) < COORDINATE_TOLERANCE &&
            abs(longitude - row.longitude) < COORDINATE_TOLERANCE

    private fun lastRowId(): Long =
        database.query("SELECT COALESCE(MAX(id), 0) FROM telemetry_samples").use { cursor ->
            cursor.moveToFirst()
            cursor.getLong(0)
        }

    private fun recordedPositions(after: Long): List<RecordedPosition> = database.query(
        """
        SELECT latitude, longitude, provider, speedMps FROM telemetry_samples
        WHERE id > ? AND event = 'telemetry_sample' AND latitude IS NOT NULL
        """.trimIndent(),
        arrayOf<Any?>(after)
    ).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(
                    RecordedPosition(
                        latitude = cursor.getDouble(0),
                        longitude = cursor.getDouble(1),
                        provider = cursor.getString(2),
                        speedMps = if (cursor.isNull(3)) null else cursor.getFloat(3)
                    )
                )
            }
        }
    }

    /*
     * The state lives in the service's companion object, which shares this
     * process, so it can be read directly - but it changes on the service's own
     * threads, and only a moment after the call that asked for it returns.
     */
    private fun awaitLoggerState(expected: LoggerState): LoggerState {
        val deadline = SystemClock.elapsedRealtime() + STATE_TIMEOUT_MS

        while (SystemClock.elapsedRealtime() < deadline) {
            val current = TelemetryForegroundService.loggerState.value

            if (current == expected) return current

            Thread.sleep(POLL_MS)
        }

        return TelemetryForegroundService.loggerState.value
    }

    /** Runs a shell command as the shell user, and waits for it to finish. */
    private fun shell(command: String) {
        val output = instrumentation.uiAutomation.executeShellCommand(command)

        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    private data class RecordedPosition(
        val latitude: Double,
        val longitude: Double,
        val provider: String?,
        val speedMps: Float?
    )

    private companion object {
        /** What `Build.HARDWARE` reports on the Android emulator, old and new. */
        val EMULATOR_HARDWARE = setOf("ranchu", "goldfish")

        const val START_LATITUDE = 48.1486
        const val START_LONGITUDE = 17.1077
        const val STEP_DEGREES = 0.0001
        const val ALTITUDE_M = 140.0
        const val ACCURACY_M = 5f
        const val EAST_DEGREES = 90f

        /** 50 km/h, comfortably above what the service counts as moving. */
        const val DRIVING_SPEED_MPS = 13.9f

        const val COORDINATE_TOLERANCE = 1e-7
        const val SPEED_TOLERANCE_MPS = 0.01f

        const val FIX_INTERVAL_MS = 500L
        const val RECORD_TIMEOUT_MS = 20_000L
        const val STATE_TIMEOUT_MS = 10_000L
        const val POLL_MS = 50L
    }
}
