package com.anonymus09.carsensors.ui

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
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import com.anonymus09.carsensors.LoggerState
import com.anonymus09.carsensors.MainUiState
import com.anonymus09.carsensors.TelemetryLocationStatus
import com.anonymus09.carsensors.data.PowerState
import com.anonymus09.carsensors.data.TelemetryStorage
import com.anonymus09.carsensors.util.AppConfig.DB_STATS_REFRESH_RATE
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_MAX_ATTEMPTS
import com.anonymus09.carsensors.util.ServerUrl

/**
 * The whole screen, given its state and told nothing about where that came
 * from. It was a private method on MainActivity, closing over the activity for
 * settings, permissions and database reads alike, which left it impossible to
 * preview or test and hard to reason about.
 */
@Composable
fun CarSensorsScreen(
    state: MainUiState,
    locationStatus: TelemetryLocationStatus,
    onAutoStartOnBootChange: (Boolean) -> Unit,
    onRecordOnBatteryChange: (Boolean) -> Unit,
    onUploadOnBatteryChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onLiveUploadChange: (Boolean) -> Unit,
    onToggleLogging: () -> Unit,
    onWakeOnMotionChange: (Boolean) -> Unit,
    onForceUpload: () -> Unit,
    onRestartService: () -> Unit,
    onServerBaseUrlSave: (String) -> Unit,
    allowCleartext: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(WindowInsets.systemBars.asPaddingValues())
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Car Sensors Logger",
            style = MaterialTheme.typography.headlineMedium
        )

        Text(
            text = "Designed for continuous background logging with foreground service.",
            style = MaterialTheme.typography.bodyMedium
        )

        Text(text = "Device id:", style = MaterialTheme.typography.titleMedium)
        Text(text = state.deviceId, style = MaterialTheme.typography.bodySmall)

        Spacer(modifier = Modifier.height(8.dp))

        SettingRow(
            title = "Wake on motion",
            description = "Waits while the vehicle is parked and starts recording " +
                "when it moves. GPS then has to confirm it is really travelling, so " +
                "picking the phone up does not start a journey.",
            checked = state.settings.wakeOnMotion,
            onCheckedChange = onWakeOnMotionChange
        )

        SettingRow(
            title = "Auto-start on boot",
            description = "Puts the logger back the way you left it after the phone " +
                "reboots, so a phone living in the car recovers on its own.",
            checked = state.settings.autoStartOnBoot,
            onCheckedChange = onAutoStartOnBootChange
        )

        SettingRow(
            title = "Record on battery",
            description = "Keeps recording after the car's power is cut. Off means " +
                "it goes back to waiting instead, so a parked car cannot flatten " +
                "the phone.",
            checked = state.settings.recordOnBattery,
            onCheckedChange = onRecordOnBatteryChange
        )

        SettingRow(
            title = "Upload on battery",
            description = "Lets batched uploads run when the phone is not on power. " +
                "Live upload never does, whatever this says.",
            checked = state.settings.uploadOnBattery,
            onCheckedChange = onUploadOnBatteryChange
        )

        SettingRow(
            title = "Wi-Fi only",
            description = "Uploads only over an unmetered network. Off allows mobile " +
                "data, for a car that never parks near Wi-Fi. Applies to live and " +
                "batched uploads alike.",
            checked = state.settings.wifiOnly,
            onCheckedChange = onWifiOnlyChange
        )

        SettingRow(
            title = "Live upload",
            description = "Sends each new position the moment it changes, instead of " +
                "waiting for a batch. Only runs while on power.",
            checked = state.settings.liveUploadEnabled,
            onCheckedChange = onLiveUploadChange
        )

        LiveUploadNote(
            enabled = state.settings.liveUploadEnabled,
            charging = state.power.charging,
            wifiOnly = state.settings.wifiOnly
        )

        Spacer(modifier = Modifier.height(8.dp))

        LoggingSection(loggerState = state.loggerState, onToggleLogging = onToggleLogging)

        GpsSection(locationStatus = locationStatus)

        Spacer(modifier = Modifier.height(8.dp))

        PowerSection(power = state.power)

        Spacer(modifier = Modifier.height(8.dp))

        StorageSection(storage = state.storage)

        Spacer(modifier = Modifier.height(8.dp))

        ServerSection(
            serverBaseUrl = state.settings.serverBaseUrl,
            uploadUrl = state.settings.uploadUrl,
            allowCleartext = allowCleartext,
            onSave = onServerBaseUrlSave
        )

        Spacer(modifier = Modifier.height(8.dp))

        UploadSection(storage = state.storage, onForceUpload = onForceUpload)

        Spacer(modifier = Modifier.height(8.dp))

        MaintenanceSection(onRestartService = onRestartService)
    }
}

/**
 * Whether live upload is actually running right now.
 *
 * What the setting *does* is on the switch itself; this is the part that
 * changes underneath it, so that someone who turns it on in an unplugged car
 * can tell the difference between waiting and broken.
 */
@Composable
private fun LiveUploadNote(enabled: Boolean, charging: Boolean, wifiOnly: Boolean) {
    if (!enabled) return

    val (message, highlighted) = when {
        charging && wifiOnly ->
            "Active on Wi-Fi - sending each new position as it changes." to true

        charging -> "Active - sending each new position as it changes." to true

        else -> (
            "Waiting for power. Everything is still recorded and uploaded in " +
                "batches meanwhile."
            ) to false
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

/**
 * The button switches the logger on and off; what it does once on is the
 * logger's own business.
 *
 * Showing all three states matters more than it looks. With "wake on motion"
 * on, pressing start in a parked car leads to ARMED and nothing is recorded
 * until it moves - which reads as a broken app unless the screen says so.
 */
@Composable
private fun LoggingSection(loggerState: LoggerState, onToggleLogging: () -> Unit) {
    val running = loggerState != LoggerState.OFF

    Text(
        text = "Logging state: " + when (loggerState) {
            LoggerState.OFF -> "STOPPED"
            LoggerState.ARMED -> "WAITING FOR MOVEMENT"
            LoggerState.RECORDING -> "RECORDING"
        },
        style = MaterialTheme.typography.titleMedium
    )

    if (loggerState == LoggerState.ARMED) {
        Text(
            text = "Parked. Sensors and GPS are off; the motion sensor will start " +
                "recording as soon as the vehicle moves.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
}

@Composable
private fun GpsSection(locationStatus: TelemetryLocationStatus) {
    Text(text = "GPS status", style = MaterialTheme.typography.titleMedium)

    if (!locationStatus.hasFix) {
        Text(
            text = "Waiting for GPS fix...",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
        return
    }

    Text(
        text = "Lat: %.5f, Lon: %.5f".format(locationStatus.latitude, locationStatus.longitude),
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        text = "Speed: ${locationStatus.speedKmh} km/h",
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        text = "Provider: ${locationStatus.provider}",
        style = MaterialTheme.typography.bodyMedium
    )
    Text(
        text = "Accuracy: ${locationStatus.accuracy?.toInt()} m",
        style = MaterialTheme.typography.bodyMedium
    )
}

@Composable
private fun PowerSection(power: PowerState) {
    Text(
        text = "Charging now: ${if (power.charging) "YES" else "NO"}",
        style = MaterialTheme.typography.titleMedium
    )
    Text(
        text = "Power source: ${power.source}",
        style = MaterialTheme.typography.labelMedium
    )
}

@Composable
private fun StorageSection(storage: TelemetryStorage) {
    val stats = storage.stats

    Text(text = "Database storage", style = MaterialTheme.typography.titleMedium)
    Text(
        text = "Updates every $DB_STATS_REFRESH_RATE seconds",
        style = MaterialTheme.typography.bodySmall
    )

    LabelledValue("Database file:", storage.databasePath, valueIsPath = true)
    LabelledValue("Database size:", formatBytes(storage.databaseSizeBytes))
    LabelledValue("Database exists:", if (storage.databaseExists) "Yes" else "No")
    LabelledValue("Total rows:", stats.totalRows.toString())
    LabelledValue("Telemetry samples:", stats.telemetryRows.toString())
    LabelledValue("Event rows:", stats.eventRows.toString())
    LabelledValue("Last stored timestamp:", formatTimestamp(stats.lastTimestamp, "N/A"))
}

@Composable
private fun UploadSection(storage: TelemetryStorage, onForceUpload: () -> Unit) {
    val stats = storage.stats

    Text(text = "Upload status", style = MaterialTheme.typography.titleMedium)
    LabelledValue("Pending upload rows:", stats.pendingUpload.toString())

    /*
     * Only worth showing once there is something to show: rows the server has
     * refused often enough that they are no longer retried, and are therefore
     * no longer holding up everything queued behind them.
     */
    if (stats.blockedUpload > 0) {
        Text(
            text = "Blocked rows (refused $UPLOAD_MAX_ATTEMPTS times):",
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text = stats.blockedUpload.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
        )
    }
    LabelledValue("Last successful upload:", formatTimestamp(stats.lastUploadTime, "Never"))
    LabelledValue("Max upload attempts:", stats.maxUploadAttempts.toString())

    Button(
        onClick = onForceUpload,
        enabled = stats.pendingUpload > 0,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(if (stats.pendingUpload > 0) "Force upload now" else "Nothing to upload")
    }
}

/**
 * A way out of a state the app should never have reached.
 *
 * Deliberately always enabled, unlike "Force upload now": a service wedged
 * badly enough to need this is not to be trusted about its own state.
 */
@Composable
private fun MaintenanceSection(onRestartService: () -> Unit) {
    Text(text = "Maintenance", style = MaterialTheme.typography.titleMedium)

    Text(
        text = "Tears the sensor, location and power listeners down and registers " +
            "them again, without losing anything already recorded.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Button(onClick = onRestartService, modifier = Modifier.fillMaxWidth()) {
        Text("Restart logging service")
    }
}

/**
 * Where telemetry is sent.
 *
 * The draft is local until saved so that a half-typed address is never stored,
 * and it is keyed on the persisted value so an edit made elsewhere replaces it
 * rather than being silently overwritten.
 */
@Composable
private fun ServerSection(
    serverBaseUrl: String,
    uploadUrl: String,
    allowCleartext: Boolean,
    onSave: (String) -> Unit
) {
    var draft by rememberSaveable(serverBaseUrl) { mutableStateOf(serverBaseUrl) }

    val validation = remember(draft, allowCleartext) {
        ServerUrl.validate(draft, allowCleartext)
    }
    val error = validation as? ServerUrl.Result.Invalid
    val valid = validation as? ServerUrl.Result.Valid

    Text(text = "Server configuration", style = MaterialTheme.typography.titleMedium)

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

    Button(
        onClick = { valid?.let { onSave(it.normalized) } },
        enabled = valid != null && valid.normalized != serverBaseUrl,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text("Save server address")
    }

    LabelledValue("Upload endpoint:", uploadUrl, valueIsPath = true)
}

/** A label over its value, the shape most of this screen is made of. */
@Composable
private fun LabelledValue(label: String, value: String, valueIsPath: Boolean = false) {
    Text(text = label, style = MaterialTheme.typography.labelMedium)
    Text(
        text = value,
        style = if (valueIsPath) {
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
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
