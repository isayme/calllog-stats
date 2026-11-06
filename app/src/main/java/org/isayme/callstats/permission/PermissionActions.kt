package org.isayme.callstats.permission

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

object PermissionActions {
    fun openOverlaySettings(context: Context) {
        startOrIgnore(
            context,
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context)),
        )
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        val request = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            packageUri(context),
        )
        if (!tryStart(context, request)) {
            openBatteryOptimizationSettings(context)
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        if (!tryStart(context, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))) {
            openAppDetails(context)
        }
    }

    /**
     * There is no public autostart API. Try the known MIUI/HyperOS entry points and fall back
     * to the app details page when the ROM does not expose one.
     */
    fun openAutostartSettings(context: Context) {
        val opened = autostartIntents().any { intent -> tryStart(context, intent) }
        if (!opened) {
            openAppDetails(context)
        }
    }

    fun openAppDetails(context: Context) {
        startOrIgnore(
            context,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)),
        )
    }

    private fun autostartIntents(): List<Intent> = listOf(
        Intent().setComponent(
            ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity",
            ),
        ),
        Intent("miui.intent.action.OP_AUTO_START").addCategory(Intent.CATEGORY_DEFAULT),
    )

    private fun tryStart(context: Context, intent: Intent): Boolean = try {
        if (intent.resolveActivity(context.packageManager) == null) {
            false
        } else {
            context.startActivity(intent)
            true
        }
    } catch (_: ActivityNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }

    private fun startOrIgnore(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            openAppDetails(context)
        } catch (_: SecurityException) {
            openAppDetails(context)
        }
    }

    private fun packageUri(context: Context): Uri =
        Uri.parse("package:${context.packageName}")
}
