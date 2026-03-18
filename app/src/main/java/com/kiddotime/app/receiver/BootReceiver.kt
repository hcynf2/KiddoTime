package com.kiddotime.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.kiddotime.app.service.AppMonitorService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Restart the monitoring service after phone reboots
            AppMonitorService.start(context)
        }
    }
}