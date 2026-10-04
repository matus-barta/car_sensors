package com.anonymus09.carsensors.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anonymus09.carsensors.MainUiState
import com.anonymus09.carsensors.util.AppConfig.DB_STATS_REFRESH_RATE
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_MAX_ATTEMPTS

/**
 * Everything worth having when something is wrong and nothing worth reading
 * when it is not, so it stays folded away by default.
 */
@Composable
internal fun DiagnosticsSection(
    state: MainUiState,
    onForceUpload: () -> Unit,
    onRestartService: () -> Unit,
    onFix: (SetupFix) -> Unit
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
        WarningText("Blocked rows (refused $UPLOAD_MAX_ATTEMPTS times): ${stats.blockedUpload}")
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

    /*
     * Some manufacturers stop background apps beyond anything Android itself
     * does, with settings of their own that an app cannot see or change.
     */
    Muted(
        "If logging stops on its own although nothing above is wrong, the phone's " +
            "manufacturer may be stopping it. The guide lists the settings to change."
    )

    val (guideLabel, guideFix) = manufacturerGuide()

    OutlinedButton(onClick = { onFix(guideFix) }, modifier = Modifier.fillMaxWidth()) {
        Text(guideLabel)
    }
}
