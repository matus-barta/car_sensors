package com.anonymus09.carsensors.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.anonymus09.carsensors.util.AppConfig.DB_NAME
import java.io.File

@Database(
    entities = [TelemetrySampleEntity::class],
    version = 3,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun telemetryDao(): TelemetryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * `uploadAttemptCount` changed meaning in this version.
         *
         * It used to count every attempt, including the ones that failed only
         * because the server was unreachable, and nothing ever read it. It now
         * counts outright refusals alone and decides when a row stops being
         * retried, so a count accumulated under the old meaning would strand a
         * backlog that is merely waiting for the server to come back. Rows that
         * have yet to be uploaded start again from zero.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "UPDATE telemetry_samples SET uploadAttemptCount = 0 WHERE uploaded = 0"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    /*
                     * No destructive fallback here on purpose. This database is
                     * the only copy of telemetry that has not reached the
                     * server yet, and falling back would silently delete a
                     * backlog that can be weeks deep. A missing migration
                     * should fail loudly instead, which is also why the schema
                     * is exported from this version on.
                     */
                    .addMigrations(MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
        }

        // expose DB file path for UI/debug
        fun getDatabaseFile(context: Context): File {
            return context.getDatabasePath(DB_NAME)
        }
    }
}
