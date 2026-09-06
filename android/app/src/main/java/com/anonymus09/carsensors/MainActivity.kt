package com.anonymus09.carsensors

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anonymus09.carsensors.data.AppDatabase
import com.anonymus09.carsensors.data.PowerStateProvider
import com.anonymus09.carsensors.data.PairingRepository
import com.anonymus09.carsensors.data.ServerHealthChecker
import com.anonymus09.carsensors.data.SettingsRepository
import com.anonymus09.carsensors.data.TelemetryRepository
import com.anonymus09.carsensors.data.parsePairingPayload
import com.anonymus09.carsensors.ui.CarSensorsScreen
import com.anonymus09.carsensors.ui.ConfirmUnpairDialog
import com.anonymus09.carsensors.ui.InvalidPairingCodeDialog
import com.anonymus09.carsensors.ui.ManualPairingDialog
import com.anonymus09.carsensors.ui.PairingOptionsDialog
import com.anonymus09.carsensors.ui.UntaggedRowsDialog
import com.anonymus09.carsensors.ui.theme.CarSensorsTheme
import com.anonymus09.carsensors.work.WifiUploadScheduler
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : ComponentActivity() {

    private var pendingStartAfterPermission: Boolean = false

    private val viewModel: MainViewModel by viewModels {
        val context = applicationContext

        MainViewModelFactory(
            settingsRepository = SettingsRepository(context),
            telemetryRepository = TelemetryRepository(
                dao = AppDatabase.getInstance(context).telemetryDao(),
                databaseFile = AppDatabase.getDatabaseFile(context)
            ),
            powerStateProvider = PowerStateProvider(context),
            healthChecker = ServerHealthChecker(
                loadPairing = { PairingRepository(context).current() }
            ),
            pairingRepository = PairingRepository(context)
        )
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val fineGranted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (pendingStartAfterPermission && (fineGranted || coarseGranted)) {
                TelemetryForegroundService.startService(this)
                WifiUploadScheduler.enqueue(this)
            }

            pendingStartAfterPermission = false
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
         * enableEdgeToEdge already lays the window out behind the system bars
         * and picks the bar icon contrast from the theme, which is what the
         * explicit setDecorFitsSystemWindows call and the SetSystemBarIcons
         * composable here were each doing again.
         */
        enableEdgeToEdge()

        WifiUploadScheduler.enqueue(this)

        setContent {
            CarSensorsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppContent()
                }
            }
        }
    }

    /**
     * Everything on the screen, lifted out of `onCreate` so that the lifecycle
     * method stays about the lifecycle.
     */
    @Composable
    private fun AppContent() {
        val state by viewModel.uiState.collectAsStateWithLifecycle()
        val locationStatus by viewModel.locationStatus.collectAsStateWithLifecycle()
        val serverHealth by viewModel.serverHealth.collectAsStateWithLifecycle()

        val untaggedRows by viewModel.untaggedRowsPendingDecision
            .collectAsStateWithLifecycle()

        var showManualPairing by remember { mutableStateOf(false) }
        var showInvalidCode by remember { mutableStateOf(false) }
        var showConfirmUnpair by remember { mutableStateOf(false) }
        var showPairingOptions by remember { mutableStateOf(false) }

        /*
         * ZXing's capture activity asks for the camera itself, so
         * there is no permission dance to run here first.
         */
        val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
            val contents = result.contents ?: return@rememberLauncherForActivityResult

            val pairing = parsePairingPayload(contents)

            if (pairing == null) {
                showInvalidCode = true
            } else {
                viewModel.startPairing(pairing)
            }
        }

        CarSensorsScreen(
            state = state,
            locationStatus = locationStatus,
            serverHealth = serverHealth,
            onAutoStartOnBootChange = viewModel::setAutoStartOnBoot,
            onRecordOnBatteryChange = viewModel::setRecordOnBattery,
            onUploadOnBatteryChange = viewModel::setUploadOnBattery,
            onWifiOnlyChange = viewModel::setWifiOnly,
            onLiveUploadChange = viewModel::setLiveUploadEnabled,
            onToggleLogging = { toggleLogging(state.loggerState) },
            onWakeOnMotionChange = viewModel::setWakeOnMotion,
            onForceUpload = { WifiUploadScheduler.enqueueNow(this) },
            onRestartService = { TelemetryForegroundService.restartService(this) },
            onServerBaseUrlSave = viewModel::setServerBaseUrl,
            onCheckServer = viewModel::checkServerHealth,
            onManagePairing = { showPairingOptions = true },
            /*
             * Cleartext is only permitted by the debug manifest, so
             * the field must refuse http:// anywhere it would not
             * actually work.
             */
            allowCleartext = BuildConfig.DEBUG
        )

        if (showPairingOptions) {
            PairingOptionsDialog(
                isPaired = state.pairing != null,
                onScan = {
                    showPairingOptions = false
                    scanLauncher.launch(pairingScanOptions())
                },
                onEnterCode = {
                    showPairingOptions = false
                    showManualPairing = true
                },
                onUnpair = {
                    showPairingOptions = false
                    showConfirmUnpair = true
                },
                onDismiss = { showPairingOptions = false }
            )
        }

        if (showManualPairing) {
            ManualPairingDialog(
                onSubmit = { pairing ->
                    showManualPairing = false
                    viewModel.startPairing(pairing)
                },
                onDismiss = { showManualPairing = false }
            )
        }

        untaggedRows?.let { rowCount ->
            UntaggedRowsDialog(
                rowCount = rowCount,
                onKeep = viewModel::keepUntaggedRows,
                onDiscard = viewModel::discardUntaggedRows,
                onCancel = viewModel::cancelPairing
            )
        }

        if (showInvalidCode) {
            InvalidPairingCodeDialog(onDismiss = { showInvalidCode = false })
        }

        if (showConfirmUnpair) {
            ConfirmUnpairDialog(
                hasPendingRows = state.storage.stats.pendingUpload > 0,
                onConfirm = {
                    showConfirmUnpair = false
                    viewModel.unpair()
                },
                onDismiss = { showConfirmUnpair = false }
            )
        }
    }

    /**
     * How the scanner is presented.
     *
     * Locked to QR and with the beep off: this is a setup step somebody runs
     * standing next to a car, not a checkout till.
     */
    private fun pairingScanOptions() = ScanOptions().apply {
        setDesiredBarcodeFormats(ScanOptions.QR_CODE)
        setPrompt("Scan the pairing code shown by the web application")
        setBeepEnabled(false)

        /*
         * Portrait, through an activity of our own that the manifest locks
         * that way. ZXing's own follows the sensor, which meant turning the
         * phone sideways to read a code from an upright screen.
         */
        setCaptureActivity(PortraitCaptureActivity::class.java)
        setOrientationLocked(true)
    }

    private fun toggleLogging(loggerState: LoggerState) {
        if (loggerState != LoggerState.OFF) {
            TelemetryForegroundService.stopService(this)
            return
        }

        if (hasLocationPermission()) {
            TelemetryForegroundService.startService(this)
        } else {
            pendingStartAfterPermission = true
            requestRequiredPermissions()
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }
}
