package org.isayme.callstats.call

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import org.isayme.callstats.service.MonitorStatus
import org.isayme.callstats.util.NumberMasking

/**
 * Receives ACTION_PHONE_STATE_CHANGED. The broadcast is protected and only the system sends it.
 * The receiver does not start the service: Android 12+ restricts background foreground-service
 * starts, so it only forwards to an already running service in the same process.
 */
class PhoneStateReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) {
            return
        }
        if (!MonitorStatus.running.value) {
            Log.i(TAG, "ignored state event: monitoring is not running")
            return
        }

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE).toPhoneState() ?: return
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val hasNumber = intent.hasExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        Log.i(
            TAG,
            "state=$state hasNumberExtra=$hasNumber number=${NumberMasking.mask(number)}",
        )
        CallEventBus.emit(PhoneStateEvent(state = state, number = number))
    }

    private fun String?.toPhoneState(): PhoneState? = when (this) {
        TelephonyManager.EXTRA_STATE_IDLE -> PhoneState.IDLE
        TelephonyManager.EXTRA_STATE_RINGING -> PhoneState.RINGING
        TelephonyManager.EXTRA_STATE_OFFHOOK -> PhoneState.OFFHOOK
        else -> null
    }

    private companion object {
        const val TAG = "PhoneStateReceiver"
    }
}
