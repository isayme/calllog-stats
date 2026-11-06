package org.isayme.callstats.stats

import org.isayme.callstats.data.AppSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class CallStatsCalculatorTest {
    private val calculator = CallStatsCalculator(zoneId = ZoneOffset.UTC)
    private val settings = AppSettings()
    private val now = Instant.parse("2026-06-15T12:00:00Z").toEpochMilli()
    private val number = "17621203857"

    @Test
    fun `unknown number reports first incoming and first contact`() {
        val stats = calculator.calculate(number, emptyList(), now, settings)!!

        assertTrue(stats.isFirstIncomingCall)
        assertTrue(stats.isFirstContact)
        assertEquals(0, stats.windowIncomingCount)
        assertEquals(1, stats.repeatWindowCount)
    }

    @Test
    fun `window counts exclude rows outside the statistics period`() {
        val entries = listOf(
            entry(CallLogType.INCOMING, hoursAgo(1), duration = 60),
            entry(CallLogType.MISSED, hoursAgo(2)),
            entry(CallLogType.OUTGOING, hoursAgo(3), duration = 120),
            entry(CallLogType.INCOMING, monthsAgo(8), duration = 30),
            entry(CallLogType.MISSED, monthsAgo(8)),
            entry(CallLogType.OUTGOING, monthsAgo(8), duration = 90),
        )

        val stats = calculator.calculate(number, entries, now, settings)!!

        assertEquals(2, stats.windowIncomingCount)
        assertEquals(1, stats.windowAnsweredIncomingCount)
        assertEquals(1, stats.windowMissedCount)
        assertEquals(1, stats.windowOutgoingCount)
        assertEquals(1, stats.windowConnectedOutgoingCount)
        assertEquals(60, stats.windowIncomingDurationSeconds)
        assertEquals(120, stats.windowOutgoingDurationSeconds)
        assertEquals(4, stats.allTimeIncomingCount)
        assertEquals(2, stats.allTimeOutgoingCount)
        assertFalse(stats.isFirstIncomingCall)
    }

    @Test
    fun `last calls use the full log and mark whether they are inside the period`() {
        val entries = listOf(
            entry(CallLogType.INCOMING, monthsAgo(8), duration = 30),
            entry(CallLogType.OUTGOING, monthsAgo(7), duration = 90),
        )

        val stats = calculator.calculate(number, entries, now, settings)!!

        assertEquals(monthsAgo(8), stats.lastAnsweredIncoming?.timestampMillis)
        assertFalse(stats.lastAnsweredIncoming!!.withinStatsPeriod)
        assertEquals(monthsAgo(7), stats.lastOutgoing?.timestampMillis)
        assertFalse(stats.lastOutgoing!!.withinStatsPeriod)
    }

    @Test
    fun `repeat count only includes the configured repeat window`() {
        val entries = listOf(
            entry(CallLogType.MISSED, minutesAgo(3)),
            entry(CallLogType.INCOMING, minutesAgo(5), duration = 10),
            entry(CallLogType.INCOMING, hoursAgo(3), duration = 10),
        )

        val stats = calculator.calculate(number, entries, now, settings)!!

        assertEquals(3, stats.repeatWindowCount)
        assertTrue(stats.repeatWindowHasMissed)
    }

    @Test
    fun `repeat window without missed calls is not flagged`() {
        val entries = listOf(
            entry(CallLogType.INCOMING, minutesAgo(3), duration = 10),
        )

        val stats = calculator.calculate(number, entries, now, settings)!!

        assertEquals(2, stats.repeatWindowCount)
        assertFalse(stats.repeatWindowHasMissed)
    }

    @Test
    fun `formatted log numbers match the normalized current number`() {
        val entries = listOf(
            CallLogEntry(
                number = "176 2120 3857",
                type = CallLogType.INCOMING,
                dateMillis = hoursAgo(1),
                durationSeconds = 20,
            ),
        )

        val stats = calculator.calculate("+8617621203857", entries, now, settings)!!

        assertEquals(1, stats.windowIncomingCount)
    }

    @Test
    fun `hidden numbers produce no statistics`() {
        assertNull(calculator.calculate("-1", emptyList(), now, settings))
        assertNull(calculator.calculate(null, emptyList(), now, settings))
    }

    @Test
    fun `outgoing only history is not a first contact`() {
        val entries = listOf(entry(CallLogType.OUTGOING, hoursAgo(1), duration = 30))

        val stats = calculator.calculate(number, entries, now, settings)!!

        assertTrue(stats.isFirstIncomingCall)
        assertFalse(stats.isFirstContact)
        assertEquals(1, stats.allTimeOutgoingCount)
    }

    private fun entry(
        type: CallLogType,
        dateMillis: Long,
        duration: Long = 0,
    ) = CallLogEntry(number = number, type = type, dateMillis = dateMillis, durationSeconds = duration)

    private fun hoursAgo(hours: Long): Long = now - hours * 60 * 60 * 1000

    private fun minutesAgo(minutes: Long): Long = now - minutes * 60 * 1000

    private fun monthsAgo(months: Long): Long = Instant.ofEpochMilli(now)
        .atZone(ZoneOffset.UTC)
        .minusMonths(months)
        .toInstant()
        .toEpochMilli()
}
