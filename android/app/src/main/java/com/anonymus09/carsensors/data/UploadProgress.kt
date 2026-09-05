package com.anonymus09.carsensors.data

/**
 * What one pass over the table says about rows still owed to the server.
 *
 * Asked for as one query rather than three: the service reads this on a
 * throttle while recording, over a table that grows by 172,800 rows a day, and
 * the count it already needed to decide when to wake the uploader is one of
 * the three columns.
 */
data class UploadProgress(
    val pendingRows: Int = 0,
    val lastUploadedAt: Long? = null,
    val oldestPendingAt: Long? = null
) {

    /**
     * How long telemetry has been waiting to reach the server, or null when
     * none of it is waiting.
     *
     * A successful upload proves the path works, so it is the better clock
     * whenever there has been one. Uploaded rows are pruned after
     * [com.anonymus09.carsensors.util.AppConfig.UPLOADED_ROW_RETENTION_MS]
     * though, so it goes missing during exactly the outage worth warning
     * about - and the oldest row still waiting then answers the same question
     * asked of the data rather than of the transport. Without that fallback a
     * long enough outage would erase its own evidence and read as a device
     * that had simply never uploaded.
     */
    fun waitingMs(now: Long): Long? {
        if (pendingRows == 0) return null

        val since = lastUploadedAt ?: oldestPendingAt ?: return null

        // A clock that has gone backwards should read as no wait, not a negative one.
        return (now - since).coerceAtLeast(0)
    }
}
