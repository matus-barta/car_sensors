package com.anonymus09.carsensors.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anonymus09.carsensors.data.DevicePairing
import com.anonymus09.carsensors.data.devicePairingOrNull

/**
 * What can be done about the pairing, in one place.
 *
 * Reached from a single button beside the identity rather than from a row of
 * three, because pairing is a rare act and the buttons were taking up the
 * screen permanently to say so. Unpairing sits here too, behind its own
 * confirmation - see [ConfirmUnpairDialog].
 */
@Composable
fun PairingOptionsDialog(
    isPaired: Boolean,
    onScan: () -> Unit,
    onEnterCode: () -> Unit,
    onUnpair: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isPaired) "Paired vehicle" else "Pair with a vehicle") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PairingOption(
                    label = "Scan code",
                    description = "Point the camera at the code shown by the web application.",
                    onClick = onScan
                )

                PairingOption(
                    label = "Enter pairing code",
                    description = "Paste the device ID and token instead of scanning.",
                    onClick = onEnterCode
                )

                if (isPaired) {
                    PairingOption(
                        label = "Unpair",
                        description = "Stop uploading. Recording carries on.",
                        onClick = onUnpair,
                        destructive = true
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun PairingOption(
    label: String,
    description: String,
    onClick: () -> Unit,
    destructive: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (destructive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )

        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The fallback for when the camera will not cooperate.
 *
 * Two fields to paste into rather than to type into: an identity and a token
 * together are around ninety characters of random data, and nobody transcribes
 * that correctly. The web application is on the same network, so the phone can
 * open it in a browser and copy the values across.
 */
@Composable
fun ManualPairingDialog(
    onSubmit: (DevicePairing) -> Unit,
    onDismiss: () -> Unit
) {
    var deviceId by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }

    val pairing = devicePairingOrNull(deviceId, token)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Paste the pairing details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Open the web application on this phone and copy both values from the " +
                        "pairing dialog."
                )

                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    label = { Text("Device ID") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = pairing != null,
                onClick = { pairing?.let(onSubmit) }
            ) {
                Text("Pair")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Asks what to do with rows recorded before this phone had an identity.
 *
 * The one moment the answer is knowable. Those rows will go up under whatever
 * identity is adopted next, so after pairing nothing can tell whether they
 * belonged to this vehicle or to a trip somebody else took with a spare phone.
 * Attaching and discarding are both reasonable, and only the person holding it
 * knows which - what must not happen is the question going unasked.
 */
@Composable
fun UntaggedRowsDialog(
    rowCount: Int,
    onKeep: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("Unsent recordings") },
        text = {
            Text(
                "$rowCount recorded ${if (rowCount == 1) "sample has" else "samples have"} " +
                    "not reached a server yet. Upload them as this vehicle, or discard them?\n\n" +
                    "Discarding cannot be undone - these rows are the only copy."
            )
        },
        confirmButton = {
            TextButton(onClick = onKeep) { Text("Upload as this vehicle") }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) { Text("Discard") }
        }
    )
}

/**
 * Confirms unpairing, which is not as recoverable as it sounds.
 *
 * The token was shown once and only its hash was kept, so nothing can put this
 * pairing back - the vehicle has to issue a new one. Worth a question before a
 * single tap costs somebody a trip to the web application.
 */
@Composable
fun ConfirmUnpairDialog(
    hasPendingRows: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unpair this phone?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Recording carries on, but nothing will be uploaded until this phone " +
                        "is paired again."
                )

                Text(
                    "This cannot be undone from here. The token was shown once and only a " +
                        "hash of it was kept, so pairing again means issuing a new one from " +
                        "the web application."
                )

                if (hasPendingRows) {
                    Text(
                        "Recordings waiting to be uploaded are kept. You will be asked what " +
                            "to do with them the next time this phone is paired."
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = "Unpair", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Says that a scanned code was not one of ours. */
@Composable
fun InvalidPairingCodeDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Not a pairing code") },
        text = {
            Text(
                "That code did not carry a device identity and token. Use the code shown " +
                    "by the web application when a vehicle is created or a phone is paired."
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    )
}
