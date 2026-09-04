package com.anonymus09.carsensors.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one thing here that genuinely needs a device.
 *
 * This database holds the only copy of telemetry that has not reached the
 * server, and the destructive fallback that used to paper over a bad migration
 * has deliberately been removed. So a migration that is wrong no longer fails
 * loudly in development and quietly deletes a backlog in a car - it has to be
 * caught before it ships.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    /**
     * Version 3 changed what `uploadAttemptCount` means rather than the shape
     * of the table: it used to count every attempt, including those that failed
     * only because the server was unreachable, and now counts refusals alone
     * and decides when a row stops being retried.
     *
     * Without the reset, counts accumulated under the old meaning would put
     * perfectly good rows past the new threshold. That is not hypothetical - on
     * the handset this was written for, 500 rows were already there, having
     * done nothing worse than wait while the server was down.
     */
    @Test
    fun migratingToVersion3ForgivesRowsThatWereOnlyWaiting() {
        helper.createDatabase(TEST_DB, 2).use { db ->
            db.execSQL(insertRow(id = 1, uploaded = false, attempts = 9))
            db.execSQL(insertRow(id = 2, uploaded = false, attempts = 0))
            db.execSQL(insertRow(id = 3, uploaded = true, attempts = 4))
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            3,
            true,
            AppDatabase.MIGRATION_2_3
        )

        assertEquals("a waiting row is forgiven", 0, attemptsOf(migrated, id = 1))
        assertEquals("a row with nothing to forgive is untouched", 0, attemptsOf(migrated, id = 2))

        // Only rows still waiting are reset. An uploaded row's count is history
        // and resetting it would quietly rewrite what happened.
        assertEquals("an uploaded row keeps its count", 4, attemptsOf(migrated, id = 3))
    }

    private fun insertRow(id: Int, uploaded: Boolean, attempts: Int) =
        """
        INSERT INTO telemetry_samples
            (id, event, timestamp, charging, uploaded, uploadAttemptCount)
        VALUES
            ($id, 'telemetry_sample', $id, 0, ${if (uploaded) 1 else 0}, $attempts)
        """.trimIndent()

    private fun attemptsOf(db: androidx.sqlite.db.SupportSQLiteDatabase, id: Int): Int =
        db.query("SELECT uploadAttemptCount FROM telemetry_samples WHERE id = $id").use {
            it.moveToFirst()
            it.getInt(0)
        }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
