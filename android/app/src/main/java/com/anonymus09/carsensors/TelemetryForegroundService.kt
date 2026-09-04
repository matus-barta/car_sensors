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
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.anonymus09.carsensors.data.AppDatabase
import com.anonymus09.carsensors.data.PowerState
import com.anonymus09.carsensors.data.PowerStateProvider
import com.anonymus09.carsensors.data.SettingsRepository
import com.anonymus09.carsensors.data.TelemetryUploader
import com.anonymus09.carsensors.data.TelemetrySampleEntity
import com.anonymus09.carsensors.work.WifiUploadScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.roundToInt
import com.anonymus09.carsensors.util.AppConfig.FLUSH_INTERVAL_MS
import com.anonymus09.carsensors.util.AppConfig.LIVE_PUSH_MAX_ROWS
import com.anonymus09.carsensors.util.AppConfig.MAX_LOCATION_AGE_MS
import com.anonymus09.carsensors.util.AppConfig.LIVE_PUSH_MIN_INTERVAL_MS
import com.anonymus09.carsensors.util.AppConfig.SENSOR_SAMPLING_US
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_CHECK_EVERY_N_SAMPLES
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_MAX_ATTEMPTS
import com.anonymus09.carsensors.util.GpsClock
import com.anonymus09.carsensors.util.ageMs
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_TRIGGER_PENDING_ROWS

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

        fun startService(context: Context) {
            val intent = Intent(context, TelemetryForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, TelemetryForegroundService::class.java)
            context.stopService(intent)
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

    /** Samples written since the upload backlog was last measured. */
    private val samplesSinceUploadCheck = AtomicInteger(0)

    /*
     * Every row is stamped from here rather than from System.currentTimeMillis,
     * which Android takes from the network and never from GPS.
     */
    private val gpsClock = GpsClock()

    private val settings by lazy { SettingsRepository(this) }
    private val uploader by lazy { TelemetryUploader.create(this) }

    /** Fix already announced live, as a monotonic clock reading. */
    @Volatile
    private var lastPushedFixNanos: Long = 0

    @Volatile
    private var lastLivePushAt: Long = 0
    private val powerStateProvider by lazy { PowerStateProvider(this) }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val telemetryDao by lazy {
        AppDatabase.getInstance(this).telemetryDao()
    }

    // Latest GPS
    @Volatile
    private var latestLocation: Location? = null

    /*
     * Latest sensor values, null until the sensor has actually reported. These
     * started life as FloatArray(3), whose zeroes read back as a genuine
     * reading of zero: every sample written before a sensor woke up claimed the
     * device was perfectly still and unmagnetised, rather than saying nothing.
     */
    @Volatile
    private var accelValues: FloatArray? = null

    @Volatile
    private var gyroValues: FloatArray? = null

    @Volatile
    private var magnetValues: FloatArray? = null

    /*
     * Scratch space for recomputeHeading, which runs on every accelerometer and
     * magnetometer event - twenty times a second between them, allocating three
     * arrays on each. Only the sensor thread touches them, and that is the same
     * thread workerHandler runs, so they need no synchronisation.
     */
    private val rotationMatrix = FloatArray(9)
    private val inclinationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

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
    private var currentPowerSource: String = PowerState.SOURCE_UNKNOWN

    // Latest pressure data
    private var pressureSensor: Sensor? = null

    @Volatile
    private var pressureHpa: Float? = null

    @Volatile
    private var pressureAccuracy: Int = SensorManager.SENSOR_STATUS_UNRELIABLE

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val wasDisciplined = gpsClock.isDisciplined
            gpsClock.discipline(location)

            /*
             * Recorded once, when satellite time first becomes available. The
             * offset is how wrong the phone's own clock was, which is the whole
             * reason for not stamping samples with it.
             */
            if (!wasDisciplined && gpsClock.isDisciplined) {
                writeSimpleEvent("gps_clock_disciplined", JSONObject().apply {
                    put("systemClockOffsetMs", gpsClock.nowMs() - System.currentTimeMillis())
                })
            }

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
                    currentPowerSource = powerStateProvider.current().source

                    writeSimpleEvent("power_connected", JSONObject().apply {
                        put("powerSource", currentPowerSource)
                    })

                    updateNotification()
                }

                Intent.ACTION_POWER_DISCONNECTED -> {
                    isCurrentlyCharging = false
                    currentPowerSource = PowerState.SOURCE_NOT_CHARGING

                    writeSimpleEvent("power_disconnected", JSONObject(), detached = true)

                    updateNotification()

                    if (settings.current().stopWhenUnplugged) {
                        writeSimpleEvent(
                            "service_stopping_due_to_unplug",
                            JSONObject(),
                            detached = true
                        )
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

        pressureSensor = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        workerThread = HandlerThread(SENSOR_THREAD_NAME).apply { start() }
        workerHandler = Handler(workerThread!!.looper)

        val power = powerStateProvider.current()
        isCurrentlyCharging = power.charging
        currentPowerSource = power.source

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
            put("autoStartOnBootEnabled", settings.current().autoStartOnBoot)
            put("stopWhenUnpluggedEnabled", settings.current().stopWhenUnplugged)
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

        /*
         * Cancelling the scope first, as this did before, cancelled the write
         * below along with it: every run of the service ended without a
         * service_stopped row, so the log simply stopped mid-stream.
         */
        writeSimpleEvent("service_stopped", JSONObject(), detached = true)
        serviceScope.cancel()

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

        pressureSensor?.let {
            sensorManager.registerListener(
                this,
                it,
                SENSOR_SAMPLING_US,
                SENSOR_SAMPLING_US * 5,
                workerHandler
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

            Sensor.TYPE_PRESSURE -> {
                pressureHpa = event.values.firstOrNull()
            }

        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor == null) return

        when (sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> accelAccuracy = accuracy
            Sensor.TYPE_GYROSCOPE -> gyroAccuracy = accuracy
            Sensor.TYPE_MAGNETIC_FIELD -> magnetAccuracy = accuracy
            Sensor.TYPE_PRESSURE -> pressureAccuracy = accuracy
        }

        writeAccuracyChangeEvent(sensor, accuracy)
    }

    private fun recomputeHeading() {
        val accel = accelValues ?: return
        val magnet = magnetValues ?: return

        val success = SensorManager.getRotationMatrix(
            rotationMatrix, inclinationMatrix, accel, magnet
        )

        if (success) {
            SensorManager.getOrientation(rotationMatrix, orientationAngles)
            val azimuthDeg = Math.toDegrees(orientationAngles[0].toDouble()).toFloat()
            headingDegrees = (azimuthDeg + 360f) % 360f
        }
    }

    // ----------------------------------------------------
    // File writing / daily rotation
    // ----------------------------------------------------

    private fun writeMergedSample() {
        Log.i("Telemetry", "Writing sample!")

        /*
         * A fix older than this is no longer where the vehicle is. The last
         * known one goes on being returned after GPS drops out, so writing it
         * with a fresh timestamp is how a phone in a tunnel came to look like a
         * vehicle reporting live from a position it left long ago. Dropping it
         * says "no position" instead, which is the truth, and the position
         * itself is already recorded in the rows written while it was current.
         */
        val location = latestLocation?.takeIf { it.ageMs() <= MAX_LOCATION_AGE_MS }

        if (location == null && _locationStatus.value.hasFix) {
            _locationStatus.value = _locationStatus.value.copy(hasFix = false)
            updateNotification()
        }

        val heading = headingDegrees
        val accel = accelValues
        val gyro = gyroValues
        val magnet = magnetValues
        val pressure = pressureHpa

        val sample = TelemetrySampleEntity(
            timestamp = gpsClock.nowMs(),
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

            accelX = accel?.getOrNull(0),
            accelY = accel?.getOrNull(1),
            accelZ = accel?.getOrNull(2),
            accelAccuracy = accel?.let { accelAccuracy },
            accelAccuracyLabel = accel?.let { accuracyToLabel(accelAccuracy) },

            gyroX = gyro?.getOrNull(0),
            gyroY = gyro?.getOrNull(1),
            gyroZ = gyro?.getOrNull(2),
            gyroAccuracy = gyro?.let { gyroAccuracy },
            gyroAccuracyLabel = gyro?.let { accuracyToLabel(gyroAccuracy) },

            magX = magnet?.getOrNull(0),
            magY = magnet?.getOrNull(1),
            magZ = magnet?.getOrNull(2),
            magnetAccuracy = magnet?.let { magnetAccuracy },
            magnetAccuracyLabel = magnet?.let { accuracyToLabel(magnetAccuracy) },

            pressureHpa = pressure,
            pressureAccuracy = pressure?.let { pressureAccuracy },
            pressureAccuracyLabel = pressure?.let { accuracyToLabel(pressureAccuracy) },

            headingDeg = heading
        )

        serviceScope.launch {
            try {
                telemetryDao.insert(sample)

                maybePushLive(location)

                /*
                 * Both counts used to run on every write: two scans of a table
                 * that grows by 172,800 rows a day, twice a second, one of them
                 * only to log its own result. The backlog size still decides
                 * when to wake the uploader, so that one stays - measured once
                 * every UPLOAD_CHECK_EVERY_N_SAMPLES instead of every time.
                 */
                if (samplesSinceUploadCheck.incrementAndGet() >= UPLOAD_CHECK_EVERY_N_SAMPLES) {
                    samplesSinceUploadCheck.set(0)

                    val pending = telemetryDao.getPendingUploadCount(UPLOAD_MAX_ATTEMPTS)
                    if (pending >= UPLOAD_TRIGGER_PENDING_ROWS) {
                        WifiUploadScheduler.enqueue(this@TelemetryForegroundService)
                    }
                }
            } catch (e: Exception) {
                Log.e("Telemetry", "Room insert failed", e)
            }
        }

    }

    /**
     * Announces the newest position as soon as there is a new one.
     *
     * Always after the row is written, never instead of it: a push that is
     * skipped, fails, or never happens because the phone is on its own battery
     * costs nothing, because the same row is already stored and the batch
     * upload carries it in the ordinary way.
     *
     * What drives this is the position changing. Sensor readings change on
     * every sample and would turn a drive into one request per sample, while
     * telling the map nothing it does not already show.
     */
    private suspend fun maybePushLive(location: Location?) {
        if (location == null) return
        if (!settings.current().liveUploadEnabled) return

        /*
         * Only on power. A live push keeps the radio awake for the length of a
         * drive, which is not something to do to a phone running on its own
         * battery unless it was asked for.
         */
        if (!isCurrentlyCharging) return

        /*
         * elapsedRealtimeNanos rather than the fix's wall clock: it is
         * monotonic, so a clock correction mid-drive cannot make an old fix
         * look new or a new one look stale.
         */
        if (location.elapsedRealtimeNanos <= lastPushedFixNanos) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastLivePushAt < LIVE_PUSH_MIN_INTERVAL_MS) return

        val attempted = uploader.ifIdle {
            val rows = telemetryDao.getNewestLocatedPending(
                LIVE_PUSH_MAX_ROWS,
                UPLOAD_MAX_ATTEMPTS
            )

            when (uploader.send(rows)) {
                TelemetryUploader.Outcome.STORED -> {
                    lastPushedFixNanos = location.elapsedRealtimeNanos
                    lastLivePushAt = now
                    Log.i("Telemetry", "Live push sent ${rows.size} row(s)")
                }

                else -> Log.w(
                    "Telemetry",
                    "Live push did not land; the batch upload will carry these rows"
                )
            }
        }

        if (!attempted) {
            Log.d("Telemetry", "Live push skipped, a batch upload is in flight")
        }
    }

    private fun writeAccuracyChangeEvent(sensor: Sensor, accuracy: Int) {
        Log.i("Accuracy Change", "Sensor: $sensor accuracy: $accuracy")

        val payload = JSONObject().apply {
            put("sensorType", sensor.type)
            put("sensorName", sensor.name)
            put("accuracy", accuracy)
            put("accuracyLabel", accuracyToLabel(accuracy))
            if (sensor.type == Sensor.TYPE_PRESSURE) {
                pressureHpa?.let {
                    put("currentPressureHpa", it)
                }
            }

        }

        val sample = TelemetrySampleEntity(
            event = "sensor_accuracy_changed",
            timestamp = gpsClock.nowMs(),
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

            pressureHpa = null,
            pressureAccuracy = null,
            pressureAccuracyLabel = null,

            headingDeg = null
        )

        serviceScope.launch {
            telemetryDao.insert(sample)
        }

    }

    /**
     * Records an event row.
     *
     * Pass [detached] for events on the shutdown path. Those are written as the
     * service is being torn down, and a coroutine started on [serviceScope]
     * there is cancelled before Room ever sees it.
     */
    private fun writeSimpleEvent(
        eventName: String,
        payload: JSONObject,
        detached: Boolean = false
    ) {
        Log.i("Telemetry", "Event: $eventName payload=$payload")

        val sample = TelemetrySampleEntity(
            event = eventName,
            timestamp = gpsClock.nowMs(),
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

            pressureHpa = null,
            pressureAccuracy = null,
            pressureAccuracyLabel = null,

            headingDeg = null
        )

        /*
         * NonCancellable takes the place of the scope's job as the parent, so a
         * detached write is no longer a child of serviceScope and survives its
         * cancellation. What it touches - the DAO - is an application singleton,
         * so the coroutine outlives the service without outliving its
         * dependencies.
         */
        serviceScope.launch(if (detached) NonCancellable else EmptyCoroutineContext) {
            telemetryDao.insert(sample)
        }

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
        // Same staleness rule as a sample: a fix this old is not a position.
        val loc = latestLocation?.takeIf { it.ageMs() <= MAX_LOCATION_AGE_MS }

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