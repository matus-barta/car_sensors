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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anonymus09.carsensors.MainUiState
import com.anonymus09.carsensors.TelemetryLocationStatus
import com.anonymus09.carsensors.data.PowerState
import com.anonymus09.carsensors.data.TelemetryStorage
import com.anonymus09.carsensors.util.AppConfig
import com.anonymus09.carsensors.util.AppConfig.DB_STATS_REFRESH_RATE
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_MAX_ATTEMPTS

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
    onStopWhenUnpluggedChange: (Boolean) -> Unit,
    onUploadOnlyWhenChargingChange: (Boolean) -> Unit,
    onToggleLogging: () -> Unit,
    onForceUpload: () -> Unit,
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
            title = "Auto-start on boot",
            checked = state.settings.autoStartOnBoot,
            onCheckedChange = onAutoStartOnBootChange
        )

        SettingRow(
            title = "Stop when unplugged",
            checked = state.settings.stopWhenUnplugged,
            onCheckedChange = onStopWhenUnpluggedChange
        )

        SettingRow(
            title = "Upload only when charging",
            checked = state.settings.uploadOnlyWhenCharging,
            onCheckedChange = onUploadOnlyWhenChargingChange
        )

        Spacer(modifier = Modifier.height(8.dp))

        LoggingSection(isLogging = state.isLogging, onToggleLogging = onToggleLogging)

        GpsSection(locationStatus = locationStatus)

        Spacer(modifier = Modifier.height(8.dp))

        PowerSection(power = state.power)

        Spacer(modifier = Modifier.height(8.dp))

        StorageSection(storage = state.storage)

        Spacer(modifier = Modifier.height(8.dp))

        UploadSection(storage = state.storage, onForceUpload = onForceUpload)
    }
}

@Composable
private fun LoggingSection(isLogging: Boolean, onToggleLogging: () -> Unit) {
    Text(
        text = "Logging state: ${if (isLogging) "ACTIVE" else "STOPPED"}",
        style = MaterialTheme.typography.titleMedium
    )

    Button(
        onClick = onToggleLogging,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isLogging) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            }
        )
    ) {
        Text(text = if (isLogging) "Stop logging" else "Start logging")
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

    Text(text = "Server configuration", style = MaterialTheme.typography.titleMedium)
    LabelledValue("Upload endpoint:", AppConfig.TELEMETRY_UPLOAD_URL, valueIsPath = true)

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

@Composable
private fun SettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
