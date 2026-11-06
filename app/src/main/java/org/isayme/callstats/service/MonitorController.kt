package org.isayme.callstats.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

object MonitorController {
    fun start(context: Context) {
        ContextCompat.startForegroundService(
            context,
            Intent(context, CallMonitorService::class.java),
        )
    }

    fun requestStop(context: Context) {
        // Only call this while the service is running; startService() would otherwise
        // create it just to stop it.
        context.startService(
            Intent(context, CallMonitorService::class.java)
                .setAction(CallMonitorService.ACTION_STOP),
        )
    }
}
