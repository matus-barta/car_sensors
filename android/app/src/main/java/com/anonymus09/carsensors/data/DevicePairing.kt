package com.anonymus09.carsensors.data

import android.util.Log
import org.json.JSONObject

/**
 * What the phone needs to upload as a particular vehicle.
 *
 * Two values doing different jobs. [deviceId] names the vehicle and is not a
 * secret - it appears on this screen and in the server's logs. [token] is the
 * secret, and it is what lets a credential be withdrawn without the vehicle
 * losing the identity every stored sample is filed under.
 */
data class DevicePairing(
    val deviceId: String,
    val token: String
)

/**
 * The payload version this build understands.
 *
 * The server writing the code ships separately from the app reading it, so a
 * future change needs something to branch on rather than a guess at the shape.
 */
private const val SUPPORTED_PAYLOAD_VERSION = 1

private const val TAG = "DevicePairing"

/**
 * Reads a pairing out of a scanned QR code.
 *
 * Returns null for anything that is not one, which includes a perfectly valid
 * QR code that happens to encode something else - pointing the camera at a
 * parcel label should say "that is not a pairing code" rather than pair the
 * phone with nonsense.
 */
fun parsePairingPayload(raw: String): DevicePairing? {
    val json = try {
        JSONObject(raw.trim())
    } catch (e: org.json.JSONException) {
        Log.w(TAG, "Scanned code is not a pairing payload", e)

        return null
    }

    val version = json.optInt("v", -1)

    if (version != SUPPORTED_PAYLOAD_VERSION) {
        Log.w(TAG, "Pairing payload version $version is not supported")

        return null
    }

    return devicePairingOrNull(
        deviceId = json.optString("deviceId"),
        token = json.optString("token")
    )
}

/**
 * A pairing from two values entered separately, or null if either is missing.
 *
 * This is the copy-and-paste path, for when the camera will not cooperate.
 * Neither value is validated beyond being present: the server decides what a
 * good identity looks like, and guessing here would only reject credentials a
 * later version of `www` might legitimately issue.
 */
fun devicePairingOrNull(deviceId: String?, token: String?): DevicePairing? {
    val trimmedId = deviceId?.trim().orEmpty()
    val trimmedToken = token?.trim().orEmpty()

    if (trimmedId.isEmpty() || trimmedToken.isEmpty()) return null

    return DevicePairing(deviceId = trimmedId, token = trimmedToken)
}
