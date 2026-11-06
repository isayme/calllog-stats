package org.isayme.callstats.stats

enum class CallLogType {
    INCOMING,
    OUTGOING,
    MISSED,
    OTHER,
}

/** A single call log row already mapped away from Android platform types. */
data class CallLogEntry(
    val number: String?,
    val type: CallLogType,
    val dateMillis: Long,
    val durationSeconds: Long,
)

data class CallMoment(
    val timestampMillis: Long,
    val durationSeconds: Long,
    val withinStatsPeriod: Boolean,
)

/**
 * Statistics for one number. All window counts exclude the current call because the
 * platform writes the row only after the call ends. The repeat count includes the
 * current call, matching the "第 N 次来电" requirement.
 */
data class CallStats(
    val displayNumber: String,
    val isFirstIncomingCall: Boolean,
    val isFirstContact: Boolean,
    val statsPeriodMonths: Int,
    val repeatWindowMinutes: Int,
    val windowIncomingCount: Int,
    val windowAnsweredIncomingCount: Int,
    val windowMissedCount: Int,
    val windowOutgoingCount: Int,
    val windowConnectedOutgoingCount: Int,
    val windowIncomingDurationSeconds: Long,
    val windowOutgoingDurationSeconds: Long,
    val lastAnsweredIncoming: CallMoment?,
    val lastOutgoing: CallMoment?,
    val repeatWindowCount: Int,
    val repeatWindowHasMissed: Boolean,
    val allTimeIncomingCount: Int,
    val allTimeOutgoingCount: Int,
)
