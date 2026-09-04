package com.anonymus09.carsensors

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.anonymus09.carsensors.data.SettingsRepository
import com.anonymus09.carsensors.work.WifiUploadScheduler

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return

        if (
            action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            val settings = SettingsRepository(context).current()

            /*
             * Restores what was there before rather than starting from a
             * preference alone, and no longer insists on being on power: the
             * logger comes back armed, which costs almost nothing, and whether
             * it records is decided by movement and the power settings. The old
             * charging condition meant a phone that rebooted while parked never
             * came back at all.
             */
            if (settings.autoStartOnBoot && settings.loggerEnabled) {
                WifiUploadScheduler.enqueue(context)
                TelemetryForegroundService.startService(context)
            }
        }
    }
}
