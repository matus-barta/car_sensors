package com.anonymus09.carsensors

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anonymus09.carsensors.data.AppDatabase
import com.anonymus09.carsensors.data.PairingRejection
import com.anonymus09.carsensors.data.PairingRepository
import com.anonymus09.carsensors.data.PowerStateProvider
import com.anonymus09.carsensors.data.ServerHealthChecker
import com.anonymus09.carsensors.data.SettingsRepository
import com.anonymus09.carsensors.data.TelemetryRepository
import com.anonymus09.carsensors.ui.CarSensorsScreen
import com.anonymus09.carsensors.ui.PairingFlow
import com.anonymus09.carsensors.ui.SetupFix
import com.anonymus09.carsensors.ui.theme.CarSensorsTheme
import com.anonymus09.carsensors.util.AccessState
import com.anonymus09.carsensors.util.LastExit
import com.anonymus09.carsensors.util.LocationAccess
import com.anonymus09.carsensors.util.SystemSettings
import com.anonymus09.carsensors.work.WifiUploadScheduler

class MainActivity : ComponentActivity() {

    private var pendingStartAfterPermission: Boolean = false

    /*
     * Read again whenever the app comes back to the front, because nearly all
     * of it is changed in the system's settings rather than on this screen.
     * Set in onCreate before anything is drawn.
     */
    private var access by mutableStateOf<AccessState?>(null)

    /** How the previous process ended, read with [access]. */
    private var lastExit by mutableStateOf<LastExit?>(null)

    /*
     * Whether asking for location to start the logger was last refused.
     *
     * Without this the button did nothing at all once Android stopped showing
     * the dialog: the request came straight back refused, nothing started and
     * nothing said why. Kept apart from [access] because no location
     * is ordinary until the button has been pressed - it is the refusal that
     * needs saying, not the absence.
     */
    private var locationRefused by mutableStateOf(false)

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

            if (pendingStartAfterPermission) {
                locationRefused = !fineGranted && !coarseGranted

                if (!locationRefused) {
                    TelemetryForegroundService.startService(this)
                    WifiUploadScheduler.enqueue(this)
                }
            }

            pendingStartAfterPermission = false
            refreshSetup()
        }

    private val backgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            refreshSetup()
        }

    /*
     * Falls back to the app's settings page when Android will not ask again,
     * which it otherwise does silently - a refusal nobody saw is the failure
     * the location request used to have.
     */
    private val preciseLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val silentlyRefused = !granted &&
                !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)

            if (silentlyRefused) openAppSettings()

            refreshSetup()
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
        refreshSetup()

        setContent {
            CarSensorsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppContent()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()

        refreshSetup()
    }

    private fun refreshSetup() {
        val current = AccessState.of(this)

        access = current
        lastExit = LastExit.of(this)

        // Granted in the system's settings meanwhile.
        if (current.location != LocationAccess.NONE) locationRefused = false
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

        var showPairingOptions by remember { mutableStateOf(false) }

        val access = access ?: return

        CarSensorsScreen(
            state = state,
            locationStatus = locationStatus,
            serverHealth = serverHealth,
            access = access,
            locationRefused = locationRefused,
            lastExit = lastExit,
            onFix = ::fix,
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

        PairingFlow(
            open = showPairingOptions,
            onOpenChange = { showPairingOptions = it },
            isPaired = state.pairing != null,
            pendingRows = state.storage.stats.pendingUpload,
            isRetired = state.pairingRejection == PairingRejection.DEVICE_RETIRED,
            untaggedRowsPendingDecision = untaggedRows,
            onPair = viewModel::startPairing,
            onKeepUntaggedRows = viewModel::keepUntaggedRows,
            onDiscardUntaggedRows = viewModel::discardUntaggedRows,
            onCancelPairing = viewModel::cancelPairing,
            onUnpair = viewModel::unpair,
            onDiscardPendingRows = viewModel::discardPendingRows
        )
    }

    private fun toggleLogging(loggerState: LoggerState) {
        if (loggerState != LoggerState.OFF) {
            TelemetryForegroundService.stopService(this)
            return
        }

        if (LocationAccess.of(this) != LocationAccess.NONE) {
            TelemetryForegroundService.startService(this)
        } else {
            pendingStartAfterPermission = true
            requestRequiredPermissions()
        }
    }

    private fun requestRequiredPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        /*
         * Ignored while targetSdk is below 33: Android shows no dialog for it
         * and notifications stay off until switched on in settings, which is
         * why the screen says when they are. Kept for the day the target is
         * raised, when asking here starts to work.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }

    /*
     * A request of its own, made only once foreground location is granted.
     *
     * Android 10 answers it with a dialog offering "Allow all the time". From
     * Android 11 the dialog can no longer grant it and offers a link to the
     * app's location page instead, where the choice is made - which is why
     * the result is read again in onResume rather than trusted from here.
     */
    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return

        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    private fun fix(fix: SetupFix) = when (fix) {
        SetupFix.BACKGROUND_LOCATION -> requestBackgroundLocation()
        SetupFix.PRECISE_LOCATION ->
            preciseLocationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        SetupFix.APP_SETTINGS -> openAppSettings()
        SetupFix.NOTIFICATIONS -> open(SystemSettings.notifications(this))
        SetupFix.BATTERY_OPTIMIZATION -> open(SystemSettings.batteryExemption(this))
        SetupFix.DATA_SAVER -> open(SystemSettings.dataSaverExemption(this))
        SetupFix.MANUFACTURER_GUIDE -> open(SystemSettings.manufacturerGuide())
    }

    private fun openAppSettings() = startActivity(SystemSettings.appDetails(this))

    /*
     * Manufacturers remove system screens, and a phone kept for logging may have
     * no browser at all. The app's own settings page always exists, and most of
     * these choices can be reached from it.
     */
    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            openAppSettings()
        }
    }
}
