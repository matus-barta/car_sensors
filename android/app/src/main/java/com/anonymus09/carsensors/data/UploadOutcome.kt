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
     * The request could not be accepted at all - a wrong endpoint being the
     * likeliest. This says nothing about the rows themselves, so nothing is
     * counted against them.
     */
    REFUSED,

    /**
     * The server refused this phone's credential.
     *
     * Pairing again fixes it: the identity is unknown to the server, or the
     * token has been rotated and this phone is holding the old one. Nothing is
     * counted against the rows, which have done nothing wrong.
     */
    CREDENTIAL_REJECTED,

    /**
     * The credential is good and the vehicle has been retired.
     *
     * Pairing again will not help - the row is deactivated on the server, and
     * only somebody re-activating it there changes that. The rows waiting on
     * this phone will never be accepted, which is a thing the user has to be
     * told rather than left to discover from a full disk.
     */
    DEVICE_RETIRED,

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

        /** The RFC 6750 error codes `ingest` answers with. */
        private const val ERROR_INVALID_TOKEN = "invalid_token"
        private const val ERROR_INSUFFICIENT_SCOPE = "insufficient_scope"

        private val CHALLENGE_ERROR = Regex("error\\s*=\\s*\"([^\"]*)\"")

        /**
         * The `error` code out of a `WWW-Authenticate: Bearer` challenge.
         *
         * Parsed rather than matched against the whole header, because the
         * challenge also carries a realm and a description in an order the
         * specification does not fix.
         */
        fun bearerChallengeError(challenge: String?): String? {
            val value = challenge ?: return null

            if (!value.trimStart().startsWith("Bearer", ignoreCase = true)) return null

            return CHALLENGE_ERROR.find(value)?.groupValues?.get(1)
        }

        /**
         * Maps a response - its code and its challenge together - onto what
         * should be done about it.
         *
         * `ingest` answers 400 for a body it cannot parse and 413 for one that
         * outgrew its limits. It draws those distinctions on purpose - "send
         * smaller batches" and "this will never be accepted" call for different
         * things - and a wrong endpoint answers 404 for every batch alike,
         * which is why that is separated from the rows being at fault.
         *
         * The authentication answers come from the challenge rather than from
         * the status alone, because 401 and 403 each mean two things. A refused
         * credential is fixed by pairing again; a retired vehicle never will
         * be, and its backlog would otherwise grow for ever with nothing said.
         * Only an explicit error code from our own server is acted on - a bare
         * 401 or 403, which is what a proxy or a wrong address produces, stays
         * [REFUSED] and claims nothing about the pairing.
         *
         * Kept apart from the uploader so it can be decided without a network.
         */
        fun forResponseCode(code: Int, challenge: String? = null): UploadOutcome = when {
            code in 200..299 -> STORED
            code == HttpURLConnection.HTTP_ENTITY_TOO_LARGE -> TOO_LARGE
            code == HttpURLConnection.HTTP_CLIENT_TIMEOUT -> TRANSIENT
            code == HTTP_TOO_MANY_REQUESTS -> TRANSIENT
            code == HttpURLConnection.HTTP_BAD_REQUEST -> MALFORMED
            code == HTTP_UNPROCESSABLE_ENTITY -> MALFORMED

            code == HttpURLConnection.HTTP_UNAUTHORIZED &&
                bearerChallengeError(challenge) == ERROR_INVALID_TOKEN -> CREDENTIAL_REJECTED

            code == HttpURLConnection.HTTP_FORBIDDEN &&
                bearerChallengeError(challenge) == ERROR_INSUFFICIENT_SCOPE -> DEVICE_RETIRED

            code in 400..499 -> REFUSED
            else -> TRANSIENT
        }
    }
}
