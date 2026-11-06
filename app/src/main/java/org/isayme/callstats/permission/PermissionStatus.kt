package org.isayme.callstats.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Snapshot of the permissions required for call monitoring.
 * Battery optimization and autostart are optional and do not block enabling monitoring.
 */
data class PermissionStatus(
    val callLog: Boolean,
    val phoneState: Boolean,
    val overlay: Boolean,
    val notifications: Boolean,
    val batteryOptimizationIgnored: Boolean,
) {
    val criticalGranted: Boolean
        get() = callLog && phoneState && overlay && notifications

    companion object {
        fun read(context: Context): PermissionStatus {
            val powerManager = context.getSystemService(PowerManager::class.java)
            return PermissionStatus(
                callLog = context.hasPermission(Manifest.permission.READ_CALL_LOG),
                phoneState = context.hasPermission(Manifest.permission.READ_PHONE_STATE),
                overlay = Settings.canDrawOverlays(context),
                notifications = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                    context.hasPermission(Manifest.permission.POST_NOTIFICATIONS),
                batteryOptimizationIgnored =
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true,
            )
        }

        private fun Context.hasPermission(permission: String): Boolean =
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }
}
