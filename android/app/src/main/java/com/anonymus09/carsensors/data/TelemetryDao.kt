package com.anonymus09.carsensors.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TelemetryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(sample: TelemetrySampleEntity)

    @Query("SELECT COUNT(*) FROM telemetry_samples")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM telemetry_samples WHERE event = 'telemetry_sample'")
    suspend fun getTelemetrySampleCount(): Int

    @Query("SELECT COUNT(*) FROM telemetry_samples WHERE event != 'telemetry_sample'")
    suspend fun getEventCount(): Int

    @Query("SELECT MAX(timestamp) FROM telemetry_samples")
    suspend fun getLastTimestamp(): Long?

    @Query(
        """
        SELECT * FROM telemetry_samples
        WHERE uploaded = 0
        ORDER BY timestamp ASC
        LIMIT :limit
    """
    )
    suspend fun getPendingBatch(limit: Int): List<TelemetrySampleEntity>

    @Query(
        """
        SELECT COUNT(*) FROM telemetry_samples
        WHERE uploaded = 0
    """
    )
    suspend fun getPendingUploadCount(): Int

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

    @Query(
        """
    SELECT MAX(uploadedAt) FROM telemetry_samples
    WHERE uploaded = 1
"""
    )
    suspend fun getLastUploadTime(): Long?


    @Query(
        """
    SELECT MAX(uploadAttemptCount) FROM telemetry_samples
"""
    )
    suspend fun getMaxUploadAttempts(): Int?

    @Query("""SELECT COUNT(*) FROM telemetry_samples WHERE uploaded = 0""")
    fun getPendingCountFlow(): Flow<Int>

    @Query("""
    SELECT MAX(uploadedAt) FROM telemetry_samples
    WHERE uploaded = 1
""")
    fun getLastUploadTimeFlow(): Flow<Long?>

}

