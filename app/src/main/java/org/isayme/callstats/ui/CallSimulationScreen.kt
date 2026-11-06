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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.isayme.callstats.R
import org.isayme.callstats.call.CallEventBus
import org.isayme.callstats.call.PhoneState
import org.isayme.callstats.call.PhoneStateEvent
import org.isayme.callstats.service.MonitorStatus

/**
 * Debug-only helper. It injects the same events the PHONE_STATE receiver produces so the
 * real state machine, statistics engine and overlay can be exercised without a call.
 */
@Composable
fun CallSimulationScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val running by MonitorStatus.running.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var number by rememberSaveable { mutableStateOf("") }
    var simulating by remember { mutableStateOf(false) }
    var statusRes by remember { mutableStateOf<Int?>(null) }

    fun simulate(incoming: Boolean) {
        if (!running || number.isBlank() || simulating) {
            return
        }
        simulating = true
        statusRes = R.string.simulation_running
        scope.launch {
            if (incoming) {
                CallEventBus.emit(PhoneStateEvent(PhoneState.RINGING, number))
                delay(2_000)
                CallEventBus.emit(PhoneStateEvent(PhoneState.OFFHOOK, number))
                delay(6_000)
            } else {
                CallEventBus.emit(PhoneStateEvent(PhoneState.OFFHOOK, number))
                delay(6_000)
            }
            CallEventBus.emit(PhoneStateEvent(PhoneState.IDLE, null))
            simulating = false
            statusRes = R.string.simulation_finished
        }
    }

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
            text = stringResource(R.string.simulation_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(R.string.simulation_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = number,
            onValueChange = { number = it },
            label = { Text(stringResource(R.string.simulation_number)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { simulate(incoming = true) },
                enabled = running && number.isNotBlank() && !simulating,
            ) {
                Text(stringResource(R.string.simulation_incoming))
            }
            Button(
                onClick = { simulate(incoming = false) },
                enabled = running && number.isNotBlank() && !simulating,
            ) {
                Text(stringResource(R.string.simulation_outgoing))
            }
        }
        if (!running) {
            Text(
                text = stringResource(R.string.simulation_requires_monitor),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        statusRes?.let { res ->
            Text(
                text = stringResource(res),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
