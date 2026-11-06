package org.isayme.callstats.data

/**
 * User-configurable settings. Defaults and ranges mirror docs/requirements.md.
 * Values read from disk are clamped so a corrupted preference never breaks the UI.
 */
data class AppSettings(
    val repeatCallWindowMinutes: Int = DEFAULT_REPEAT_CALL_WINDOW_MINUTES,
    val statsPeriodMonths: Int = DEFAULT_STATS_PERIOD_MONTHS,
    val normalizeNumbers: Boolean = DEFAULT_NORMALIZE_NUMBERS,
    val showOverlayOnOutgoing: Boolean = DEFAULT_SHOW_OVERLAY_ON_OUTGOING,
) {
    companion object {
        const val MIN_REPEAT_CALL_WINDOW_MINUTES = 1
        const val MAX_REPEAT_CALL_WINDOW_MINUTES = 60
        const val DEFAULT_REPEAT_CALL_WINDOW_MINUTES = 10

        const val MIN_STATS_PERIOD_MONTHS = 1
        const val MAX_STATS_PERIOD_MONTHS = 24
        const val DEFAULT_STATS_PERIOD_MONTHS = 6

        const val DEFAULT_NORMALIZE_NUMBERS = true
        const val DEFAULT_SHOW_OVERLAY_ON_OUTGOING = true

        fun clampRepeatCallWindow(minutes: Int): Int =
            minutes.coerceIn(MIN_REPEAT_CALL_WINDOW_MINUTES, MAX_REPEAT_CALL_WINDOW_MINUTES)

        fun clampStatsPeriod(months: Int): Int =
            months.coerceIn(MIN_STATS_PERIOD_MONTHS, MAX_STATS_PERIOD_MONTHS)
    }
}
