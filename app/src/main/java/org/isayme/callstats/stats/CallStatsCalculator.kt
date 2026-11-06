package org.isayme.callstats.stats

import org.isayme.callstats.data.AppSettings
import java.time.Instant
import java.time.ZoneId

/**
 * Pure statistics calculator. Call log rows belonging to other numbers are ignored before
 * any counting, so the result only depends on the requested number, the rows and the
 * current settings.
 */
class CallStatsCalculator(
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    fun calculate(
        rawNumber: String?,
        entries: List<CallLogEntry>,
        nowMillis: Long,
        settings: AppSettings,
    ): CallStats? {
        val target = NumberNormalizer.normalize(rawNumber, settings.normalizeNumbers) ?: return null
        val displayNumber = rawNumber.orEmpty()

        val matches = entries.filter { entry ->
            NumberNormalizer.normalize(entry.number, settings.normalizeNumbers) == target
        }
        if (matches.isEmpty()) {
            return emptyStats(displayNumber, target, settings)
        }

        val statsCutoff = Instant.ofEpochMilli(nowMillis)
            .atZone(zoneId)
            .minusMonths(settings.statsPeriodMonths.toLong())
            .toInstant()
            .toEpochMilli()
        val repeatCutoff = nowMillis - settings.repeatCallWindowMinutes * 60_000L

        val withinPeriod = matches.filter { it.dateMillis >= statsCutoff }
        val repeatWindow = matches.filter { it.dateMillis >= repeatCutoff }

        val incoming = matches.filter { it.type == CallLogType.INCOMING || it.type == CallLogType.MISSED }
        val outgoing = matches.filter { it.type == CallLogType.OUTGOING }

        val windowIncoming = withinPeriod.filter {
            it.type == CallLogType.INCOMING || it.type == CallLogType.MISSED
        }
        val windowAnsweredIncoming = withinPeriod.filter {
            it.type == CallLogType.INCOMING && it.durationSeconds > 0
        }
        val windowMissed = withinPeriod.filter { it.type == CallLogType.MISSED }
        val windowOutgoing = withinPeriod.filter { it.type == CallLogType.OUTGOING }
        val windowConnectedOutgoing = withinPeriod.filter {
            it.type == CallLogType.OUTGOING && it.durationSeconds > 0
        }

        val lastAnsweredIncoming = incoming
            .filter { it.type == CallLogType.INCOMING }
            .maxByOrNull { it.dateMillis }
            ?.toMoment(statsCutoff)
        val lastOutgoing = outgoing
            .maxByOrNull { it.dateMillis }
            ?.toMoment(statsCutoff)

        val repeatWindowIncoming = repeatWindow.filter {
            it.type == CallLogType.INCOMING || it.type == CallLogType.MISSED
        }

        return CallStats(
            displayNumber = displayNumber,
            isFirstIncomingCall = incoming.isEmpty(),
            isFirstContact = incoming.isEmpty() && outgoing.isEmpty(),
            statsPeriodMonths = settings.statsPeriodMonths,
            repeatWindowMinutes = settings.repeatCallWindowMinutes,
            windowIncomingCount = windowIncoming.size,
            windowAnsweredIncomingCount = windowAnsweredIncoming.size,
            windowMissedCount = windowMissed.size,
            windowOutgoingCount = windowOutgoing.size,
            windowConnectedOutgoingCount = windowConnectedOutgoing.size,
            windowIncomingDurationSeconds = windowAnsweredIncoming.sumOf { it.durationSeconds },
            windowOutgoingDurationSeconds = windowConnectedOutgoing.sumOf { it.durationSeconds },
            lastAnsweredIncoming = lastAnsweredIncoming,
            lastOutgoing = lastOutgoing,
            repeatWindowCount = repeatWindowIncoming.size + 1,
            repeatWindowHasMissed = repeatWindowIncoming.any { it.type == CallLogType.MISSED },
            allTimeIncomingCount = incoming.size,
            allTimeOutgoingCount = outgoing.size,
        )
    }

    /**
     * An unknown number still needs first-time badges, but there are no rows to summarize.
     * The display number is kept so the overlay can show it.
     */
    private fun emptyStats(
        displayNumber: String,
        target: String,
        settings: AppSettings,
    ): CallStats = CallStats(
        displayNumber = displayNumber,
        isFirstIncomingCall = true,
        isFirstContact = true,
        statsPeriodMonths = settings.statsPeriodMonths,
        repeatWindowMinutes = settings.repeatCallWindowMinutes,
        windowIncomingCount = 0,
        windowAnsweredIncomingCount = 0,
        windowMissedCount = 0,
        windowOutgoingCount = 0,
        windowConnectedOutgoingCount = 0,
        windowIncomingDurationSeconds = 0,
        windowOutgoingDurationSeconds = 0,
        lastAnsweredIncoming = null,
        lastOutgoing = null,
        repeatWindowCount = 1,
        repeatWindowHasMissed = false,
        allTimeIncomingCount = 0,
        allTimeOutgoingCount = 0,
    )

    private fun CallLogEntry.toMoment(statsCutoff: Long) = CallMoment(
        timestampMillis = dateMillis,
        durationSeconds = durationSeconds,
        withinStatsPeriod = dateMillis >= statsCutoff,
    )
}
