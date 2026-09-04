package com.anonymus09.carsensors.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TelemetryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: TelemetrySampleEntity)

    @Query(
        """
        SELECT * FROM telemetry_samples
        WHERE uploaded = 0 AND uploadAttemptCount < :maxAttempts
        ORDER BY timestamp ASC
        LIMIT :limit
    """
    )
    suspend fun getPendingBatch(limit: Int, maxAttempts: Int): List<TelemetrySampleEntity>

    @Query(
        """
        SELECT COUNT(*) FROM telemetry_samples
        WHERE uploaded = 0 AND uploadAttemptCount < :maxAttempts
    """
    )
    suspend fun getPendingUploadCount(maxAttempts: Int): Int

    @Query(
        """
        UPDATE telemetry_samples
        SET uploaded = 1,
            uploadedAt = :uploadedAt
        WHERE id IN (:ids)
    """
    )
    suspend fun markUploaded(ids: List<Long>, uploadedAt: Long)

    @Query(
        """
        UPDATE telemetry_samples
        SET uploadAttemptCount = uploadAttemptCount + 1
        WHERE id IN (:ids)
    """
    )
    suspend fun incrementUploadAttempts(ids: List<Long>)

    @Query(
        """
        DELETE FROM telemetry_samples
        WHERE uploaded = 1 AND uploadedAt IS NOT NULL AND uploadedAt < :cutoff
    """
    )
    suspend fun deleteUploadedOlderThan(cutoff: Long): Int

    /**
     * Every figure the storage panel shows, in one pass over the table.
     *
     * These were seven separate queries driven from the composable. Asking for
     * them together is one scan rather than seven, and COUNT with a CASE that
     * yields NULL for the rows it should skip keeps each column non-null
     * without SUM's nullability.
     */
    @Query(
        """
        SELECT
            COUNT(*) AS totalRows,
            COUNT(CASE WHEN event = 'telemetry_sample' THEN 1 END) AS telemetryRows,
            COUNT(CASE WHEN event != 'telemetry_sample' THEN 1 END) AS eventRows,
            COUNT(CASE WHEN uploaded = 0 AND uploadAttemptCount < :maxAttempts THEN 1 END)
                AS pendingUpload,
            COUNT(CASE WHEN uploaded = 0 AND uploadAttemptCount >= :maxAttempts THEN 1 END)
                AS blockedUpload,
            MAX(timestamp) AS lastTimestamp,
            MAX(CASE WHEN uploaded = 1 THEN uploadedAt END) AS lastUploadTime,
            COALESCE(MAX(uploadAttemptCount), 0) AS maxUploadAttempts
        FROM telemetry_samples
    """
    )
    suspend fun getStats(maxAttempts: Int): TelemetryStats
}
