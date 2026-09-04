package com.anonymus09.carsensors.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import com.anonymus09.carsensors.util.AppConfig.BATTERY_LOCATION_ONLY_PERCENT
import com.anonymus09.carsensors.util.AppConfig.BATTERY_PAUSE_UPLOAD_PERCENT
import com.anonymus09.carsensors.util.AppConfig.BATTERY_REDUCE_RATE_PERCENT
import com.anonymus09.carsensors.util.AppConfig.BATTERY_STOP_RECORDING_PERCENT
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/** Whether the device is on power, what it is plugged into, and how full it is. */
data class PowerState(
    val charging: Boolean = false,
    val source: String = SOURCE_UNKNOWN,
    /** Null when the broadcast did not say, which is rare but possible. */
    val levelPercent: Int? = null
) {
    /**
     * What the logger should still be doing at this state of charge.
     *
     * On power there is nothing to conserve, so everything runs. Off power the
     * tiers come into force in the order set out in `AppConfig`.
     */
    val tier: PowerTier
        get() {
            if (charging || levelPercent == null) return PowerTier.FULL

            return when {
                levelPercent <= BATTERY_STOP_RECORDING_PERCENT -> PowerTier.PAUSED
                levelPercent <= BATTERY_LOCATION_ONLY_PERCENT -> PowerTier.LOCATION_ONLY
                levelPercent <= BATTERY_REDUCE_RATE_PERCENT -> PowerTier.REDUCED_RATE
                levelPercent <= BATTERY_PAUSE_UPLOAD_PERCENT -> PowerTier.NO_UPLOAD
                else -> PowerTier.FULL
            }
        }

    companion object {
        const val SOURCE_UNKNOWN = "UNKNOWN"
        const val SOURCE_NOT_CHARGING = "NOT_CHARGING"
    }
}

/** How much of itself the logger is still doing. Ordered by how much is left. */
enum class PowerTier {
    /** Everything. */
    FULL,

    /** Recording as usual, but nothing is sent until there is power again. */
    NO_UPLOAD,

    /** Samples written less often. */
    REDUCED_RATE,

    /** Only location and the accelerometer behind it; the rest are let go. */
    LOCATION_ONLY,

    /** Nothing recorded - back to waiting until the phone is charged. */
    PAUSED
}

/**
 * The device's power state, as a one-off reading or as a stream.
 *
 * The screen used to show a reading taken once during composition, behind a
 * "Refresh charging status" button, because nothing told it when the state
 * changed. ACTION_BATTERY_CHANGED does, and being sticky it also answers the
 * first question at registration.
 */
class PowerStateProvider(context: Context) {

    private val appContext = context.applicationContext

    fun current(): PowerState =
        appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            .toPowerState()

    fun observe(): Flow<PowerState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                trySend(intent.toPowerState())
            }
        }

        /*
         * ACTION_BATTERY_CHANGED is sticky, so registering hands back the
         * current value straight away and the first emission needs no separate
         * reading. It then fires for every change in level or temperature too,
         * hence distinctUntilChanged.
         */
        val sticky = appContext.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        trySend(sticky.toPowerState())

        awaitClose { appContext.unregisterReceiver(receiver) }
    }.distinctUntilChanged().conflate()
}

private fun Intent?.toPowerState(): PowerState {
    if (this == null) return PowerState()

    val status = getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
        status == BatteryManager.BATTERY_STATUS_FULL

    val source = when (getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)) {
        BatteryManager.BATTERY_PLUGGED_AC -> "AC"
        BatteryManager.BATTERY_PLUGGED_USB -> "USB"
        BatteryManager.BATTERY_PLUGGED_WIRELESS -> "WIRELESS"
        else -> PowerState.SOURCE_NOT_CHARGING
    }

    val level = getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = getIntExtra(BatteryManager.EXTRA_SCALE, -1)

    return PowerState(
        charging = charging,
        source = source,
        levelPercent = if (level >= 0 && scale > 0) level * 100 / scale else null
    )
}
