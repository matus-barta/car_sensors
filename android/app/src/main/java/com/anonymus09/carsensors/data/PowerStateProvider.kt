package com.anonymus09.carsensors.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged

/** Whether the device is on power, and what it is plugged into. */
data class PowerState(
    val charging: Boolean = false,
    val source: String = SOURCE_UNKNOWN
) {
    companion object {
        const val SOURCE_UNKNOWN = "UNKNOWN"
        const val SOURCE_NOT_CHARGING = "NOT_CHARGING"
    }
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

    return PowerState(charging = charging, source = source)
}
