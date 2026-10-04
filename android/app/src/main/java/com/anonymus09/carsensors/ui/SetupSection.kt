package com.anonymus09.carsensors.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.anonymus09.carsensors.MainUiState
import com.anonymus09.carsensors.data.DevicePairing
import com.anonymus09.carsensors.data.PairingRejection
import com.anonymus09.carsensors.data.ServerHealth
import com.anonymus09.carsensors.util.ServerUrl

@Composable
internal fun SetupSection(
    state: MainUiState,
    serverHealth: ServerHealth,
    allowCleartext: Boolean,
    onServerBaseUrlSave: (String) -> Unit,
    onCheckServer: (String) -> Unit,
    onWakeOnMotionChange: (Boolean) -> Unit,
    onAutoStartOnBootChange: (Boolean) -> Unit,
    onRecordOnBatteryChange: (Boolean) -> Unit,
    onUploadOnBatteryChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onLiveUploadChange: (Boolean) -> Unit,
    onManagePairing: () -> Unit
) {
    Text(text = "Setup", style = MaterialTheme.typography.titleLarge)

    ServerAddress(
        serverBaseUrl = state.settings.serverBaseUrl,
        uploadUrl = state.settings.uploadUrl,
        serverHealth = serverHealth,
        allowCleartext = allowCleartext,
        onSave = onServerBaseUrlSave,
        onCheck = onCheckServer
    )

    PairingStatus(
        pairing = state.pairing,
        rejection = state.pairingRejection,
        onManage = onManagePairing
    )

    Spacer(modifier = Modifier.height(4.dp))

    SettingRow(
        title = "Wake on motion",
        description = AnnotatedString(
            "Waits while parked and starts when the car moves. GPS has to confirm " +
                "real travel, so picking the phone up does not start a journey."
        ),
        checked = state.settings.wakeOnMotion,
        onCheckedChange = onWakeOnMotionChange
    )

    SettingRow(
        title = "Auto-start on boot",
        description = buildAnnotatedString {
            append("Restores the logger after a reboot. ")

            /*
             * Set apart because it is the one case the setting cannot cover, and
             * the symptom - a phone that recorded nothing for a week - looks
             * exactly like the app being broken.
             */
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                append(
                    "Force-stopping the app in Android settings disables this " +
                        "until you open it again. Stopping it from Active apps in the " +
                        "notification panel ends logging until the next restart."
                )
            }
        },
        checked = state.settings.autoStartOnBoot,
        onCheckedChange = onAutoStartOnBootChange
    )

    SettingRow(
        title = "Record on battery",
        description = AnnotatedString(
            "Keeps recording once the car's power is cut. Off waits instead, so a " +
                "parked car cannot flatten the phone."
        ),
        checked = state.settings.recordOnBattery,
        onCheckedChange = onRecordOnBatteryChange
    )

    SettingRow(
        title = "Upload on battery",
        description = AnnotatedString(
            "Allows batched uploads off power. Live upload never runs on battery."
        ),
        checked = state.settings.uploadOnBattery,
        onCheckedChange = onUploadOnBatteryChange
    )

    SettingRow(
        title = "Wi-Fi only",
        description = AnnotatedString(
            "Uploads only on unmetered networks. Off allows mobile data. Applies to " +
                "live and batched uploads."
        ),
        checked = state.settings.wifiOnly,
        onCheckedChange = onWifiOnlyChange
    )

    SettingRow(
        title = "Live upload",
        description = AnnotatedString(
            "Sends each new position as it changes rather than in batches. Only " +
                "while on power."
        ),
        checked = state.settings.liveUploadEnabled,
        onCheckedChange = onLiveUploadChange
    )
}

/**
 * Which vehicle this phone is paired with, and how to change it.
 *
 * Pairing is deliberately repeatable rather than a one-off. A phone moves
 * between cars, a token is withdrawn, an app is reinstalled - the flow that
 * assigns an identity is the flow that reassigns one, so it stays reachable
 * whether or not there is a pairing already.
 */
@Composable
private fun PairingStatus(
    pairing: DevicePairing?,
    rejection: PairingRejection?,
    onManage: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Paired vehicle", style = MaterialTheme.typography.labelMedium)

            if (pairing == null) {
                Text(
                    text = "Not paired. Recording still works and the rows are kept, but " +
                        "nothing is uploaded.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(text = pairing.deviceId, style = MaterialTheme.typography.bodySmall)
            }

            /*
             * Said here rather than left in a log, and said as a remedy rather
             * than as a status: one of these is fixed by pairing again and the
             * other never will be, which is the whole reason they are told
             * apart.
             */
            if (rejection != null) {
                Text(
                    text = when (rejection) {
                        PairingRejection.CREDENTIAL_REJECTED ->
                            "The server refused this credential. Pair the phone again."

                        PairingRejection.DEVICE_RETIRED ->
                            "This vehicle has been retired on the server. Nothing will be " +
                                "uploaded again until it is re-activated there."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        IconButton(onClick = onManage) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = if (pairing == null) {
                    "Pair with a vehicle"
                } else {
                    "Change the paired vehicle"
                }
            )
        }
    }
}

/**
 * The address, and whether anything is actually there.
 *
 * The draft is local until saved so a half-typed address is never stored, and
 * it is keyed on the persisted value so an edit made elsewhere replaces it.
 */
@Composable
private fun ServerAddress(
    serverBaseUrl: String,
    uploadUrl: String,
    serverHealth: ServerHealth,
    allowCleartext: Boolean,
    onSave: (String) -> Unit,
    onCheck: (String) -> Unit
) {
    var draft by rememberSaveable(serverBaseUrl) { mutableStateOf(serverBaseUrl) }

    val validation = remember(draft, allowCleartext) {
        ServerUrl.validate(draft, allowCleartext)
    }
    val error = validation as? ServerUrl.Result.Invalid
    val valid = validation as? ServerUrl.Result.Valid

    OutlinedTextField(
        value = draft,
        onValueChange = { draft = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Server address") },
        singleLine = true,
        isError = error != null,
        supportingText = {
            Text(
                text = error?.reason
                    ?: if (allowCleartext) {
                        "http:// is accepted in this build only"
                    } else {
                        "https:// only"
                    }
            )
        }
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = { valid?.let { onSave(it.normalized) } },
            enabled = valid != null && valid.normalized != serverBaseUrl,
            modifier = Modifier.weight(1f)
        ) {
            Text("Save")
        }

        /*
         * Tests what is in the field rather than what was last saved. Checking
         * the saved address after editing answers a question nobody asked, and
         * the check costs a couple of requests - so there is no reason to make
         * somebody save a value to find out whether it works.
         */
        OutlinedButton(
            onClick = { valid?.let { onCheck(it.normalized) } },
            enabled = valid != null,
            modifier = Modifier.weight(1f)
        ) {
            Text("Test connection")
        }
    }

    ServerHealthLine(serverHealth)
    Muted(uploadUrl)
}

@Composable
private fun ServerHealthLine(serverHealth: ServerHealth) {
    val (message, colour) = when (serverHealth) {
        ServerHealth.Unknown -> "Not checked yet" to MaterialTheme.colorScheme.onSurfaceVariant
        ServerHealth.Checking -> "Checking…" to MaterialTheme.colorScheme.onSurfaceVariant
        ServerHealth.Ok ->
            "Server reachable, this device accepted" to MaterialTheme.colorScheme.primary
        ServerHealth.Unreachable ->
            "Nothing answered - check the address, port and network" to
                MaterialTheme.colorScheme.error
        ServerHealth.NotTheApi ->
            "Answered, but this is not the telemetry API - check the address" to
                MaterialTheme.colorScheme.error
        ServerHealth.NotPaired ->
            "Not paired with a vehicle yet" to MaterialTheme.colorScheme.onSurfaceVariant
        ServerHealth.DeviceUnknown ->
            "Server reachable, but it rejected this device's credential - pair it again" to
                MaterialTheme.colorScheme.error
        ServerHealth.DeviceDeactivated ->
            "Server reachable, but this device has been deactivated" to
                MaterialTheme.colorScheme.error
        is ServerHealth.ServerFault ->
            "Server answered ${serverHealth.code}" to MaterialTheme.colorScheme.error
    }

    Text(text = message, style = MaterialTheme.typography.bodySmall, color = colour)
}
