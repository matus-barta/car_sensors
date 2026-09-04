package com.anonymus09.carsensors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anonymus09.carsensors.data.PowerState
import com.anonymus09.carsensors.data.PowerStateProvider
import com.anonymus09.carsensors.data.ServerHealth
import com.anonymus09.carsensors.data.ServerHealthChecker
import com.anonymus09.carsensors.data.SettingsRepository
import com.anonymus09.carsensors.data.TelemetryRepository
import com.anonymus09.carsensors.data.TelemetrySettings
import com.anonymus09.carsensors.data.TelemetryStorage
import com.anonymus09.carsensors.util.AppConfig.DB_STATS_REFRESH_RATE
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
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
    val loggerState: LoggerState = LoggerState.OFF
)

class MainViewModel(
    private val settingsRepository: SettingsRepository,
    telemetryRepository: TelemetryRepository,
    powerStateProvider: PowerStateProvider,
    private val healthChecker: ServerHealthChecker,
    loadDeviceId: suspend () -> String
) : ViewModel() {

    private val deviceId = MutableStateFlow("")

    private val _serverHealth = MutableStateFlow<ServerHealth>(ServerHealth.Unknown)

    /**
     * Whether the configured address is answering, and whether it will take
     * this device's telemetry.
     */
    val serverHealth: StateFlow<ServerHealth> = _serverHealth.asStateFlow()

    init {
        // Reading it creates it on first run, so this is disk work, not a getter.
        viewModelScope.launch { deviceId.value = loadDeviceId() }

        checkServerHealth()
    }

    fun checkServerHealth() {
        viewModelScope.launch {
            _serverHealth.value = ServerHealth.Checking
            _serverHealth.value = healthChecker.check()
        }
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
        TelemetryForegroundService.loggerState
    ) { deviceId, settings, power, storage, loggerState ->
        MainUiState(
            deviceId = deviceId,
            settings = settings,
            power = power,
            storage = storage,
            loggerState = loggerState
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

    fun setWakeOnMotion(enabled: Boolean) = settingsRepository.setWakeOnMotion(enabled)

    fun setAutoStartOnBoot(enabled: Boolean) = settingsRepository.setAutoStartOnBoot(enabled)

    fun setRecordOnBattery(enabled: Boolean) = settingsRepository.setRecordOnBattery(enabled)

    fun setWifiOnly(enabled: Boolean) = settingsRepository.setWifiOnly(enabled)

    fun setUploadOnBattery(enabled: Boolean) = settingsRepository.setUploadOnBattery(enabled)

    fun setLiveUploadEnabled(enabled: Boolean) =
        settingsRepository.setLiveUploadEnabled(enabled)

    fun setServerBaseUrl(baseUrl: String) {
        settingsRepository.setServerBaseUrl(baseUrl)

        // A new address is exactly when its correctness is worth knowing.
        checkServerHealth()
    }
}

class MainViewModelFactory(
    private val settingsRepository: SettingsRepository,
    private val telemetryRepository: TelemetryRepository,
    private val powerStateProvider: PowerStateProvider,
    private val healthChecker: ServerHealthChecker,
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
            healthChecker,
            loadDeviceId
        ) as T
    }
}
