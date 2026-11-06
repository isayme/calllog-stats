package org.isayme.callstats.accessibility

/**
 * The accessibility service and the monitoring service run in the same process.
 * The overlay controller uses this bridge to request a TYPE_ACCESSIBILITY_OVERLAY
 * window, which is the only window type that may appear above the keyguard.
 */
object AccessibilityOverlayBridge {
    @Volatile
    var service: CallStatsAccessibilityService? = null

    val isAvailable: Boolean
        get() = service != null
}
