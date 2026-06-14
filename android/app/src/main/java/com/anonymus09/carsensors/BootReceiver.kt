package com.anonymus09.carsensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anonymus09.carsensors.work.WifiUploadScheduler

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return


        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val autoStart = TelemetryForegroundService.isAutoStartOnBootEnabled(context)
            val charging = TelemetryForegroundService.isDeviceCharging(context)

            if (autoStart && charging) {
                WifiUploadScheduler.enqueue(context)
                TelemetryForegroundService.startService(context)
            }
        }
    }
}
