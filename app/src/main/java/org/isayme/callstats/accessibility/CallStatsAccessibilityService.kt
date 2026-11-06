package org.isayme.callstats.accessibility

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Minimal accessibility service whose only purpose is to own a
 * TYPE_ACCESSIBILITY_OVERLAY window during locked incoming calls. It never reads
 * window content or inspects accessibility events.
 */
class CallStatsAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityOverlayBridge.service = this
        Log.i(TAG, "accessibility overlay service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (AccessibilityOverlayBridge.service === this) {
            AccessibilityOverlayBridge.service = null
        }
        Log.i(TAG, "accessibility overlay service destroyed")
        super.onDestroy()
    }

    private companion object {
        const val TAG = "CallStatsAccessibility"
    }
}
