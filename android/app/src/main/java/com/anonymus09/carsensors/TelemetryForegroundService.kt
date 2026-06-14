package com.anonymus09.carsensors

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.anonymus09.carsensors.data.AppDatabase
import com.anonymus09.carsensors.data.TelemetrySampleEntity
import com.anonymus09.carsensors.work.WifiUploadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import com.anonymus09.carsensors.util.AppConfig.FLUSH_INTERVAL_MS
import com.anonymus09.carsensors.util.AppConfig.SENSOR_SAMPLING_US

data class TelemetryLocationStatus(
    val hasFix: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val speedKmh: Int? = null,
    val provider: String? = null,
    val accuracy: Float? = null
)

class TelemetryForegroundService : Service(), SensorEventListener {

    companion object {
        private const val CHANNEL_ID = "telemetry_logger_channel"
        private const val NOTIFICATION_ID = 1001
        private const val SENSOR_THREAD_NAME = "TelemetryLoggerThread"

        // SharedPreferences
        const val PREFS_NAME = "car_sensors_prefs"
        const val PREF_AUTO_START_ON_BOOT = "auto_start_on_boot"
        const val PREF_STOP_WHEN_UNPLUGGED = "stop_when_unplugged"
        const val PREF_UPLOAD_ONLY_WHEN_CHARGING = "upload_only_when_charging"

        fun startService(context: Context) {
            val intent = Intent(context, TelemetryForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TelemetryForegroundService::class.java)
            context.stopService(intent)
        }

        fun isAutoStartOnBootEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getBoolean(PREF_AUTO_START_ON_BOOT, true)
        }

        fun isStopWhenUnpluggedEnabled(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getBoolean(PREF_STOP_WHEN_UNPLUGGED, true)
        }

        fun setAutoStartOnBoot(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                putBoolean(PREF_AUTO_START_ON_BOOT, enabled)
            }
        }

        fun setStopWhenUnplugged(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit {
                putBoolean(PREF_STOP_WHEN_UNPLUGGED, enabled)
            }
        }

        fun isUploadOnlyWhenChargingEnabled(context: Context): Boolean {
            return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .getBoolean(PREF_UPLOAD_ONLY_WHEN_CHARGING, true)
        }

        fun setUploadOnlyWhenCharging(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit {
                    putBoolean(PREF_UPLOAD_ONLY_WHEN_CHARGING, enabled)
                }
        }

        fun isDeviceCharging(context: Context): Boolean {
            val batteryStatus: Intent? = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        }

        fun getChargePlugLabel(context: Context): String {
            val batteryStatus: Intent? = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )

            val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            return when (plugged) {
                BatteryManager.BATTERY_PLUGGED_AC -> "AC"
                BatteryManager.BATTERY_PLUGGED_USB -> "USB"
                BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
                else -> "NOT_CHARGING"
            }
        }

        fun getLogDir(context: Context): File? {
            return context.getExternalFilesDir(null)
        }

        fun getCurrentLogFile(context: Context): File {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val fileName = "telemetry_${sdf.format(Date())}.ndjson"
            return File(getLogDir(context), fileName)
        }

        fun getRecentLogFiles(context: Context, limit: Int = 5): List<File> {
            val dir = getLogDir(context) ?: return emptyList()

            return dir.listFiles { file ->
                file.name.startsWith("telemetry_") && file.name.endsWith(".ndjson")
            }?.sortedByDescending { it.lastModified() }?.take(limit) ?: emptyList()
        }

        private val _isRunningFlow = MutableStateFlow(false)
        val isRunningFlow: StateFlow<Boolean> = _isRunningFlow.asStateFlow()

        private val _locationStatus = MutableStateFlow(TelemetryLocationStatus())
        val locationStatus: StateFlow<TelemetryLocationStatus> = _locationStatus.asStateFlow()

    }

    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager

    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magnetometer: Sensor? = null

    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null

    private val isRunning = AtomicBoolean(false)

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val telemetryDao by lazy {
        AppDatabase.getInstance(this).telemetryDao()
    }

    // Latest GPS
    @Volatile
    private var latestLocation: Location? = null

    // Latest sensor values
    @Volatile
    private var accelValues = FloatArray(3)

    @Volatile
    private var gyroValues = FloatArray(3)

    @Volatile
    private var magnetValues = FloatArray(3)

    @Volatile
    private var headingDegrees: Float? = null

    // Latest sensor accuracy
    @Volatile
    private var accelAccuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE

    @Volatile
    private var gyroAccuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE

    @Volatile
    private var magnetAccuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE

    // Latest power state
    @Volatile
    private var isCurrentlyCharging: Boolean = false

    @Volatile
    private var currentPowerSource: String = "UNKNOWN"

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            latestLocation = location

            _locationStatus.value = TelemetryLocationStatus(
                hasFix = true,
                latitude = location.latitude,
                longitude = location.longitude,
                speedKmh = (location.speed * 3.6f).toInt(),
                provider = location.provider,
                accuracy = location.accuracy
            )

            updateNotification()
        }

        override fun onProviderEnabled(provider: String) {
            writeSimpleEvent("location_provider_enabled", JSONObject().apply {
                put("provider", provider)
            })
            updateNotification()
        }

        override fun onProviderDisabled(provider: String) {
            _locationStatus.value = _locationStatus.value.copy(
                hasFix = false,
                provider = provider
            )

            writeSimpleEvent("location_provider_disabled", JSONObject().apply {
                put("provider", provider)
            })
            updateNotification()
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {
            writeSimpleEvent("location_provider_status_changed", JSONObject().apply {
                put("provider", provider)
                put("status", status)
            })
        }
    }

    /**
     * Runtime power receiver.
     * This is intentionally registered in code, not in manifest.
     */
    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    isCurrentlyCharging = true
                    currentPowerSource = getChargePlugLabel(context)

                    writeSimpleEvent("power_connected", JSONObject().apply {
                        put("powerSource", currentPowerSource)
                    })

                    updateNotification()
                }

                Intent.ACTION_POWER_DISCONNECTED -> {
                    isCurrentlyCharging = false
                    currentPowerSource = "NOT_CHARGING"

                    writeSimpleEvent("power_disconnected", JSONObject())

                    updateNotification()

                    if (isStopWhenUnpluggedEnabled(context)) {
                        writeSimpleEvent("service_stopping_due_to_unplug", JSONObject())
                        stopSelf()
                    }
                }
            }
        }
    }

    private val flushRunnable = object : Runnable {
        override fun run() {
            Log.i("Telemetry", "Flush tick")
            if (!isRunning.get()) return

            try {
                writeMergedSample()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            workerHandler?.postDelayed(this, FLUSH_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()

        _locationStatus.value = TelemetryLocationStatus(
            hasFix = false
        )

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        workerThread = HandlerThread(SENSOR_THREAD_NAME).apply { start() }
        workerHandler = Handler(workerThread!!.looper)

        isCurrentlyCharging = isDeviceCharging(this)
        currentPowerSource = getChargePlugLabel(this)

        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID, buildNotification("Starting telemetry logger...", null, null, null)
        )

        isRunning.set(true)
        _isRunningFlow.value = true

        registerPowerReceiver()
        registerSensors()
        requestLocationUpdates()

        writeSimpleEvent("service_started", JSONObject().apply {
            put("charging", isCurrentlyCharging)
            put("powerSource", currentPowerSource)
            put("autoStartOnBootEnabled", isAutoStartOnBootEnabled(this@TelemetryForegroundService))
            put(
                "stopWhenUnpluggedEnabled",
                isStopWhenUnpluggedEnabled(this@TelemetryForegroundService)
            )
        })

        workerHandler?.post(flushRunnable)
        updateNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Good for a long-running logger service
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning.set(false)
        _isRunningFlow.value = false
        serviceScope.cancel()

        try {
            unregisterReceiver(powerReceiver)
        } catch (_: Exception) {
        }

        try {
            sensorManager.unregisterListener(this)
        } catch (_: Exception) {
        }

        try {
            locationManager.removeUpdates(locationListener)
        } catch (_: Exception) {
        }

        workerHandler?.removeCallbacksAndMessages(null)
        workerThread?.quitSafely()

        writeSimpleEvent("service_stopped", JSONObject())
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ----------------------------------------------------
    // Registration
    // ----------------------------------------------------

    private fun registerPowerReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        registerReceiver(powerReceiver, filter)
    }

    private fun registerSensors() {
        accelerometer?.let {
            sensorManager.registerListener(
                this, it, SENSOR_SAMPLING_US, SENSOR_SAMPLING_US * 5, workerHandler
            )
        }

        gyroscope?.let {
            sensorManager.registerListener(
                this, it, SENSOR_SAMPLING_US, SENSOR_SAMPLING_US * 5, workerHandler
            )
        }

        magnetometer?.let {
            sensorManager.registerListener(
                this, it, SENSOR_SAMPLING_US, SENSOR_SAMPLING_US * 5, workerHandler
            )
        }
    }

    private fun requestLocationUpdates() {
        val fineGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            writeSimpleEvent("location_permission_missing", JSONObject())
            updateNotification()
            return
        }

        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2000L,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
        } catch (e: SecurityException) {
            e.printStackTrace()
            writeSimpleEvent("location_security_exception", JSONObject().apply {
                put("message", e.message)
            })
            updateNotification()
        }
    }

    // ----------------------------------------------------
    // Sensors
    // ----------------------------------------------------

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                accelValues = event.values.clone()
                recomputeHeading()
            }

            Sensor.TYPE_GYROSCOPE -> {
                gyroValues = event.values.clone()
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                magnetValues = event.values.clone()
                recomputeHeading()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor == null) return

        when (sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> accelAccuracy = accuracy
            Sensor.TYPE_GYROSCOPE -> gyroAccuracy = accuracy
            Sensor.TYPE_MAGNETIC_FIELD -> magnetAccuracy = accuracy
        }

        writeAccuracyChangeEvent(sensor, accuracy)
    }

    private fun recomputeHeading() {
        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)

        val success = SensorManager.getRotationMatrix(
            rotationMatrix, inclinationMatrix, accelValues, magnetValues
        )

        if (success) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            val azimuthRad = orientation[0]
            val azimuthDeg = Math.toDegrees(azimuthRad.toDouble()).toFloat()
            headingDegrees = (azimuthDeg + 360f) % 360f
        }
    }

    // ----------------------------------------------------
    // File writing / daily rotation
    // ----------------------------------------------------

    private fun writeMergedSample() {
        Log.i("Telemetry", "Writing sample!")

        val location = latestLocation
        val heading = headingDegrees

        val sample = TelemetrySampleEntity(
            timestamp = System.currentTimeMillis(),
            event = "telemetry_sample",

            charging = isCurrentlyCharging,
            powerSource = currentPowerSource,

            payload = null,

            latitude = location?.latitude,
            longitude = location?.longitude,
            altitude = location?.altitude,
            speedMps = location?.speed,
            speedKmh = location?.speed?.times(3.6f),
            bearing = location?.bearing,
            accuracyM = location?.accuracy,
            provider = location?.provider,

            accelX = accelValues.getOrNull(0),
            accelY = accelValues.getOrNull(1),
            accelZ = accelValues.getOrNull(2),
            accelAccuracy = accelAccuracy,
            accelAccuracyLabel = accuracyToLabel(accelAccuracy),

            gyroX = gyroValues.getOrNull(0),
            gyroY = gyroValues.getOrNull(1),
            gyroZ = gyroValues.getOrNull(2),
            gyroAccuracy = gyroAccuracy,
            gyroAccuracyLabel = accuracyToLabel(gyroAccuracy),

            magX = magnetValues.getOrNull(0),
            magY = magnetValues.getOrNull(1),
            magZ = magnetValues.getOrNull(2),
            magnetAccuracy = magnetAccuracy,
            magnetAccuracyLabel = accuracyToLabel(magnetAccuracy),

            headingDeg = heading
        )

        serviceScope.launch {
            try {
                telemetryDao.insert(sample)

                val pending = telemetryDao.getPendingUploadCount()
                if (pending >= 200) {
                    WifiUploadScheduler.enqueue(this@TelemetryForegroundService)
                }

                val count = telemetryDao.getCount()
                Log.i("Telemetry", "DB rows: $count")

            } catch (e: Exception) {
                Log.e("Telemetry", "Room insert failed", e)
            }
        }

    }

    private fun writeAccuracyChangeEvent(sensor: Sensor, accuracy: Int) {
        Log.i("Accuracy Change", "Sensor: $sensor accuracy: $accuracy")

        val payload = JSONObject().apply {
            put("sensorType", sensor.type)
            put("sensorName", sensor.name)
            put("accuracy", accuracy)
            put("accuracyLabel", accuracyToLabel(accuracy))
        }

        val sample = TelemetrySampleEntity(
            event = "sensor_accuracy_changed",
            timestamp = System.currentTimeMillis(),
            payload = payload.toString(),

            charging = isCurrentlyCharging,
            powerSource = currentPowerSource,

            latitude = null,
            longitude = null,
            altitude = null,
            speedMps = null,
            speedKmh = null,
            bearing = null,
            accuracyM = null,
            provider = null,

            accelX = null,
            accelY = null,
            accelZ = null,
            accelAccuracy = null,
            accelAccuracyLabel = null,

            gyroX = null,
            gyroY = null,
            gyroZ = null,
            gyroAccuracy = null,
            gyroAccuracyLabel = null,

            magX = null,
            magY = null,
            magZ = null,
            magnetAccuracy = null,
            magnetAccuracyLabel = null,

            headingDeg = null
        )

        serviceScope.launch {
            telemetryDao.insert(sample)
        }

    }

    private fun writeSimpleEvent(eventName: String, payload: JSONObject) {
        Log.i("Telemetry", "Event: $eventName payload=$payload")

        val sample = TelemetrySampleEntity(
            event = eventName,
            timestamp = System.currentTimeMillis(),
            payload = payload.toString(),

            charging = isCurrentlyCharging,
            powerSource = currentPowerSource,

            // no GPS for event
            latitude = null,
            longitude = null,
            altitude = null,
            speedMps = null,
            speedKmh = null,
            bearing = null,
            accuracyM = null,
            provider = null,

            // no sensors
            accelX = null,
            accelY = null,
            accelZ = null,
            accelAccuracy = null,
            accelAccuracyLabel = null,

            gyroX = null,
            gyroY = null,
            gyroZ = null,
            gyroAccuracy = null,
            gyroAccuracyLabel = null,

            magX = null,
            magY = null,
            magZ = null,
            magnetAccuracy = null,
            magnetAccuracyLabel = null,

            headingDeg = null
        )

        serviceScope.launch {
            telemetryDao.insert(sample)
        }

    }

    private fun appendJsonLine(json: JSONObject) {
        val file = currentLogFile()
        file.appendText(json.toString() + "\n")
    }

    private fun currentLogFile(): File {
        val fileName = "telemetry_${currentDateString()}.ndjson"
        val dir = getExternalFilesDir(null)

        //val dir = filesDir <--- private data storage

        return File(dir, fileName)
    }

    private fun currentDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    private fun accuracyToLabel(accuracy: Int): String {
        return when (accuracy) {
            SensorManager.SENSOR_STATUS_UNRELIABLE -> "UNRELIABLE"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "LOW"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "MEDIUM"
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "HIGH"
            else -> "UNKNOWN"
        }
    }

    // ----------------------------------------------------
    // Notification
    // ----------------------------------------------------

    private fun createNotificationChannel() {

        val channel = NotificationChannel(
            CHANNEL_ID, "Telemetry Logger", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Logs location and sensor data while driving"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)

    }

    private fun buildNotification(
        status: String,
        gpsPart: String?,
        powerPart: String?,
        headingPart: String?
    ): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID).setContentTitle("Car telemetry logger")
            .setContentText("$status $gpsPart")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    """
                    $status
                    $gpsPart
                    $powerPart
                    $headingPart
                    """.trimIndent()
                )
            )
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setOnlyAlertOnce(true).build()
    }

    private fun updateNotification() {
        val loc = latestLocation

        val gpsPart = if (loc == null) {
            "GPS: waiting"
        } else {
            val speedKmh = (loc.speed * 3.6f).roundToInt()
            "GPS: ${"%.5f".format(Locale.US, loc.latitude)}, " + "${
                "%.5f".format(
                    Locale.US,
                    loc.longitude
                )
            } | $speedKmh km/h"
        }

        val powerPart = if (isCurrentlyCharging) {
            "Power: $currentPowerSource"
        } else {
            "Power: unplugged"
        }

        val headingPart = headingDegrees?.let {
            "Heading: ${it.roundToInt()}°"
        } ?: "Heading: n/a"

        updateNotificationText(gpsPart, powerPart, headingPart)
    }

    private fun updateNotificationText(gpsPart: String, powerPart: String, headingPart: String) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            NOTIFICATION_ID,
            buildNotification("Logging Active", gpsPart, powerPart, headingPart)
        )
    }
}