package com.anonymus09.carsensors.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_MAX_ATTEMPTS
import java.io.File
import kotlin.time.Duration

/** Row counts and upload progress, as one query returns them. */
data class TelemetryStats(
    val totalRows: Int = 0,
    val telemetryRows: Int = 0,
    val eventRows: Int = 0,
    val pendingUpload: Int = 0,
    val blockedUpload: Int = 0,
    val lastTimestamp: Long? = null,
    val lastUploadTime: Long? = null,
    val maxUploadAttempts: Int = 0
)

/** What the storage panel reports: the counts above, plus the file itself. */
data class TelemetryStorage(
    val stats: TelemetryStats = TelemetryStats(),
    val databaseExists: Boolean = false,
    val databaseSizeBytes: Long = 0,
    val databasePath: String = ""
)

class TelemetryRepository(
    private val dao: TelemetryDao,
    private val databaseFile: File
) {

    /**
     * Re-reads the storage figures every [interval] for as long as it is
     * collected.
     *
     * Deliberately a poll rather than a Room `Flow`: the table takes two writes
     * a second while logging, and an observing query would re-run on every one
     * of them. Polling costs one scan per interval whatever the write rate.
     *
     * The screen's own version of this only ticked while logging was active, so
     * every figure here froze the moment logging stopped - including the
     * pending count that decides whether "Force upload now" is enabled.
     */
    fun observeStorage(interval: Duration): Flow<TelemetryStorage> = flow {
        while (true) {
            emit(readStorage())
            delay(interval)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun pendingUploadCount(): Int = dao.getPendingUploadCount(UPLOAD_MAX_ATTEMPTS)

    private suspend fun readStorage() = TelemetryStorage(
        stats = dao.getStats(UPLOAD_MAX_ATTEMPTS),
        databaseExists = databaseFile.exists(),
        databaseSizeBytes = databaseFile.length(),
        databasePath = databaseFile.absolutePath
    )
}
