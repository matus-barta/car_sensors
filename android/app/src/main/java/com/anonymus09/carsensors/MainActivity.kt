package com.anonymus09.carsensors

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.anonymus09.carsensors.data.AppDatabase
import com.anonymus09.carsensors.ui.theme.CarSensorsTheme
import com.anonymus09.carsensors.util.AppConfig
import com.anonymus09.carsensors.util.AppConfig.DB_STATS_REFRESH_RATE
import com.anonymus09.carsensors.util.DeviceIdProvider
import com.anonymus09.carsensors.work.WifiUploadScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

data class DbStorageInfo(
    val exists: Boolean = false,
    val totalRows: Int = 0,
    val telemetryRows: Int = 0,
    val eventRows: Int = 0,
    val lastTimestamp: Long? = null,
    val dbSizeBytes: Long = 0,

    val pendingUpload: Int = 0,
    val lastUploadTime: Long? = null,
    val maxUploadAttempts: Int = 0

)

class MainActivity : ComponentActivity() {

    private var pendingStartAfterPermission: Boolean = false

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
        WifiUploadScheduler.enqueue(this)

        enableEdgeToEdge()

        WindowCompat.setDecorFitsSystemWindows(window, false)

        TelemetryForegroundService.setAutoStartOnBoot(this, true)
        TelemetryForegroundService.setStopWhenUnplugged(this, true)

        val db = AppDatabase.getInstance(applicationContext)
        val dao = db.telemetryDao()

        val viewModel: MainViewModel = ViewModelProvider(
            this,
            MainViewModelFactory(dao)
        )[MainViewModel::class.java]

        setContent {
            CarSensorsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CarSensorsScreen(viewModel = viewModel)
                }
            }
        }
    }

    @Composable
    fun SetSystemBarIcons(activity: Activity) {
        val darkTheme = isSystemInDarkTheme()

        SideEffect {
            val controller = WindowInsetsControllerCompat(
                activity.window,
                activity.window.decorView
            )

            // TRUE → dark icons (light background)
            // FALSE → light icons (dark background)
            controller.isAppearanceLightStatusBars = !darkTheme
            controller.isAppearanceLightNavigationBars = !darkTheme
        }
    }

    @Composable
    private fun CarSensorsScreen(viewModel: MainViewModel) {
        val pendingCount by viewModel.pendingCount.collectAsState()
        SetSystemBarIcons(activity = this@MainActivity)

        val lastUploadTime by viewModel.lastUploadTime.collectAsState()

        var autoStartOnBoot by remember {
            mutableStateOf(
                TelemetryForegroundService.isAutoStartOnBootEnabled(this@MainActivity)
            )
        }

        var stopWhenUnplugged by remember {
            mutableStateOf(
                TelemetryForegroundService.isStopWhenUnpluggedEnabled(this@MainActivity)
            )
        }

        var isCharging by remember {
            mutableStateOf(
                TelemetryForegroundService.isDeviceCharging(this@MainActivity)
            )
        }

        var powerSource by remember {
            mutableStateOf(
                TelemetryForegroundService.getChargePlugLabel(this@MainActivity)
            )
        }

        var uploadOnlyWhenCharging by remember {
            mutableStateOf(
                TelemetryForegroundService.isUploadOnlyWhenChargingEnabled(this@MainActivity)
            )
        }

        val insets = WindowInsets.systemBars.asPaddingValues()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)

        ) {
            Text(
                text = "Car Sensors Logger",
                style = MaterialTheme.typography.headlineMedium
            )

            Text(
                text = "Designed for continuous background logging with foreground service.",
                style = MaterialTheme.typography.bodyMedium
            )

            val deviceId = DeviceIdProvider.getOrCreateDeviceId(this@MainActivity)
            Text(
                text = "Device id:",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = deviceId,
                style = MaterialTheme.typography.bodySmall
            )

            Spacer(modifier = Modifier.height(8.dp))

            SettingRow(
                title = "Auto-start on boot",
                checked = autoStartOnBoot,
                onCheckedChange = { checked ->
                    autoStartOnBoot = checked
                    TelemetryForegroundService.setAutoStartOnBoot(this@MainActivity, checked)
                }
            )

            SettingRow(
                title = "Stop when unplugged",
                checked = stopWhenUnplugged,
                onCheckedChange = { checked ->
                    stopWhenUnplugged = checked
                    TelemetryForegroundService.setStopWhenUnplugged(this@MainActivity, checked)
                }
            )

            SettingRow(
                title = "Upload only when charging",
                checked = uploadOnlyWhenCharging,
                onCheckedChange = { checked ->
                    uploadOnlyWhenCharging = checked
                    TelemetryForegroundService.setUploadOnlyWhenCharging(this@MainActivity, checked)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            val isLogging by TelemetryForegroundService.isRunningFlow.collectAsState()

            Text(
                text = "Logging state: ${if (isLogging) "ACTIVE" else "STOPPED"}",
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = {
                    if (!isLogging) {
                        // START
                        if (hasLocationPermission()) {
                            TelemetryForegroundService.startService(this@MainActivity)
                        } else {
                            pendingStartAfterPermission = true
                            requestRequiredPermissions()
                        }
                    } else {
                        // STOP
                        TelemetryForegroundService.stopService(this@MainActivity)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLogging)
                        MaterialTheme.colorScheme.error       // red when running
                    else
                        MaterialTheme.colorScheme.primary     // normal when stopped
                )
            ) {
                Text(
                    text = if (isLogging) "Stop logging" else "Start logging"
                )
            }

            val locationStatus by TelemetryForegroundService.locationStatus.collectAsState()

            Text(
                text = "GPS status",
                style = MaterialTheme.typography.titleMedium
            )

            if (!locationStatus.hasFix) {
                Text(
                    text = "Waiting for GPS fix...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                Text(
                    text = "Lat: %.5f, Lon: %.5f".format(
                        locationStatus.latitude,
                        locationStatus.longitude
                    ),
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Speed: ${locationStatus.speedKmh} km/h",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Provider: ${locationStatus.provider}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Accuracy: ${locationStatus.accuracy?.toInt()} m",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Charging now: ${if (isCharging) "YES" else "NO"}",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Power source: $powerSource",
                style = MaterialTheme.typography.labelMedium
            )

            Button(
                onClick = {
                    isCharging = TelemetryForegroundService.isDeviceCharging(this@MainActivity)
                    powerSource = TelemetryForegroundService.getChargePlugLabel(this@MainActivity)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Refresh charging status")
            }

            Spacer(modifier = Modifier.height(8.dp))


            val context = this@MainActivity
            val dbFile = remember { AppDatabase.getDatabaseFile(context) }

            var refreshKey by remember { mutableIntStateOf(0) }
            val lifecycleOwner = LocalLifecycleOwner.current



            LaunchedEffect(isLogging, lifecycleOwner) {
                lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    while (isActive && isLogging) {
                        Log.i("UI", "Auto refresh tick")
                        delay(DB_STATS_REFRESH_RATE.seconds)
                        refreshKey++
                    }
                }
            }

            val dbInfo by produceState(
                initialValue = DbStorageInfo(),
                key1 = refreshKey
            ) {
                value = withContext(Dispatchers.IO) {
                    val dao = AppDatabase.getInstance(context).telemetryDao()

                    DbStorageInfo(
                        exists = dbFile.exists(),
                        totalRows = dao.getCount(),
                        telemetryRows = dao.getTelemetrySampleCount(),
                        eventRows = dao.getEventCount(),
                        lastTimestamp = dao.getLastTimestamp(),
                        dbSizeBytes = dbFile.length(),

                        pendingUpload = dao.getPendingUploadCount(),
                        lastUploadTime = dao.getLastUploadTime(),
                        maxUploadAttempts = dao.getMaxUploadAttempts() ?: 0

                    )
                }
            }

            val lastSampleText = dbInfo.lastTimestamp?.let {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(it))
            } ?: "N/A"

            Text(
                text = "Database storage",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Updates every $DB_STATS_REFRESH_RATE seconds",
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = "Database file:",
                style = MaterialTheme.typography.labelMedium
            )

            Text(
                text = dbFile.absolutePath,
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = "Database size:",
                style = MaterialTheme.typography.labelMedium
            )

            Text(
                text = formatBytes(dbInfo.dbSizeBytes),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Database exists:",
                style = MaterialTheme.typography.labelMedium
            )

            Text(
                text = if (dbInfo.exists) "Yes" else "No",
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Total rows:",
                style = MaterialTheme.typography.labelMedium
            )

            Text(
                text = dbInfo.totalRows.toString(),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Telemetry samples:",
                style = MaterialTheme.typography.labelMedium
            )

            Text(
                text = dbInfo.telemetryRows.toString(),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Event rows:",
                style = MaterialTheme.typography.labelMedium
            )

            Text(
                text = dbInfo.eventRows.toString(),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Last stored timestamp:",
                style = MaterialTheme.typography.labelMedium
            )

            Text(
                text = lastSampleText,
                style = MaterialTheme.typography.bodyMedium
            )

            val lastUploadText = lastUploadTime?.let {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(it))
            } ?: "Never"

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Server configuration",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Upload endpoint:",
                style = MaterialTheme.typography.labelMedium
            )

            Text(
                text = AppConfig.TELEMETRY_UPLOAD_URL,
                style = MaterialTheme.typography.bodySmall
            )

            Text(
                text = "Upload status",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Pending upload rows:",
                style = MaterialTheme.typography.labelMedium
            )

            Text(
                text = pendingCount.toString(),
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Last successful upload:",
                style = MaterialTheme.typography.labelMedium
            )

            Text(
                text = lastUploadText,
                style = MaterialTheme.typography.bodyMedium
            )

            Text(
                text = "Max upload attempts:",
                style = MaterialTheme.typography.labelMedium
            )

            Text(
                text = dbInfo.maxUploadAttempts.toString(),
                style = MaterialTheme.typography.bodyMedium
            )

            Button(
                onClick = {
                    WifiUploadScheduler.enqueueNow(this@MainActivity)
                },
                enabled = dbInfo.pendingUpload > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (dbInfo.pendingUpload > 0)
                        "Force upload now"
                    else
                        "Nothing to upload"
                )
            }
        }
    }

    @Composable
    private fun SettingRow(
        title: String,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
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

        // Optional for newer Android versions if you test on Android 13+ devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += Manifest.permission.POST_NOTIFICATIONS
        }

        permissionLauncher.launch(permissions.toTypedArray())
    }

    fun formatBytes(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0

        return when {
            mb >= 1 -> String.format(Locale.US, "%.2f MB", mb)
            kb >= 1 -> String.format(Locale.US, "%.1f KB", kb)
            else -> "$bytes B"
        }
    }

}