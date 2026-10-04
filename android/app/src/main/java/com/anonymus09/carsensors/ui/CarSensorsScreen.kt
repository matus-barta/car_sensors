package com.anonymus09.carsensors.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anonymus09.carsensors.MainUiState
import com.anonymus09.carsensors.TelemetryLocationStatus
import com.anonymus09.carsensors.data.ServerHealth
import com.anonymus09.carsensors.util.AccessState
import com.anonymus09.carsensors.util.LastExit

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
    access: AccessState,
    locationRefused: Boolean,
    lastExit: LastExit?,
    onFix: (SetupFix) -> Unit,
    onToggleLogging: () -> Unit,
    onWakeOnMotionChange: (Boolean) -> Unit,
    onAutoStartOnBootChange: (Boolean) -> Unit,
    onRecordOnBatteryChange: (Boolean) -> Unit,
    onUploadOnBatteryChange: (Boolean) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onLiveUploadChange: (Boolean) -> Unit,
    onServerBaseUrlSave: (String) -> Unit,
    onCheckServer: (String) -> Unit,
    onForceUpload: () -> Unit,
    onRestartService: () -> Unit,
    onManagePairing: () -> Unit,
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
            access = access,
            locationRefused = locationRefused,
            lastExit = lastExit,
            onFix = onFix,
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
            onLiveUploadChange = onLiveUploadChange,
            onManagePairing = onManagePairing
        )

        SectionDivider()

        DiagnosticsSection(
            state = state,
            onForceUpload = onForceUpload,
            onRestartService = onRestartService,
            onFix = onFix
        )
    }
}
