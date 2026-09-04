package com.anonymus09.carsensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anonymus09.carsensors.data.PowerStateProvider
import com.anonymus09.carsensors.data.SettingsRepository
import com.anonymus09.carsensors.work.WifiUploadScheduler

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val autoStart = SettingsRepository(context).current().autoStartOnBoot
            val charging = PowerStateProvider(context).current().charging

            if (autoStart && charging) {
                WifiUploadScheduler.enqueue(context)
                TelemetryForegroundService.startService(context)
            }
        }
    }
}
