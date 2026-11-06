package org.isayme.callstats.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.isayme.callstats.data.SettingsRepository
import org.isayme.callstats.permission.PermissionStatus

/**
 * Restores monitoring after a reboot or an app update. Android 12 explicitly exempts
 * BOOT_COMPLETED and MY_PACKAGE_REPLACED from the background foreground-service start limit.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        AppCoroutineScope.scope.launch {
            try {
                val repository = SettingsRepository(context.applicationContext)
                val enabled = repository.monitorEnabled.first()
                val permissions = PermissionStatus.read(context)
                Log.i(
                    TAG,
                    "restore requested: enabled=$enabled criticalGranted=${permissions.criticalGranted}",
                )
                if (enabled && permissions.criticalGranted) {
                    MonitorController.start(context)
                }
            } catch (error: Exception) {
                Log.e(TAG, "failed to restore monitoring", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
