package com.anonymus09.carsensors.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anonymus09.carsensors.LoggerState
import com.anonymus09.carsensors.MainUiState
import com.anonymus09.carsensors.TelemetryLocationStatus
import com.anonymus09.carsensors.data.PowerState
import com.anonymus09.carsensors.data.PowerTier
import com.anonymus09.carsensors.data.ServerHealth
import com.anonymus09.carsensors.data.TelemetryStorage
import com.anonymus09.carsensors.util.AppConfig.DB_STATS_REFRESH_RATE
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_MAX_ATTEMPTS
import com.anonymus09.carsensors.util.ServerUrl

/**
 * The whole screen, given its state and told nothing about where it came from.
 *
 * Ordered by what someone opening the app needs first. The state used to sit
 * below three screens of settings, which is how a phone that recorded nothing
 * went unnoticed until somebody went looking for the data.
 */
@Composable
fun CarSensorsScreen(
    state: MainUiState,
    locationStatus: TelemetryLocationStatus,
    serverHealth: ServerHealth,
    onToggleLogging: () -> Unit,
    onWakeOnMotionChange: (Boolean) -> Unit,
    onAutoStartOnBootChange: (Boolean) -> Unit,
    onRecordOnBatteryChange: (Boolean) -> Unit,
    onUploadOnBatteryChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onLiveUploadChange: (Boolean) -> Unit,
    onServerBaseUrlSave: (String) -> Unit,
    onCheckServer: () -> Unit,
    onForceUpload: () -> Unit,
    onRestartService: () -> Unit,
    allowCleartext: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(WindowInsets.systemBars.asPaddingValues())
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatusSection(
            state = state,
            locationStatus = locationStatus,
            onToggleLogging = onToggleLogging
        )

        SectionDivider()

        SetupSection(
            state = state,
            serverHealth = serverHealth,
            allowCleartext = allowCleartext,
            onServerBaseUrlSave = onServerBaseUrlSave,
            onCheckServer = onCheckServer,
            onWakeOnMotionChange = onWakeOnMotionChange,
            onAutoStartOnBootChange = onAutoStartOnBootChange,
            onRecordOnBatteryChange = onRecordOnBatteryChange,
            onUploadOnBatteryChange = onUploadOnBatteryChange,
            onWifiOnlyChange = onWifiOnlyChange,
            onLiveUploadChange = onLiveUploadChange
        )

        SectionDivider()

        DiagnosticsSection(
            state = state,
            onForceUpload = onForceUpload,
            onRestartService = onRestartService
        )
    }
}

// ----------------------------------------------------
// Status
// ----------------------------------------------------

@Composable
private fun StatusSection(
    state: MainUiState,
    locationStatus: TelemetryLocationStatus,
    onToggleLogging: () -> Unit
) {
    val running = state.loggerState != LoggerState.OFF

    Text(text = "Car Sensors Logger", style = MaterialTheme.typography.headlineMedium)

    Text(
        text = when (state.loggerState) {
            LoggerState.OFF -> "STOPPED"
            LoggerState.ARMED -> "WAITING FOR MOVEMENT"
            LoggerState.RECORDING -> "RECORDING"
        },
        style = MaterialTheme.typography.headlineSmall,
        color = when (state.loggerState) {
            LoggerState.RECORDING -> MaterialTheme.colorScheme.primary
            LoggerState.ARMED -> MaterialTheme.colorScheme.tertiary
            LoggerState.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    )

    if (state.loggerState == LoggerState.ARMED) {
        Muted(
            "Parked. Sensors and GPS are off; the motion sensor will start " +
                "recording as soon as the vehicle moves."
        )
    }

    if (state.power.tier != PowerTier.FULL) {
        Text(
            text = "Battery saving: " + when (state.power.tier) {
                PowerTier.NO_UPLOAD -> "uploads held until the phone is charged"
                PowerTier.REDUCED_RATE -> "recording less often"
                PowerTier.LOCATION_ONLY -> "location only, other sensors off"
                PowerTier.PAUSED -> "recording stopped until charged"
                PowerTier.FULL -> ""
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }

    Button(
        onClick = onToggleLogging,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (running) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    ) {
        Text(text = if (running) "Stop logging" else "Start logging")
    }

    GpsStatus(locationStatus)
    PowerStatus(state.power)

    if (state.settings.liveUploadEnabled) {
        LiveUploadNote(charging = state.power.charging, wifiOnly = state.settings.wifiOnly)
    }
}

@Composable
private fun GpsStatus(locationStatus: TelemetryLocationStatus) {
    if (!locationStatus.hasFix) {
        Text(
            text = "GPS: waiting for a fix",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        return
    }

    Text(
        text = "GPS: %.5f, %.5f".format(locationStatus.latitude, locationStatus.longitude),
        style = MaterialTheme.typography.bodyMedium
    )
    Muted(
        "${locationStatus.speedKmh} km/h - ${locationStatus.provider} - " +
            "${locationStatus.accuracy?.toInt()} m"
    )
}

@Composable
private fun PowerStatus(power: PowerState) {
    val level = power.levelPercent?.let { " - $it%" } ?: ""

    Text(
        text = if (power.charging) {
            "Power: charging (${power.source})$level"
        } else {
            "Power: on battery$level"
        },
        style = MaterialTheme.typography.bodyMedium
    )
}

/**
 * Whether live upload is actually running right now.
 *
 * What the setting does is on the switch itself; this is the part that changes
 * underneath it, so someone who turns it on in an unplugged car can tell the
 * difference between waiting and broken.
 */
@Composable
private fun LiveUploadNote(charging: Boolean, wifiOnly: Boolean) {
    val (message, highlighted) = when {
        charging && wifiOnly ->
            "Live upload active on Wi-Fi." to true

        charging -> "Live upload active." to true

        else -> "Live upload waiting for power; batches continue meanwhile." to false
    }

    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = if (highlighted) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

// ----------------------------------------------------
// Setup
// ----------------------------------------------------

@Composable
private fun SetupSection(
    state: MainUiState,
    serverHealth: ServerHealth,
    allowCleartext: Boolean,
    onServerBaseUrlSave: (String) -> Unit,
    onCheckServer: () -> Unit,
    onWakeOnMotionChange: (Boolean) -> Unit,
    onAutoStartOnBootChange: (Boolean) -> Unit,
    onRecordOnBatteryChange: (Boolean) -> Unit,
    onUploadOnBatteryChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onLiveUploadChange: (Boolean) -> Unit
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

    Text(text = "Device id", style = MaterialTheme.typography.labelMedium)
    Text(text = state.deviceId, style = MaterialTheme.typography.bodySmall)

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
                        "until you open it again."
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
    onCheck: () -> Unit
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
                    ?: if (allowCleartext) "http:// is accepted in this build only" else "https:// only"
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

        OutlinedButton(onClick = onCheck, modifier = Modifier.weight(1f)) {
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
        ServerHealth.Ok -> "Server reachable, this device accepted" to MaterialTheme.colorScheme.primary
        ServerHealth.Unreachable ->
            "Nothing answered - check the address, port and network" to MaterialTheme.colorScheme.error
        ServerHealth.NotTheApi ->
            "Answered, but this is not the telemetry API - check the address" to
                MaterialTheme.colorScheme.error
        ServerHealth.DeviceUnknown ->
            "Server reachable, but it does not know this device - register it" to
                MaterialTheme.colorScheme.error
        ServerHealth.DeviceDeactivated ->
            "Server reachable, but this device has been deactivated" to
                MaterialTheme.colorScheme.error
        is ServerHealth.ServerFault ->
            "Server answered ${serverHealth.code}" to MaterialTheme.colorScheme.error
    }

    Text(text = message, style = MaterialTheme.typography.bodySmall, color = colour)
}

// ----------------------------------------------------
// Diagnostics
// ----------------------------------------------------

/**
 * Everything worth having when something is wrong and nothing worth reading
 * when it is not, so it stays folded away by default.
 */
@Composable
private fun DiagnosticsSection(
    state: MainUiState,
    onForceUpload: () -> Unit,
    onRestartService: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Diagnostics",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleLarge
        )
        Text(text = if (expanded) "Hide" else "Show", style = MaterialTheme.typography.labelLarge)
    }

    if (!expanded) return

    val storage = state.storage
    val stats = storage.stats

    Muted("Updates every $DB_STATS_REFRESH_RATE seconds")

    LabelledValue("Database file", storage.databasePath, small = true)
    LabelledValue("Database size", formatBytes(storage.databaseSizeBytes))
    LabelledValue("Total rows", stats.totalRows.toString())
    LabelledValue("Telemetry samples", stats.telemetryRows.toString())
    LabelledValue("Event rows", stats.eventRows.toString())
    LabelledValue("Last stored timestamp", formatTimestamp(stats.lastTimestamp, "N/A"))

    Spacer(modifier = Modifier.height(4.dp))

    LabelledValue("Pending upload rows", stats.pendingUpload.toString())
    LabelledValue("Last successful upload", formatTimestamp(stats.lastUploadTime, "Never"))
    LabelledValue("Max upload attempts", stats.maxUploadAttempts.toString())

    if (stats.blockedUpload > 0) {
        Text(
            text = "Blocked rows (refused $UPLOAD_MAX_ATTEMPTS times): ${stats.blockedUpload}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }

    Button(
        onClick = onForceUpload,
        enabled = stats.pendingUpload > 0,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (stats.pendingUpload > 0) "Force upload now" else "Nothing to upload")
    }

    Muted(
        "Restarting tears the sensor, location and power listeners down and registers " +
            "them again, without losing anything already recorded."
    )

    OutlinedButton(onClick = onRestartService, modifier = Modifier.fillMaxWidth()) {
        Text("Restart logging service")
    }
}

// ----------------------------------------------------
// Shared pieces
// ----------------------------------------------------

@Composable
private fun SectionDivider() = HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

@Composable
private fun Muted(text: String) = Text(
    text = text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun LabelledValue(label: String, value: String, small: Boolean = false) {
    Text(text = label, style = MaterialTheme.typography.labelMedium)
    Text(
        text = value,
        style = if (small) {
            MaterialTheme.typography.bodySmall
        } else {
            MaterialTheme.typography.bodyMedium
        }
    )
}

/**
 * A switch with the sentence that says what it does.
 *
 * The description is required rather than optional on purpose: these options
 * interact - motion, power and network all gate each other - and a bare label
 * is not enough to remember which does what.
 */
@Composable
private fun SettingRow(
    title: String,
    description: AnnotatedString,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    /*
     * Six options that gate each other need more explanation than fits on a
     * phone at once, so each is trimmed to a line and opens on a tap. The text
     * is kept whole rather than shortened to fit, because the parts that would
     * be cut are the interactions that make an option confusing.
     */
    var expanded by rememberSaveable(title) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { expanded = !expanded }
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
