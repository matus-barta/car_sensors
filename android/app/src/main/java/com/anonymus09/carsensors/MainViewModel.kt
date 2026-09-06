package com.anonymus09.carsensors

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anonymus09.carsensors.data.DevicePairing
import com.anonymus09.carsensors.data.PairingRejection
import com.anonymus09.carsensors.data.PairingRepository
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
    /**
     * The vehicle this phone is paired with, or null while it has none.
     *
     * Null is an ordinary state rather than a fault. An unpaired app records
     * freely - somebody may be driving before the vehicle exists - and simply
     * never uploads, so the rows wait until an identity is assigned.
     */
    val pairing: DevicePairing? = null,

    /**
     * Why the server last refused this phone, if it did.
     *
     * Separate from [pairing] because a pairing can be perfectly well formed
     * and still be turned away - and which of the two reasons it was decides
     * whether pairing again is the remedy or a waste of time.
     */
    val pairingRejection: PairingRejection? = null,
    val settings: TelemetrySettings = TelemetrySettings(),
    val power: PowerState = PowerState(),
    val storage: TelemetryStorage = TelemetryStorage(),
    val loggerState: LoggerState = LoggerState.OFF
)

class MainViewModel(
    private val settingsRepository: SettingsRepository,
    private val telemetryRepository: TelemetryRepository,
    powerStateProvider: PowerStateProvider,
    private val healthChecker: ServerHealthChecker,
    private val pairingRepository: PairingRepository
) : ViewModel() {

    private val _serverHealth = MutableStateFlow<ServerHealth>(ServerHealth.Unknown)

    /**
     * Whether the configured address is answering, and whether it will take
     * this device's telemetry.
     */
    val serverHealth: StateFlow<ServerHealth> = _serverHealth.asStateFlow()

    /**
     * How many recorded rows have never reached a server, asked when pairing
     * begins and cleared once it is answered.
     *
     * Non-null means the question is on screen. Those rows will go up under
     * whatever identity is adopted next, so the one moment to ask about them is
     * before that identity exists - afterwards nothing can tell which vehicle
     * they belonged to.
     */
    private val _untaggedRowsPendingDecision = MutableStateFlow<Int?>(null)
    val untaggedRowsPendingDecision: StateFlow<Int?> =
        _untaggedRowsPendingDecision.asStateFlow()

    private val pendingPairing = MutableStateFlow<DevicePairing?>(null)

    init {
        checkServerHealth()
    }

    /**
     * Adopts a scanned or pasted pairing, asking about orphaned rows first.
     *
     * Rows recorded before this point belong to nobody in particular. Attaching
     * them to the vehicle being paired is right when the phone has been sitting
     * in that car all along, and wrong when it recorded somebody else's trip,
     * and only the person holding it knows which - so the pairing waits until
     * they say.
     */
    fun startPairing(pairing: DevicePairing) {
        viewModelScope.launch {
            val waiting = telemetryRepository.pendingUploadCount()

            if (waiting > 0) {
                pendingPairing.value = pairing
                _untaggedRowsPendingDecision.value = waiting

                return@launch
            }

            commitPairing(pairing)
        }
    }

    /** Keeps the waiting rows, which will upload as the vehicle just paired. */
    fun keepUntaggedRows() {
        val pairing = pendingPairing.value ?: return

        _untaggedRowsPendingDecision.value = null
        pendingPairing.value = null

        commitPairing(pairing)
    }

    /** Discards the waiting rows, then pairs. */
    fun discardUntaggedRows() {
        val pairing = pendingPairing.value ?: return

        _untaggedRowsPendingDecision.value = null
        pendingPairing.value = null

        viewModelScope.launch {
            telemetryRepository.deleteNotUploaded()

            commitPairing(pairing)
        }
    }

    /** Abandons the pairing rather than answering the question. */
    fun cancelPairing() {
        _untaggedRowsPendingDecision.value = null
        pendingPairing.value = null
    }

    /**
     * Throws away every row that has not been uploaded.
     *
     * Offered when the vehicle has been retired on the server, because those
     * rows will never be accepted and the alternative is letting a storage
     * ceiling decide. Nothing calls this on its own - see
     * `discardUntaggedRows` for the other place the user is asked.
     */
    fun discardPendingRows() {
        viewModelScope.launch { telemetryRepository.deleteNotUploaded() }
    }

    /** Returns the app to being unpaired. Recording continues; uploading stops. */
    fun unpair() {
        pairingRepository.clear()

        _serverHealth.value = ServerHealth.NotPaired
    }

    private fun commitPairing(pairing: DevicePairing) {
        pairingRepository.save(pairing)

        // A new credential is exactly when its correctness is worth knowing.
        checkServerHealth()
    }

    /**
     * Checks [baseUrl], defaulting to the address in force.
     *
     * The screen passes what is in the field rather than what was last saved,
     * so an address can be tried before committing to it.
     */
    fun checkServerHealth(baseUrl: String? = null) {
        viewModelScope.launch {
            _serverHealth.value = ServerHealth.Checking
            _serverHealth.value = healthChecker.check(
                baseUrl ?: settingsRepository.current().serverBaseUrl
            )
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
        pairingRepository.observe(),
        settingsRepository.observe(),
        powerStateProvider.observe(),
        telemetryRepository.observeStorage(DB_STATS_REFRESH_RATE.seconds),
        TelemetryForegroundService.loggerState
    ) { pairingStatus, settings, power, storage, loggerState ->
        MainUiState(
            pairing = pairingStatus.pairing,
            pairingRejection = pairingStatus.rejection,
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
    private val pairingRepository: PairingRepository
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
            pairingRepository
        ) as T
    }
}
