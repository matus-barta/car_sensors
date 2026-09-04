package com.anonymus09.carsensors.util

import java.net.URI

/**
 * Checks and normalises the server address the user types in.
 *
 * The address used to be a compile-time constant, and being wrong about it cost
 * two months of uploads before anyone noticed - so this is deliberately strict
 * about what it accepts and says why when it refuses.
 */
object ServerUrl {

    sealed interface Result {
        /** [normalized] is what should be stored: trimmed, without a trailing slash. */
        data class Valid(val normalized: String) : Result

        data class Invalid(val reason: String) : Result
    }

    fun validate(input: String, allowCleartext: Boolean): Result {
        val trimmed = input.trim().trimEnd('/')

        if (trimmed.isEmpty()) {
            return Result.Invalid("Enter a server address")
        }

        val uri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return Result.Invalid("Not a valid address")
        }

        val scheme = uri.scheme?.lowercase()

        if (scheme != "http" && scheme != "https") {
            return Result.Invalid("Start with http:// or https://")
        }

        if (uri.host.isNullOrBlank()) {
            return Result.Invalid("Missing a host name")
        }

        if (!uri.path.isNullOrEmpty()) {
            return Result.Invalid("Enter the base address only, without a path")
        }

        /*
         * Cleartext is a testing affordance. The device id doubles as the
         * credential, so over plain HTTP anyone on the network can read it and
         * then post telemetry indistinguishable from this device's own.
         */
        if (scheme == "http" && !allowCleartext) {
            return Result.Invalid("This build requires https://")
        }

        return Result.Valid(trimmed)
    }
}
