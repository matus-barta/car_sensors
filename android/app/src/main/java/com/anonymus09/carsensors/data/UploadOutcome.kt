package com.anonymus09.carsensors.data

import java.net.HttpURLConnection

/** What the server made of a batch, and so what the caller should do next. */
enum class UploadOutcome {
    /** Stored. The rows are marked uploaded. */
    STORED,

    /** The server or the network is having a bad time; the rows will do later. */
    TRANSIENT,

    /** Too big to accept. The same rows may fit in a smaller batch. */
    TOO_LARGE,

    /**
     * The request could not be accepted at all - wrong endpoint, unknown or
     * deactivated device. This says nothing about the rows themselves, so
     * nothing is counted against them.
     */
    REFUSED,

    /** The server understood the request and rejected this body. */
    MALFORMED;

    companion object {
        /** No constants for these two in [HttpURLConnection]. */
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_UNPROCESSABLE_ENTITY = 422

        /**
         * Maps a response code onto what should be done about it.
         *
         * `ingest` answers 401 for a device it does not know, 403 for one that
         * has been deactivated, 400 for a body it cannot parse and 413 for one
         * that outgrew its limits. It draws those distinctions on purpose -
         * "send smaller batches" and "this will never be accepted" call for
         * different things - and a wrong endpoint answers 404 for every batch
         * alike, which is why that is separated from the rows being at fault.
         *
         * Kept apart from the uploader so it can be decided without a network.
         */
        fun forResponseCode(code: Int): UploadOutcome = when {
            code in 200..299 -> STORED
            code == HttpURLConnection.HTTP_ENTITY_TOO_LARGE -> TOO_LARGE
            code == HttpURLConnection.HTTP_CLIENT_TIMEOUT -> TRANSIENT
            code == HTTP_TOO_MANY_REQUESTS -> TRANSIENT
            code == HttpURLConnection.HTTP_BAD_REQUEST -> MALFORMED
            code == HTTP_UNPROCESSABLE_ENTITY -> MALFORMED
            code in 400..499 -> REFUSED
            else -> TRANSIENT
        }
    }
}
