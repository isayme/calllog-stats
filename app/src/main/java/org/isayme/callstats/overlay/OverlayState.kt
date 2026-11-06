package org.isayme.callstats.overlay

import org.isayme.callstats.call.CallDirection
import org.isayme.callstats.stats.CallStats

data class OverlayState(
    val number: String,
    val direction: CallDirection,
    val stats: CallStats?,
    val expanded: Boolean,
)
