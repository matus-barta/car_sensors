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
    MALFORMED,

    /**
     * The phone has no pairing, so there is nothing to authenticate with.
     *
     * Never reaches the network. An upload without a credential is a certain
     * rejection, and counting it against the rows would quarantine perfectly
     * good data for the app's own lack of setup.
     */
    NOT_PAIRED;

    companion object {
        /** No constants for these two in [HttpURLConnection]. */
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private const val HTTP_UNPROCESSABLE_ENTITY = 422

        /**
         * Maps a response code onto what should be done about it.
         *
         * `ingest` answers 400 for a body it cannot parse and 413 for one that
         * outgrew its limits. It draws those distinctions on purpose - "send
         * smaller batches" and "this will never be accepted" call for different
         * things - and a wrong endpoint answers 404 for every batch alike,
         * which is why that is separated from the rows being at fault.
         *
         * The authentication answers are flattened here, and should not stay
         * that way. Since the bearer token landed, `ingest` follows RFC 6750:
         * 401 with `error="invalid_token"` means the credential was refused and
         * re-pairing fixes it, 401 with no error code means none was sent, and
         * 403 with `error="insufficient_scope"` means the token is good and the
         * vehicle has been retired - which re-pairing will never fix. All three
         * collapse into [REFUSED] below, so a permanently banned device is
         * indistinguishable from a mistyped address and its backlog grows for
         * ever. See `todo.md`.
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
