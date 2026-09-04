package com.anonymus09.carsensors

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anonymus09.carsensors.data.AppDatabase
import com.anonymus09.carsensors.data.PowerStateProvider
import com.anonymus09.carsensors.data.SettingsRepository
import com.anonymus09.carsensors.data.TelemetryRepository
import com.anonymus09.carsensors.ui.CarSensorsScreen
import com.anonymus09.carsensors.ui.theme.CarSensorsTheme
import com.anonymus09.carsensors.util.DeviceIdProvider
import com.anonymus09.carsensors.work.WifiUploadScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            loadDeviceId = {
                withContext(Dispatchers.IO) { DeviceIdProvider.getOrCreateDeviceId(context) }
            }
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
                    val state by viewModel.uiState.collectAsStateWithLifecycle()
                    val locationStatus by viewModel.locationStatus.collectAsStateWithLifecycle()

                    CarSensorsScreen(
                        state = state,
                        locationStatus = locationStatus,
                        onAutoStartOnBootChange = viewModel::setAutoStartOnBoot,
                        onStopWhenUnpluggedChange = viewModel::setStopWhenUnplugged,
                        onUploadOnlyWhenChargingChange = viewModel::setUploadOnlyWhenCharging,
                        onToggleLogging = { toggleLogging(state.isLogging) },
                        onForceUpload = { WifiUploadScheduler.enqueueNow(this) }
                    )
                }
            }
        }
    }

    private fun toggleLogging(isLogging: Boolean) {
        if (isLogging) {
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
