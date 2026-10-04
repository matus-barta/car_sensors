package com.anonymus09.carsensors

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anonymus09.carsensors.data.AppDatabase
import com.anonymus09.carsensors.data.PowerStateProvider
import com.anonymus09.carsensors.data.PairingRejection
import com.anonymus09.carsensors.data.PairingRepository
import com.anonymus09.carsensors.data.ServerHealthChecker
import com.anonymus09.carsensors.data.SettingsRepository
import com.anonymus09.carsensors.data.TelemetryRepository
import com.anonymus09.carsensors.ui.CarSensorsScreen
import com.anonymus09.carsensors.ui.PairingFlow
import com.anonymus09.carsensors.ui.theme.CarSensorsTheme
import com.anonymus09.carsensors.util.LocationAccess
import com.anonymus09.carsensors.work.WifiUploadScheduler

class MainActivity : ComponentActivity() {

    private var pendingStartAfterPermission: Boolean = false

    /*
     * Read again whenever the app comes back to the front, because the place
     * background access is granted is the system's settings, not this screen.
     */
    private var locationAccess by mutableStateOf(LocationAccess.NONE)

    /*
     * Whether asking for location to start the logger was last refused.
     *
     * Without this the button did nothing at all once Android stopped showing
     * the dialog: the request came straight back refused, nothing started and
     * nothing said why. Kept apart from [locationAccess] because no location
     * is ordinary until the button has been pressed - it is the refusal that
     * needs saying, not the absence.
     */
    private var locationRefused by mutableStateOf(false)

    /*
     * Whether this app may post notifications at all - Android 13's
     * permission, or notifications switched off by hand on any version. The
     * upload warning and the logger's own notification both depend on it.
     */
    private var notificationsEnabled by mutableStateOf(true)

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
            locationAccess = LocationAccess.of(this)
        }

    private val backgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            locationAccess = LocationAccess.of(this)
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

    override fun onResume() {
        super.onResume()

        locationAccess = LocationAccess.of(this)

        // Granted in the system's settings meanwhile.
        if (locationAccess != LocationAccess.NONE) locationRefused = false

        notificationsEnabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
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

        CarSensorsScreen(
            state = state,
            locationStatus = locationStatus,
            serverHealth = serverHealth,
            locationAccess = locationAccess,
            locationRefused = locationRefused,
            notificationsEnabled = notificationsEnabled,
            onAllowBackgroundLocation = ::requestBackgroundLocation,
            onOpenAppSettings = ::openAppSettings,
            onOpenNotificationSettings = ::openNotificationSettings,
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

    /*
     * The app's own page in the system's settings, where its permissions are.
     *
     * Rather than asking again: once Android has decided not to show the
     * dialog any more, asking returns a refusal without the user seeing
     * anything, and this page is the only place left to change the answer.
     *
     * The app's page rather than its location permission page, because that
     * one cannot be opened by an ordinary app; the warning names the two taps
     * from here instead.
     */
    private fun openAppSettings() {
        startActivity(
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null)
            )
        )
    }

    /*
     * Straight to the switch, unlike location: Android lets an app open its own
     * notification settings, and on Android 13 that switch is the permission.
     *
     * Rather than asking with a dialog first, because the dialog may no longer
     * be shown - asking would then come back refused without the user seeing
     * anything, exactly the failure the location request had.
     */
    private fun openNotificationSettings() {
        startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        )
    }
}
