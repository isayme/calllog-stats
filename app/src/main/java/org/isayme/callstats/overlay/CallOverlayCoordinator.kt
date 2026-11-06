package org.isayme.callstats.overlay

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.isayme.callstats.call.CallDirection
import org.isayme.callstats.call.CallEvent
import org.isayme.callstats.data.AppSettings
import org.isayme.callstats.data.SettingsRepository
import org.isayme.callstats.stats.CallLogRepository
import org.isayme.callstats.stats.CallStatsCalculator
import org.isayme.callstats.stats.NumberNormalizer

/**
 * Turns call events into overlay content. Statistics are loaded off the event thread and
 * results from a previous call are ignored through a monotonically increasing token.
 */
class CallOverlayCoordinator(
    private val context: Context,
    private val overlay: CallOverlayController,
    private val repository: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var settings = AppSettings()

    private var activeNumber: String? = null
    private var activeDirection: CallDirection? = null
    private var statsJob: Job? = null
    private var token = 0

    @Synchronized
    fun updateSettings(newSettings: AppSettings) {
        val previous = settings
        settings = newSettings

        val number = activeNumber ?: return
        if (!newSettings.showOverlayOnOutgoing &&
            activeDirection == CallDirection.OUTGOING
        ) {
            hideInternal()
            return
        }
        if (previous.statsPeriodMonths != newSettings.statsPeriodMonths ||
            previous.repeatCallWindowMinutes != newSettings.repeatCallWindowMinutes ||
            previous.normalizeNumbers != newSettings.normalizeNumbers
        ) {
            reload(number)
        }
    }

    @Synchronized
    fun onCallEvent(event: CallEvent) {
        when (event) {
            is CallEvent.Started -> {
                if (event.direction == CallDirection.OUTGOING && !settings.showOverlayOnOutgoing) {
                    return
                }
                event.number?.let { showFor(it, event.direction) }
            }

            is CallEvent.NumberResolved -> {
                if (event.direction == CallDirection.OUTGOING && !settings.showOverlayOnOutgoing) {
                    return
                }
                showFor(event.number, event.direction)
            }

            is CallEvent.Answered -> {
                if (activeNumber != null) {
                    overlay.onAnswered()
                }
            }

            is CallEvent.WaitingStarted -> event.number?.let {
                showFor(it, CallDirection.INCOMING)
            }

            CallEvent.Ended -> hideInternal()
        }
    }

    @Synchronized
    fun destroy() {
        hideInternal()
        scope.cancel()
    }

    @Synchronized
    private fun showFor(number: String, direction: CallDirection) {
        // Hidden/unknown numbers and values without digits must never create a window.
        if (NumberNormalizer.normalize(number, settings.normalizeNumbers) == null) {
            hideInternal()
            return
        }
        activeNumber = number
        activeDirection = direction
        val currentToken = ++token
        overlay.show(number, direction, stats = null)
        loadStats(number, currentToken)
    }

    @Synchronized
    private fun reload(number: String) {
        val currentToken = ++token
        loadStats(number, currentToken)
    }

    @Synchronized
    private fun hideInternal() {
        token++
        statsJob?.cancel()
        statsJob = null
        activeNumber = null
        activeDirection = null
        overlay.hide()
    }

    @Synchronized
    private fun isCurrent(callToken: Int): Boolean = callToken == token

    private fun loadStats(number: String, callToken: Int) {
        statsJob?.cancel()
        statsJob = scope.launch {
            val entries = withContext(Dispatchers.IO) {
                CallLogRepository(context).loadEntries()
            }.getOrElse { error ->
                Log.e(TAG, "unable to load call log for overlay", error)
                if (isCurrent(callToken)) {
                    hideInternal()
                }
                return@launch
            }

            val stats = withContext(Dispatchers.Default) {
                CallStatsCalculator().calculate(
                    rawNumber = number,
                    entries = entries,
                    nowMillis = System.currentTimeMillis(),
                    settings = settings,
                )
            }

            if (!isCurrent(callToken)) {
                return@launch
            }
            if (stats == null) {
                hideInternal()
            } else {
                overlay.updateStats(stats)
            }
        }
    }

    private companion object {
        const val TAG = "CallOverlayCoordinator"
    }
}
