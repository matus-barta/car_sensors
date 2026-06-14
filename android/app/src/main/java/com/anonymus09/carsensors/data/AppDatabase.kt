package com.anonymus09.carsensors.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.anonymus09.carsensors.util.AppConfig.DB_NAME
import java.io.File

@Database(
    entities = [TelemetrySampleEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun telemetryDao(): TelemetryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration(true)
                    .build().also { INSTANCE = it }
            }
        }


        // expose DB file path for UI/debug
        fun getDatabaseFile(context: Context): File {
            return context.getDatabasePath(DB_NAME)
        }

        // optional: expose DB directory (nice for debugging)
        fun getDatabaseDir(context: Context): File? {
            return getDatabaseFile(context).parentFile
        }

    }
}