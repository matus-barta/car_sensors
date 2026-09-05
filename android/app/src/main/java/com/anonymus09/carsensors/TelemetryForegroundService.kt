package com.anonymus09.carsensors

import android.annotation.SuppressLint
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
import android.hardware.TriggerEvent
import android.hardware.TriggerEventListener
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.os.IBinder
import android.os.PowerManager
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.anonymus09.carsensors.data.AppDatabase
import com.anonymus09.carsensors.data.PowerState
import com.anonymus09.carsensors.data.PowerTier
import com.anonymus09.carsensors.data.PowerStateProvider
import com.anonymus09.carsensors.data.ServerHealthChecker
import com.anonymus09.carsensors.data.SettingsRepository
import com.anonymus09.carsensors.data.TelemetryUploader
import com.anonymus09.carsensors.data.UploadOutcome
import com.anonymus09.carsensors.data.uploadSilenceMessage
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
import com.anonymus09.carsensors.util.AppConfig.BATTERY_REDUCED_RATE_FACTOR
import com.anonymus09.carsensors.util.AppConfig.FLUSH_INTERVAL_MS
import com.anonymus09.carsensors.util.AppConfig.GPS_UPDATE_INTERVAL_MS
import com.anonymus09.carsensors.util.AppConfig.MPS_TO_KMH
import com.anonymus09.carsensors.util.AppConfig.NETWORK_UPDATE_INTERVAL_MS
import com.anonymus09.carsensors.util.AppConfig.SENSOR_BATCH_LATENCY_FACTOR
import com.anonymus09.carsensors.util.AppConfig.LIVE_PUSH_MAX_ROWS
import com.anonymus09.carsensors.util.AppConfig.MAX_LOCATION_AGE_MS
import com.anonymus09.carsensors.util.AppConfig.MOTION_CONFIRM_WINDOW_MS
import com.anonymus09.carsensors.util.AppConfig.MOTION_IDLE_TIMEOUT_MS
import com.anonymus09.carsensors.util.AppConfig.MOVEMENT_SPEED_MPS
import com.anonymus09.carsensors.util.AppConfig.LIVE_PUSH_MIN_INTERVAL_MS
import com.anonymus09.carsensors.util.AppConfig.SENSOR_SAMPLING_US
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_CHECK_EVERY_N_SAMPLES
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_MAX_ATTEMPTS
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_SILENCE_RENOTIFY_MS
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_SILENCE_WARNING_MS
import com.anonymus09.carsensors.util.DeviceIdProvider
import com.anonymus09.carsensors.util.GpsClock
import com.anonymus09.carsensors.util.ageMs
import com.anonymus09.carsensors.util.AppConfig.UPLOAD_TRIGGER_PENDING_ROWS

/**
 * What the logger is doing.
 *
 * [ARMED] is the parked state: the service stays alive so that nothing has to
 * wake it, but the sensors and GPS are unregistered and only the hardware
 * significant-motion trigger is listening. It costs almost nothing and is what
 * lets a phone live in a car unattended without either recording a stationary
 * vehicle around the clock or needing to be restarted by hand.
 */
enum class LoggerState { OFF, ARMED, RECORDING }

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

        /*
         * Recorded here rather than in the service's own lifecycle, because it
         * is the user's intent that should survive a reboot - not whether the
         * process happened to be alive. A service restarted by START_STICKY
         * must not be able to change the answer.
         */
        fun startService(context: Context) {
            SettingsRepository(context).setLoggerEnabled(true)

            val intent = Intent(context, TelemetryForegroundService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            SettingsRepository(context).setLoggerEnabled(false)

            val intent = Intent(context, TelemetryForegroundService::class.java)
            context.stopService(intent)
        }

        /** Asks a running service to tear its listeners down and re-register them. */
        const val ACTION_RESTART = "com.anonymus09.carsensors.action.RESTART"

        private const val WAKE_LOCK_TAG = "CarSensors::Telemetry"

        /** Normalises an azimuth that came back negative into 0..360. */
        private const val FULL_TURN_DEGREES = 360f

        fun restartService(context: Context) {
            val intent = Intent(context, TelemetryForegroundService::class.java).apply {
                action = ACTION_RESTART
            }

            ContextCompat.startForegroundService(context, intent)
        }

        private val _loggerState = MutableStateFlow(LoggerState.OFF)
        val loggerState: StateFlow<LoggerState> = _loggerState.asStateFlow()

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
    private val uploadSilenceNotifier by lazy { UploadSilenceNotifier(this) }

    /**
     * When the upload-silence warning was last posted, so that an outage
     * lasting weeks does not re-check the server every minute.
     *
     * Held on the instance rather than persisted: forgetting it across a
     * restart costs one extra check, and the warning itself survives in the
     * shade regardless.
     */
    private var uploadSilenceWarnedAt: Long? = null

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

    private var significantMotion: Sensor? = null

    /*
     * Held only while recording. Handler.postDelayed runs on uptimeMillis,
     * which does not advance in deep sleep, so without this the flush loop
     * stalls whenever the device suspends - which is exactly what a phone left
     * in a parked car does.
     */
    private var wakeLock: PowerManager.WakeLock? = null

    /** How much of itself the logger is currently doing. */
    @Volatile
    private var powerTier: PowerTier = PowerTier.FULL

    /** Last time the vehicle was seen moving, on the monotonic clock. */
    @Volatile
    private var lastMovementAt: Long = 0

    /**
     * Whether GPS has seconded the motion sensor's opinion this session.
     *
     * Significant motion is a cheap first gate but an indiscriminate one - it
     * fires for a door closing or the phone being picked up. Until a fix shows
     * the vehicle actually travelling, a session is treated as unproven and
     * given up quickly.
     */
    @Volatile
    private var movementConfirmed: Boolean = false

    /**
     * Fires once when the device starts moving, then unregisters itself.
     *
     * Hardware-backed and cheap, which is the whole point: it is what stands in
     * for keeping the sensors and GPS running while the car is parked.
     */
    private val motionTrigger = object : TriggerEventListener() {
        override fun onTrigger(event: TriggerEvent?) {
            workerHandler?.post {
                if (canRecordNow()) {
                    Log.i("Telemetry", "Significant motion - starting to record")
                    enterRecording()
                } else {
                    Log.i("Telemetry", "Motion while on battery - staying parked")
                    rearmMotionTrigger()
                }
            }
        }
    }

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

            if (location.speed >= MOVEMENT_SPEED_MPS) {
                lastMovementAt = SystemClock.elapsedRealtime()
                movementConfirmed = true
            }

            _locationStatus.value = TelemetryLocationStatus(
                hasFix = true,
                latitude = location.latitude,
                longitude = location.longitude,
                speedKmh = (location.speed * MPS_TO_KMH).toInt(),
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

                    /*
                     * Power is the other thing that can end a wait. If waiting
                     * was only ever about being on battery, recording can start
                     * now; if movement is also being waited for, it cannot.
                     */
                    if (_loggerState.value == LoggerState.ARMED && !shouldWaitForMotion()) {
                        enterRecording()
                    }
                }

                Intent.ACTION_POWER_DISCONNECTED -> {
                    isCurrentlyCharging = false
                    currentPowerSource = PowerState.SOURCE_NOT_CHARGING

                    writeSimpleEvent("power_disconnected", JSONObject())

                    updateNotification()

                    /*
                     * Parked rather than stopped. Stopping took the power
                     * receiver down with the service, so nothing was left
                     * listening for the power coming back and the logger stayed
                     * down until someone opened the app - which, for a phone
                     * that lives in a car, was never.
                     */
                    if (!settings.current().recordOnBattery) {
                        writeSimpleEvent("recording_stopped_on_unplug", JSONObject())
                        enterArmed()
                    }
                }
            }
        }
    }

    private val flushRunnable = object : Runnable {
        override fun run() {
            if (!isRunning.get()) return

            if (shouldReturnToArmed()) {
                Log.i(
                    "Telemetry",
                    if (movementConfirmed) {
                        "Stationary; going back to waiting for movement"
                    } else {
                        "Motion was not the vehicle moving; going back to waiting"
                    }
                )
                enterArmed()
                return
            }

            try {
                writeMergedSample()
            } catch (e: Exception) {
                Log.e("Telemetry", "Could not write a sample", e)
            }

            val interval = if (powerTier >= PowerTier.REDUCED_RATE) {
                FLUSH_INTERVAL_MS * BATTERY_REDUCED_RATE_FACTOR
            } else {
                FLUSH_INTERVAL_MS
            }

            workerHandler?.postDelayed(this, interval)
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
        significantMotion = sensorManager.getDefaultSensor(Sensor.TYPE_SIGNIFICANT_MOTION)

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

        registerPowerReceiver()
        watchPowerState()

        writeSimpleEvent("service_started", JSONObject().apply {
            put("charging", isCurrentlyCharging)
            put("powerSource", currentPowerSource)
            put("autoStartOnBootEnabled", settings.current().autoStartOnBoot)
            put("wakeOnMotionEnabled", settings.current().wakeOnMotion)
            put("recordOnBattery", settings.current().recordOnBattery)
            put("hasMotionSensor", significantMotion != null)
        })

        enterInitialState()
    }

    // ----------------------------------------------------
    // Armed / recording
    // ----------------------------------------------------

    /**
     * Follows the battery as well as the plug.
     *
     * The broadcast receiver hears the moment power is connected or lost, which
     * is what the transitions hang off; this hears the level, which is what
     * decides how much of the logger keeps running.
     */
    private fun watchPowerState() {
        serviceScope.launch {
            powerStateProvider.observe().collect { power ->
                isCurrentlyCharging = power.charging
                currentPowerSource = power.source
                applyPowerTier(power.tier)
            }
        }
    }

    @Synchronized
    private fun applyPowerTier(tier: PowerTier) {
        if (tier == powerTier) return

        val previous = powerTier
        powerTier = tier

        writeSimpleEvent("power_tier_changed", JSONObject().apply {
            put("from", previous.name)
            put("to", tier.name)
        })

        updateNotification()

        when {
            // Nothing left to spend: back to waiting until there is power again.
            tier == PowerTier.PAUSED -> enterArmed()

            // Recovered enough to record, and nothing else says to wait.
            previous == PowerTier.PAUSED && canRecordNow() && !shouldWaitForMotion() ->
                enterRecording()

            // The set of sensors worth running has changed under a live session.
            _loggerState.value == LoggerState.RECORDING -> {
                try {
                    sensorManager.unregisterListener(this@TelemetryForegroundService)
                } catch (_: Exception) {
                }

                registerSensors()
            }
        }
    }

    private fun enterInitialState() {
        when {
            // Parked because the power settings forbid recording right now.
            !canRecordNow() -> enterArmed()

            // Parked because the vehicle is not known to be moving.
            shouldWaitForMotion() -> enterArmed()

            else -> enterRecording()
        }
    }

    /** Whether the power settings allow recording at this moment. */
    private fun canRecordNow(): Boolean =
        isCurrentlyCharging || settings.current().recordOnBattery

    /**
     * Whether the vehicle standing still should mean waiting rather than
     * recording.
     *
     * Without the hardware trigger nothing would ever end the wait, so a device
     * that lacks one records continuously instead - the old behaviour, which is
     * worse but at least keeps working.
     */
    private fun shouldWaitForMotion(): Boolean {
        if (!settings.current().wakeOnMotion) return false

        if (significantMotion == null) {
            Log.w("Telemetry", "No significant motion sensor; recording continuously")
            return false
        }

        return true
    }

    /** Parked: everything expensive off, only the motion trigger listening. */
    @Synchronized
    private fun enterArmed() {
        /*
         * The trigger is one-shot and has unregistered itself by the time it is
         * handled, so an already-parked logger still needs it put back.
         */
        if (_loggerState.value == LoggerState.ARMED) {
            rearmMotionTrigger()
            return
        }

        stopRecordingHardware()
        rearmMotionTrigger()

        _loggerState.value = LoggerState.ARMED
        _locationStatus.value = TelemetryLocationStatus(hasFix = false)

        writeSimpleEvent("logger_armed", JSONObject().apply {
            put("movementWasConfirmed", movementConfirmed)
            put("onPower", isCurrentlyCharging)
        })
        updateNotification()
    }

    /**
     * Puts the one-shot motion trigger back, if there is one to put back.
     *
     * False means nothing will wake the logger by movement - no such sensor, or
     * the setting is off - and only power can promote it out of waiting.
     */
    private fun rearmMotionTrigger(): Boolean {
        if (!settings.current().wakeOnMotion) return false

        val sensor = significantMotion ?: return false

        return sensorManager.requestTriggerSensor(motionTrigger, sensor)
    }

    /** Moving: sensors, GPS and the flush loop, with the CPU held awake. */
    @Synchronized
    private fun enterRecording() {
        if (_loggerState.value == LoggerState.RECORDING) return

        significantMotion?.let { sensorManager.cancelTriggerSensor(motionTrigger, it) }

        acquireWakeLock()
        registerSensors()
        requestLocationUpdates()

        // Assumed moving until proven otherwise, or it would re-arm at once.
        lastMovementAt = SystemClock.elapsedRealtime()
        movementConfirmed = false
        _loggerState.value = LoggerState.RECORDING

        writeSimpleEvent("logger_recording", JSONObject())

        workerHandler?.removeCallbacks(flushRunnable)
        workerHandler?.post(flushRunnable)
        updateNotification()
    }

    private fun stopRecordingHardware() {
        workerHandler?.removeCallbacks(flushRunnable)

        try {
            sensorManager.unregisterListener(this)
        } catch (_: Exception) {
        }

        try {
            locationManager.removeUpdates(locationListener)
        } catch (_: Exception) {
        }

        releaseWakeLock()
    }

    /*
     * No timeout on purpose: the lock lasts exactly as long as recording does,
     * which is a journey, and releasing it early is the failure this exists to
     * prevent.
     */
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager

        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RESTART) {
            Log.i("Telemetry", "Restart requested")
            writeSimpleEvent("service_restarted", JSONObject())

            stopRecordingHardware()
            significantMotion?.let { sensorManager.cancelTriggerSensor(motionTrigger, it) }

            // Momentary, and only so the guards in enterArmed/enterRecording do
            // not mistake the current state for the one being asked for.
            _loggerState.value = LoggerState.OFF

            enterInitialState()
        }

        // Good for a long-running logger service
        return START_STICKY
    }

    override fun onDestroy() {
        isRunning.set(false)
        _loggerState.value = LoggerState.OFF

        significantMotion?.let { sensorManager.cancelTriggerSensor(motionTrigger, it) }
        releaseWakeLock()

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

    /**
     * Registers what this power tier still justifies.
     *
     * The accelerometer stays to the end because it is what a heading and any
     * sense of movement rest on; the barometer, magnetometer and gyroscope
     * describe a position rather than establish it, so they are the first to
     * go.
     */
    private fun registerSensors() {
        accelerometer?.let {
            sensorManager.registerListener(
                this,
                it,
                SENSOR_SAMPLING_US,
                SENSOR_SAMPLING_US * SENSOR_BATCH_LATENCY_FACTOR,
                workerHandler
            )
        }

        gyroscope?.takeIf { runsDecorativeSensors() }?.let {
            sensorManager.registerListener(
                this,
                it,
                SENSOR_SAMPLING_US,
                SENSOR_SAMPLING_US * SENSOR_BATCH_LATENCY_FACTOR,
                workerHandler
            )
        }

        magnetometer?.takeIf { runsDecorativeSensors() }?.let {
            sensorManager.registerListener(
                this,
                it,
                SENSOR_SAMPLING_US,
                SENSOR_SAMPLING_US * SENSOR_BATCH_LATENCY_FACTOR,
                workerHandler
            )
        }

        pressureSensor?.takeIf { runsDecorativeSensors() }?.let {
            sensorManager.registerListener(
                this,
                it,
                SENSOR_SAMPLING_US,
                SENSOR_SAMPLING_US * SENSOR_BATCH_LATENCY_FACTOR,
                workerHandler
            )
        }

    }

    /** Whether this tier still runs the sensors that merely decorate a fix. */
    private fun runsDecorativeSensors(): Boolean = powerTier < PowerTier.LOCATION_ONLY

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
                    GPS_UPDATE_INTERVAL_MS,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }

            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    NETWORK_UPDATE_INTERVAL_MS,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
        } catch (e: SecurityException) {
            Log.e("Telemetry", "Location permission was revoked underneath us", e)
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
            headingDegrees = (azimuthDeg + FULL_TURN_DEGREES) % FULL_TURN_DEGREES
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
            speedKmh = location?.speed?.times(MPS_TO_KMH),
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

                    reviewUploadBacklog()
                }
            } catch (e: Exception) {
                Log.e("Telemetry", "Room insert failed", e)
            }
        }

    }

    /**
     * Wakes the uploader when the backlog justifies it, and warns when that
     * backlog has stopped draining.
     *
     * Both questions are answered from one scan of the table. The warning is
     * raised here rather than in the uploader because the failure worth
     * catching includes the uploader never running at all - a constraint that
     * is never satisfied, or work that is never enqueued, produces no failed
     * upload to notice.
     */
    private suspend fun reviewUploadBacklog() {
        val progress = telemetryDao.getUploadProgress(UPLOAD_MAX_ATTEMPTS)

        if (progress.pendingRows >= UPLOAD_TRIGGER_PENDING_ROWS) {
            WifiUploadScheduler.enqueue(this)
        }

        val now = System.currentTimeMillis()
        val waiting = progress.waitingMs(now)

        if (waiting == null || waiting < UPLOAD_SILENCE_WARNING_MS) {
            /*
             * Telemetry is moving again, or there is none to move. Clearing
             * unconditionally rather than only when a warning is up: the
             * process may have died and been restarted since it was posted,
             * and the notification outlives the field that remembers it.
             */
            uploadSilenceNotifier.clear()
            uploadSilenceWarnedAt = null

            return
        }

        val warnedAt = uploadSilenceWarnedAt

        if (warnedAt != null && now - warnedAt < UPLOAD_SILENCE_RENOTIFY_MS) return

        /*
         * Only now, with the silence established, is the server asked what is
         * wrong with it - the answer decides the wording, and this is rare
         * enough for a network round trip to cost nothing.
         */
        val health = ServerHealthChecker(settings) {
            DeviceIdProvider.getOrCreateDeviceId(this)
        }.check()

        uploadSilenceNotifier.warn(
            uploadSilenceMessage(health, settings.current(), progress.pendingRows, waiting)
        )

        uploadSilenceWarnedAt = now
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
        val settings = settings.current()

        if (!settings.liveUploadEnabled) return

        /*
         * Only on power. A live push keeps the radio awake for the length of a
         * drive, which is not something to do to a phone running on its own
         * battery unless it was asked for.
         */
        if (!isCurrentlyCharging) return

        /*
         * The batch uploader gets its network rule from WorkManager's
         * constraints; a live push is a plain HTTP call and had none, so it
         * would happily spend mobile data while the batch path was refusing to.
         */
        if (settings.wifiOnly && !isOnUnmeteredNetwork()) return

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
                UploadOutcome.STORED -> {
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

    /**
     * Whether the vehicle has been still long enough to stop recording it.
     *
     * Only meaningful while recording, and only when something is able to start
     * it again.
     */
    private fun shouldReturnToArmed(): Boolean {
        if (_loggerState.value != LoggerState.RECORDING) return false
        if (!shouldWaitForMotion()) return false

        /*
         * An unproven session gets the short window: something shook the phone,
         * and if GPS has not seen it travelling by now it very likely was not
         * the car pulling away. A confirmed journey gets the long one, so
         * traffic lights and level crossings do not end it.
         */
        val timeout = if (movementConfirmed) MOTION_IDLE_TIMEOUT_MS else MOTION_CONFIRM_WINDOW_MS

        return SystemClock.elapsedRealtime() - lastMovementAt > timeout
    }

    private fun isOnUnmeteredNetwork(): Boolean {
        val manager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false

        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
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
            val speedKmh = (loc.speed * MPS_TO_KMH).roundToInt()
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
        val base = when (_loggerState.value) {
            LoggerState.RECORDING -> "Logging active"
            LoggerState.ARMED -> "Waiting for movement"
            LoggerState.OFF -> "Stopped"
        }

        // Being cut back looks identical to being broken unless it is said.
        val status = if (powerTier == PowerTier.FULL) {
            base
        } else {
            "$base (battery saving: ${powerTier.name.lowercase().replace('_', ' ')})"
        }

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(
            NOTIFICATION_ID,
            buildNotification(status, gpsPart, powerPart, headingPart)
        )
    }
}
