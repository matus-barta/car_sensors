package com.anonymus09.carsensors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anonymus09.carsensors.data.PowerState
import com.anonymus09.carsensors.data.PowerStateProvider
import com.anonymus09.carsensors.data.SettingsRepository
import com.anonymus09.carsensors.data.TelemetryRepository
import com.anonymus09.carsensors.data.TelemetrySettings
import com.anonymus09.carsensors.data.TelemetryStorage
import com.anonymus09.carsensors.util.AppConfig.DB_STATS_REFRESH_RATE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** Everything the main screen draws, in one value. */
data class MainUiState(
    val deviceId: String = "",
    val settings: TelemetrySettings = TelemetrySettings(),
    val power: PowerState = PowerState(),
    val storage: TelemetryStorage = TelemetryStorage(),
    val isLogging: Boolean = false
)

class MainViewModel(
    private val settingsRepository: SettingsRepository,
    telemetryRepository: TelemetryRepository,
    powerStateProvider: PowerStateProvider,
    loadDeviceId: suspend () -> String
) : ViewModel() {

    private val deviceId = MutableStateFlow("")

    init {
        // Reading it creates it on first run, so this is disk work, not a getter.
        viewModelScope.launch { deviceId.value = loadDeviceId() }
    }

    /**
     * One state for the whole screen.
     *
     * Every source here pushes. The screen previously mixed three mechanisms -
     * values read once into `remember`, a manual refresh button, and a timer
     * that only ran while logging was active - and each of them had a case
     * where the display stopped matching reality.
     *
     * WhileSubscribed stops all of them shortly after the last collector goes
     * away, which is what the hand-rolled repeatOnLifecycle loop was for.
     */
    val uiState: StateFlow<MainUiState> = combine(
        deviceId,
        settingsRepository.observe(),
        powerStateProvider.observe(),
        telemetryRepository.observeStorage(DB_STATS_REFRESH_RATE.seconds),
        TelemetryForegroundService.isRunningFlow
    ) { deviceId, settings, power, storage, isLogging ->
        MainUiState(
            deviceId = deviceId,
            settings = settings,
            power = power,
            storage = storage,
            isLogging = isLogging
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = MainUiState()
    )

    /**
     * Passed straight through: the service is the only source for it, and it is
     * already a StateFlow.
     */
    val locationStatus: StateFlow<TelemetryLocationStatus> =
        TelemetryForegroundService.locationStatus

    fun setAutoStartOnBoot(enabled: Boolean) = settingsRepository.setAutoStartOnBoot(enabled)

    fun setStopWhenUnplugged(enabled: Boolean) = settingsRepository.setStopWhenUnplugged(enabled)

    fun setUploadOnlyWhenCharging(enabled: Boolean) =
        settingsRepository.setUploadOnlyWhenCharging(enabled)
}

class MainViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val telemetryRepository: TelemetryRepository,
    private val powerStateProvider: PowerStateProvider,
    private val loadDeviceId: suspend () -> String
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MainViewModel::class.java)) {
            "Unknown ViewModel class: ${modelClass.name}"
        }

        @Suppress("UNCHECKED_CAST")
        return MainViewModel(
            settingsRepository,
            telemetryRepository,
            powerStateProvider,
            loadDeviceId
        ) as T
    }
}
