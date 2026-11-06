package org.isayme.callstats.overlay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.isayme.callstats.R
import org.isayme.callstats.call.CallDirection
import org.isayme.callstats.stats.CallMoment
import org.isayme.callstats.stats.CallStats
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
internal fun CallOverlayContent(
    state: OverlayState,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit,
    onToggleExpanded: () -> Unit,
) {
    val stats = state.stats
    val warning = state.direction == CallDirection.INCOMING &&
        stats != null &&
        stats.repeatWindowCount > 1 &&
        stats.repeatWindowHasMissed

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = onDragEnd,
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount.x, dragAmount.y)
                    },
                )
            }
            .clickable(onClick = onToggleExpanded),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = state.number,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(
                        if (state.direction == CallDirection.INCOMING) {
                            R.string.overlay_direction_incoming
                        } else {
                            R.string.overlay_direction_outgoing
                        },
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = headerText(state),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = if (warning) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )

            if (!state.expanded) {
                Text(
                    text = collapsedText(state),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (stats == null) {
                Text(
                    text = stringResource(R.string.overlay_loading),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                HorizontalDivider()
                Text(
                    text = summaryText(state.direction, stats),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(
                        R.string.overlay_incoming_counts,
                        stats.windowIncomingCount,
                        stats.windowAnsweredIncomingCount,
                        stats.windowMissedCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(
                        R.string.overlay_outgoing_counts,
                        stats.windowOutgoingCount,
                        stats.windowConnectedOutgoingCount,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(
                        R.string.overlay_durations,
                        durationText(stats.windowIncomingDurationSeconds),
                        durationText(stats.windowOutgoingDurationSeconds),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = lastMomentText(
                        R.string.overlay_last_incoming_label,
                        stats.lastAnsweredIncoming,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = lastMomentText(
                        R.string.overlay_last_outgoing_label,
                        stats.lastOutgoing,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun headerText(state: OverlayState): String {
    val stats = state.stats ?: return stringResource(R.string.overlay_loading)
    return when (state.direction) {
        CallDirection.INCOMING -> when {
            stats.isFirstIncomingCall -> stringResource(R.string.overlay_first_incoming)
            stats.windowIncomingCount == 0 -> stringResource(
                R.string.overlay_quiet_incoming,
                stats.allTimeIncomingCount,
            )
            else -> stringResource(
                R.string.overlay_nth_incoming,
                stats.windowIncomingCount + 1,
            )
        }

        CallDirection.OUTGOING -> when {
            stats.isFirstContact -> stringResource(R.string.overlay_first_contact)
            stats.windowOutgoingCount == 0 -> stringResource(
                R.string.overlay_quiet_outgoing,
                stats.allTimeOutgoingCount,
            )
            else -> stringResource(
                R.string.overlay_nth_contact,
                stats.windowOutgoingCount + 1,
            )
        }
    }
}

@Composable
private fun summaryText(direction: CallDirection, stats: CallStats): String = when (direction) {
    CallDirection.INCOMING -> stringResource(
        R.string.overlay_summary_incoming,
        stats.statsPeriodMonths,
        stats.windowIncomingCount,
        stats.windowAnsweredIncomingCount,
    )

    CallDirection.OUTGOING -> stringResource(
        R.string.overlay_summary_outgoing,
        stats.statsPeriodMonths,
        stats.windowOutgoingCount,
        stats.windowConnectedOutgoingCount,
    )
}

@Composable
private fun collapsedText(state: OverlayState): String {
    val stats = state.stats ?: return stringResource(R.string.overlay_loading)
    return when (state.direction) {
        CallDirection.INCOMING -> stringResource(
            R.string.overlay_collapsed_incoming,
            stats.windowIncomingCount,
            stats.windowMissedCount,
        )

        CallDirection.OUTGOING -> stringResource(
            R.string.overlay_collapsed_outgoing,
            stats.windowOutgoingCount,
            stats.windowConnectedOutgoingCount,
        )
    }
}

@Composable
private fun lastMomentText(labelRes: Int, moment: CallMoment?): String {
    if (moment == null) {
        return stringResource(labelRes) + " " + stringResource(R.string.overlay_none)
    }
    val text = stringResource(labelRes) + " " + stringResource(
        R.string.overlay_last_value,
        formatTime(moment.timestampMillis),
        durationText(moment.durationSeconds),
    )
    return if (moment.withinStatsPeriod) {
        text
    } else {
        text + stringResource(R.string.overlay_outside_period)
    }
}

@Composable
private fun durationText(seconds: Long): String = when {
    seconds <= 0 -> stringResource(R.string.overlay_duration_seconds, 0)
    seconds < 60 -> stringResource(R.string.overlay_duration_seconds, seconds)
    else -> stringResource(
        R.string.overlay_duration_minutes,
        seconds / 60,
        seconds % 60,
    )
}

private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dateFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val fullDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

private fun formatTime(epochMillis: Long): String {
    val zone = ZoneId.systemDefault()
    val moment = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val now = Instant.now().atZone(zone)
    val today = now.toLocalDate()
    return when {
        moment.toLocalDate() == today -> moment.format(timeFormatter)
        moment.toLocalDate().year == now.year -> moment.format(dateFormatter)
        else -> moment.format(fullDateFormatter)
    }
}
