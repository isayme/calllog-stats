package org.isayme.callstats.stats

import android.content.Context
import android.provider.CallLog
import android.util.Log

class CallLogRepository(private val context: Context) {
    /**
     * Loads the full call log. First-time detection and "last call" fields need all rows,
     * not only the configured statistics window, so the query is intentionally unbounded.
     * The caller must run this on an IO dispatcher.
     */
    fun loadEntries(): Result<List<CallLogEntry>> = runCatching {
        val projection = arrayOf(
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
        )
        val entries = mutableListOf<CallLogEntry>()
        context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            null,
            null,
            "${CallLog.Calls.DATE} DESC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                entries += CallLogEntry(
                    number = cursor.getString(0),
                    type = cursor.getInt(1).toCallLogType(),
                    dateMillis = cursor.getLong(2),
                    durationSeconds = cursor.getLong(3),
                )
            }
        } ?: error("CallLog provider returned a null cursor")

        Log.i(TAG, "loaded ${entries.size} call log rows")
        entries
    }.onFailure { error ->
        Log.e(TAG, "failed to load call log", error)
    }

    private fun Int.toCallLogType(): CallLogType = when (this) {
        CallLog.Calls.INCOMING_TYPE -> CallLogType.INCOMING
        CallLog.Calls.OUTGOING_TYPE -> CallLogType.OUTGOING
        CallLog.Calls.MISSED_TYPE -> CallLogType.MISSED
        else -> CallLogType.OTHER
    }

    private companion object {
        const val TAG = "CallLogRepository"
    }
}
