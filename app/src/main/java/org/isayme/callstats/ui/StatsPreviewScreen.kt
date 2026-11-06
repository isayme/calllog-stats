package org.isayme.callstats.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.isayme.callstats.R
import org.isayme.callstats.data.AppSettings
import org.isayme.callstats.data.SettingsRepository
import org.isayme.callstats.stats.CallMoment
import org.isayme.callstats.stats.CallStats
import org.isayme.callstats.stats.CallStatsCalculator
import org.isayme.callstats.stats.CallLogRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private sealed interface PreviewState {
    data object Idle : PreviewState
    data object Loading : PreviewState
    data class Loaded(val stats: CallStats) : PreviewState
    data object InvalidNumber : PreviewState
    data class Failed(val message: String) : PreviewState
}

@Composable
fun StatsPreviewScreen(repository: SettingsRepository, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    var number by rememberSaveable { mutableStateOf("") }
    var state by remember { mutableStateOf<PreviewState>(PreviewState.Idle) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = onBack) {
            Text(stringResource(R.string.about_back))
        }
        Text(
            text = stringResource(R.string.stats_preview_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.stats_preview_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = number,
            onValueChange = { number = it },
            label = { Text(stringResource(R.string.stats_preview_number)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                state = PreviewState.Loading
                scope.launch {
                    val entries = withContext(Dispatchers.IO) {
                        CallLogRepository(context).loadEntries()
                    }
                    entries.fold(
                        onSuccess = { rows ->
                            val stats = withContext(Dispatchers.Default) {
                                CallStatsCalculator().calculate(
                                    rawNumber = number,
                                    entries = rows,
                                    nowMillis = System.currentTimeMillis(),
                                    settings = settings,
                                )
                            }
                            state = if (stats == null) {
                                PreviewState.InvalidNumber
                            } else {
                                PreviewState.Loaded(stats)
                            }
                        },
                        onFailure = { error ->
                            state = PreviewState.Failed(error.message.orEmpty())
                        },
                    )
                }
            },
            enabled = number.isNotBlank() && state != PreviewState.Loading,
        ) {
            Text(stringResource(R.string.stats_preview_calculate))
        }

        when (val current = state) {
            PreviewState.Idle -> Unit
            PreviewState.Loading -> Text(stringResource(R.string.stats_preview_loading))
            PreviewState.InvalidNumber -> Text(
                text = stringResource(R.string.stats_preview_invalid_number),
                color = MaterialTheme.colorScheme.error,
            )

            is PreviewState.Failed -> Text(
                text = stringResource(R.string.stats_preview_failed, current.message),
                color = MaterialTheme.colorScheme.error,
            )

            is PreviewState.Loaded -> StatsCard(current.stats)
        }
    }
}

@Composable
private fun StatsCard(stats: CallStats) {
    Card {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = statisticalHeader(stats),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(text = stats.displayNumber, style = MaterialTheme.typography.bodyLarge)
            HorizontalDivider()

            StatRow(
                stringResource(R.string.stats_preview_first_incoming),
                yesNo(stats.isFirstIncomingCall),
            )
            StatRow(
                stringResource(R.string.stats_preview_first_contact),
                yesNo(stats.isFirstContact),
            )
            StatRow(
                stringResource(R.string.stats_preview_period),
                stringResource(R.string.stats_preview_period_value, stats.statsPeriodMonths),
            )
            StatRow(
                stringResource(R.string.stats_preview_incoming),
                stringResource(
                    R.string.stats_preview_incoming_value,
                    stats.windowIncomingCount,
                    stats.windowAnsweredIncomingCount,
                    stats.windowMissedCount,
                ),
            )
            StatRow(
                stringResource(R.string.stats_preview_outgoing),
                stringResource(
                    R.string.stats_preview_outgoing_value,
                    stats.windowOutgoingCount,
                    stats.windowConnectedOutgoingCount,
                ),
            )
            StatRow(
                stringResource(R.string.stats_preview_durations),
                stringResource(
                    R.string.stats_preview_durations_value,
                    stats.windowIncomingDurationSeconds,
                    stats.windowOutgoingDurationSeconds,
                ),
            )
            StatRow(
                stringResource(R.string.stats_preview_last_incoming),
                momentText(stats.lastAnsweredIncoming),
            )
            StatRow(
                stringResource(R.string.stats_preview_last_outgoing),
                momentText(stats.lastOutgoing),
            )
            val repeatText = stringResource(
                R.string.stats_preview_repeat_value,
                stats.repeatWindowCount,
            ) + if (stats.repeatWindowHasMissed) {
                stringResource(R.string.stats_preview_repeat_with_missed)
            } else {
                ""
            }
            StatRow(
                stringResource(R.string.stats_preview_repeat),
                repeatText,
            )
            StatRow(
                stringResource(R.string.stats_preview_all_time),
                stringResource(
                    R.string.stats_preview_all_time_value,
                    stats.allTimeIncomingCount,
                    stats.allTimeOutgoingCount,
                ),
            )
        }
    }
}

@Composable
private fun statisticalHeader(stats: CallStats): String = when {
    stats.isFirstIncomingCall -> stringResource(R.string.stats_preview_header_first_incoming)
    stats.windowIncomingCount == 0 -> stringResource(
        R.string.stats_preview_header_quiet,
        stats.allTimeIncomingCount,
    )
    else -> stringResource(
        R.string.stats_preview_header_nth,
        stats.windowIncomingCount + 1,
    )
}

@Composable
private fun yesNo(value: Boolean): String = stringResource(
    if (value) R.string.stats_preview_yes else R.string.stats_preview_no,
)

@Composable
private fun momentText(moment: CallMoment?): String {
    if (moment == null) {
        return stringResource(R.string.stats_preview_none)
    }
    val text = stringResource(
        R.string.stats_preview_last_value,
        formatTime(moment.timestampMillis),
        moment.durationSeconds,
    )
    return if (moment.withinStatsPeriod) {
        text
    } else {
        text + stringResource(R.string.stats_preview_outside_period)
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

private val previewTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun formatTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(previewTimeFormatter)
