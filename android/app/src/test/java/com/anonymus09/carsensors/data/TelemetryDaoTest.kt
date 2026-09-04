package com.anonymus09.carsensors.data

import androidx.room.Room
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The statistics query is one pass of COUNT with CASE over the whole table, and
 * a mistake in any branch reports a wrong number rather than failing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TelemetryDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: TelemetryDao

    private fun sample(
        event: String = "telemetry_sample",
        timestamp: Long = 1_000,
        uploaded: Boolean = false,
        attempts: Int = 0,
        latitude: Double? = 48.1
    ) = TelemetrySampleEntity(
        event = event, timestamp = timestamp, payload = null,
        charging = true, powerSource = "USB",
        latitude = latitude, longitude = latitude?.let { 17.1 }, altitude = null,
        speedMps = null, speedKmh = null, bearing = null, accuracyM = null, provider = "gps",
        accelX = null, accelY = null, accelZ = null,
        accelAccuracy = null, accelAccuracyLabel = null,
        gyroX = null, gyroY = null, gyroZ = null, gyroAccuracy = null, gyroAccuracyLabel = null,
        magX = null, magY = null, magZ = null, magnetAccuracy = null, magnetAccuracyLabel = null,
        headingDeg = null, pressureHpa = null,
        pressureAccuracy = null, pressureAccuracyLabel = null,
        uploaded = uploaded, uploadedAt = if (uploaded) timestamp else null,
        uploadAttemptCount = attempts
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        )
            .allowMainThreadQueries()
            .build()
        dao = db.telemetryDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `counts telemetry apart from events, and pending apart from blocked`() = runTest {
        dao.insert(sample(timestamp = 10))
        dao.insert(sample(timestamp = 20))
        dao.insert(sample(event = "service_started", timestamp = 30, latitude = null))
        dao.insert(sample(timestamp = 40, uploaded = true))
        dao.insert(sample(timestamp = 50, attempts = 9))

        val stats = dao.getStats(maxAttempts = 5)

        assertEquals(5, stats.totalRows)
        assertEquals(4, stats.telemetryRows)
        assertEquals(1, stats.eventRows)
        // Three, not two: an event row is telemetry that still has to be sent,
        // so it counts as pending even though it is not a telemetry_sample.
        assertEquals(3, stats.pendingUpload)
        assertEquals(1, stats.blockedUpload)
        assertEquals(50L, stats.lastTimestamp)
        assertEquals(40L, stats.lastUploadTime)
        assertEquals(9, stats.maxUploadAttempts)
    }

    @Test
    fun `the pending batch skips rows the server keeps refusing`() = runTest {
        dao.insert(sample(timestamp = 10, attempts = 9))
        dao.insert(sample(timestamp = 20))

        val batch = dao.getPendingBatch(limit = 10, maxAttempts = 5)

        assertEquals(1, batch.size)
        assertEquals(20L, batch.first().timestamp)
    }

    @Test
    fun `the live batch takes the newest located rows, not the oldest`() = runTest {
        dao.insert(sample(timestamp = 10))
        dao.insert(sample(timestamp = 30))
        dao.insert(sample(timestamp = 20, latitude = null))

        val live = dao.getNewestLocatedPending(limit = 2, maxAttempts = 5)

        // Opposite order to the drain, and rows with no position cannot move a map.
        assertEquals(listOf(30L, 10L), live.map { it.timestamp })
    }
}
