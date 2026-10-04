package com.anonymus09.carsensors.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.anonymus09.carsensors.LoggerState
import com.anonymus09.carsensors.MainUiState
import com.anonymus09.carsensors.TelemetryLocationStatus
import com.anonymus09.carsensors.data.PowerState
import com.anonymus09.carsensors.data.PowerTier
import com.anonymus09.carsensors.util.AccessState
import com.anonymus09.carsensors.util.LastExit

@Composable
internal fun StatusSection(
    state: MainUiState,
    locationStatus: TelemetryLocationStatus,
    access: AccessState,
    locationRefused: Boolean,
    lastExit: LastExit?,
    onFix: (SetupFix) -> Unit,
    onToggleLogging: () -> Unit
) {
    val running = state.loggerState != LoggerState.OFF

    Text(text = "Car Sensors Logger", style = MaterialTheme.typography.headlineMedium)

    LoggerStateHeadline(state.loggerState)
    PowerTierNote(state.power.tier)

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

    SetupWarnings(
        access = access,
        locationRefused = locationRefused,
        /*
         * Only while it matters: switched on, yet not running. Once it runs
         * again the stop is history, and the server has been told.
         */
        stoppedByUser = lastExit?.takeIf {
            it.stoppedByUser && state.settings.loggerEnabled && !running
        },
        uploadsMayUseMobileData = !state.settings.wifiOnly,
        onFix = onFix
    )

    GpsStatus(locationStatus)
    PowerStatus(state.power)

    if (state.settings.liveUploadEnabled) {
        LiveUploadNote(charging = state.power.charging, wifiOnly = state.settings.wifiOnly)
    }
}

@Composable
private fun LoggerStateHeadline(loggerState: LoggerState) {
    Text(
        text = when (loggerState) {
            LoggerState.OFF -> "STOPPED"
            LoggerState.ARMED -> "WAITING FOR MOVEMENT"
            LoggerState.RECORDING -> "RECORDING"
        },
        style = MaterialTheme.typography.headlineSmall,
        color = when (loggerState) {
            LoggerState.RECORDING -> MaterialTheme.colorScheme.primary
            LoggerState.ARMED -> MaterialTheme.colorScheme.tertiary
            LoggerState.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    )

    if (loggerState == LoggerState.ARMED) {
        Muted(
            "Parked. Sensors and GPS are off; the motion sensor will start " +
                "recording as soon as the vehicle moves."
        )
    }
}

/** Being cut back looks identical to being broken unless it is said. */
@Composable
private fun PowerTierNote(tier: PowerTier) {
    if (tier == PowerTier.FULL) return

    WarningText(
        "Battery saving: " + when (tier) {
            PowerTier.NO_UPLOAD -> "uploads held until the phone is charged"
            PowerTier.REDUCED_RATE -> "recording less often"
            PowerTier.LOCATION_ONLY -> "location only, other sensors off"
            PowerTier.PAUSED -> "recording stopped until charged"
            PowerTier.FULL -> ""
        }
    )
}

@Composable
private fun GpsStatus(locationStatus: TelemetryLocationStatus) {
    if (!locationStatus.hasFix) {
        WarningText("GPS: waiting for a fix")
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
