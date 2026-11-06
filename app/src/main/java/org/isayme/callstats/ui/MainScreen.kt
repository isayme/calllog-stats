package org.isayme.callstats.ui

import android.Manifest
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.isayme.callstats.R
import org.isayme.callstats.data.AppSettings
import org.isayme.callstats.data.SettingsRepository
import org.isayme.callstats.permission.PermissionActions
import org.isayme.callstats.permission.PermissionStatus
import org.isayme.callstats.service.MonitorController
import org.isayme.callstats.service.MonitorStatus

private val runtimePermissions = arrayOf(
    Manifest.permission.READ_CALL_LOG,
    Manifest.permission.READ_PHONE_STATE,
)

@Composable
fun MainScreen(
    repository: SettingsRepository,
    onOpenAbout: () -> Unit,
    onOpenStatsPreview: () -> Unit,
    onOpenCallSimulation: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val debuggable = remember {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    val settings by repository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val monitorEnabled by repository.monitorEnabled.collectAsStateWithLifecycle(initialValue = false)
    val running by MonitorStatus.running.collectAsStateWithLifecycle()
    var permissions by remember { mutableStateOf(PermissionStatus.read(context)) }

    fun refreshPermissions() {
        permissions = PermissionStatus.read(context)
    }

    val runtimePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { refreshPermissions() }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshPermissions() }

    LifecycleResumeEffect(Unit) {
        refreshPermissions()
        // A runtime permission can be revoked while the app is in the background.
        if (MonitorStatus.running.value && !PermissionStatus.read(context).criticalGranted) {
            scope.launch { repository.setMonitorEnabled(false) }
            MonitorController.requestStop(context)
        }
        onPauseOrDispose { }
    }

    val statusText = when {
        running -> stringResource(R.string.main_status_running)
        monitorEnabled -> stringResource(R.string.main_status_stopped_hint)
        else -> stringResource(R.string.main_status_off)
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.main_toggle_label),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = stringResource(R.string.main_toggle_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = running,
                            onCheckedChange = { enable ->
                                if (enable) {
                                    if (!permissions.criticalGranted) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                context.getString(R.string.snackbar_permissions_missing),
                                            )
                                        }
                                    } else {
                                        scope.launch { repository.setMonitorEnabled(true) }
                                        MonitorController.start(context)
                                    }
                                } else {
                                    scope.launch { repository.setMonitorEnabled(false) }
                                    if (MonitorStatus.running.value) {
                                        MonitorController.requestStop(context)
                                    }
                                }
                            },
                        )
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (running) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    if (!permissions.criticalGranted) {
                        Text(
                            text = stringResource(R.string.main_status_permissions_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    if (!permissions.batteryOptimizationIgnored) {
                        Text(
                            text = stringResource(R.string.main_battery_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.permission_section_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Card {
                Column {
                    PermissionRow(
                        title = stringResource(R.string.permission_call_log),
                        description = stringResource(R.string.permission_call_log_description),
                        granted = permissions.callLog,
                        grantedLabel = stringResource(R.string.permission_status_granted),
                        actionLabel = stringResource(R.string.permission_action_grant),
                        onAction = { runtimePermissionLauncher.launch(runtimePermissions) },
                    )
                    HorizontalDivider()
                    PermissionRow(
                        title = stringResource(R.string.permission_phone_state),
                        description = stringResource(R.string.permission_phone_state_description),
                        granted = permissions.phoneState,
                        grantedLabel = stringResource(R.string.permission_status_granted),
                        actionLabel = stringResource(R.string.permission_action_grant),
                        onAction = { runtimePermissionLauncher.launch(runtimePermissions) },
                    )
                    HorizontalDivider()
                    PermissionRow(
                        title = stringResource(R.string.permission_overlay),
                        description = stringResource(R.string.permission_overlay_description),
                        granted = permissions.overlay,
                        grantedLabel = stringResource(R.string.permission_status_granted),
                        actionLabel = stringResource(R.string.permission_action_settings),
                        onAction = { PermissionActions.openOverlaySettings(context) },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        HorizontalDivider()
                        PermissionRow(
                            title = stringResource(R.string.permission_notifications),
                            description = stringResource(R.string.permission_notifications_description),
                            granted = permissions.notifications,
                            grantedLabel = stringResource(R.string.permission_status_granted),
                            actionLabel = stringResource(R.string.permission_action_grant),
                            onAction = {
                                notificationPermissionLauncher.launch(
                                    Manifest.permission.POST_NOTIFICATIONS,
                                )
                            },
                        )
                    }
                    HorizontalDivider()
                    PermissionRow(
                        title = stringResource(R.string.permission_accessibility),
                        description = stringResource(R.string.permission_accessibility_description),
                        granted = permissions.accessibilityService,
                        grantedLabel = stringResource(R.string.permission_status_granted),
                        actionLabel = stringResource(R.string.permission_action_enable),
                        onAction = { PermissionActions.openAccessibilitySettings(context) },
                    )
                    HorizontalDivider()
                    PermissionRow(
                        title = stringResource(R.string.permission_battery),
                        description = stringResource(R.string.permission_battery_description),
                        granted = permissions.batteryOptimizationIgnored,
                        grantedLabel = stringResource(R.string.permission_status_battery_ignored),
                        actionLabel = stringResource(R.string.permission_action_settings),
                        onAction = { PermissionActions.requestIgnoreBatteryOptimizations(context) },
                    )
                    HorizontalDivider()
                    PermissionRow(
                        title = stringResource(R.string.permission_autostart),
                        description = stringResource(R.string.permission_autostart_description),
                        granted = null,
                        grantedLabel = stringResource(R.string.permission_status_granted),
                        actionLabel = stringResource(R.string.permission_action_configure),
                        onAction = { PermissionActions.openAutostartSettings(context) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    StepperRow(
                        title = stringResource(R.string.settings_repeat_window),
                        value = stringResource(
                            R.string.settings_repeat_window_value,
                            settings.repeatCallWindowMinutes,
                        ),
                        onDecrease = {
                            scope.launch {
                                repository.setRepeatCallWindowMinutes(
                                    settings.repeatCallWindowMinutes - 1,
                                )
                            }
                        },
                        onIncrease = {
                            scope.launch {
                                repository.setRepeatCallWindowMinutes(
                                    settings.repeatCallWindowMinutes + 1,
                                )
                            }
                        },
                        decreaseEnabled = settings.repeatCallWindowMinutes >
                            AppSettings.MIN_REPEAT_CALL_WINDOW_MINUTES,
                        increaseEnabled = settings.repeatCallWindowMinutes <
                            AppSettings.MAX_REPEAT_CALL_WINDOW_MINUTES,
                    )
                    HorizontalDivider()
                    StepperRow(
                        title = stringResource(R.string.settings_stats_period),
                        value = stringResource(
                            R.string.settings_stats_period_value,
                            settings.statsPeriodMonths,
                        ),
                        onDecrease = {
                            scope.launch {
                                repository.setStatsPeriodMonths(settings.statsPeriodMonths - 1)
                            }
                        },
                        onIncrease = {
                            scope.launch {
                                repository.setStatsPeriodMonths(settings.statsPeriodMonths + 1)
                            }
                        },
                        decreaseEnabled = settings.statsPeriodMonths >
                            AppSettings.MIN_STATS_PERIOD_MONTHS,
                        increaseEnabled = settings.statsPeriodMonths <
                            AppSettings.MAX_STATS_PERIOD_MONTHS,
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = stringResource(R.string.settings_normalize),
                        description = stringResource(R.string.settings_normalize_description),
                        checked = settings.normalizeNumbers,
                        onCheckedChange = { enabled ->
                            scope.launch { repository.setNormalizeNumbers(enabled) }
                        },
                    )
                    HorizontalDivider()
                    SwitchRow(
                        title = stringResource(R.string.settings_outgoing_overlay),
                        description = stringResource(R.string.settings_outgoing_overlay_description),
                        checked = settings.showOverlayOnOutgoing,
                        onCheckedChange = { enabled ->
                            scope.launch { repository.setShowOverlayOnOutgoing(enabled) }
                        },
                    )
                }
            }

            if (debuggable) {
                OutlinedButton(
                    onClick = onOpenStatsPreview,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.stats_preview_entry))
                }
                OutlinedButton(
                    onClick = onOpenCallSimulation,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.simulation_entry))
                }
            }

            OutlinedButton(
                onClick = onOpenAbout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.about_title))
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    granted: Boolean?,
    grantedLabel: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (granted == true) {
            Text(
                text = grantedLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else {
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun StepperRow(
    title: String,
    value: String,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    decreaseEnabled: Boolean,
    increaseEnabled: Boolean,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        TextButton(onClick = onDecrease, enabled = decreaseEnabled) {
            Text(
                text = stringResource(R.string.setting_decrease_symbol),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        TextButton(onClick = onIncrease, enabled = increaseEnabled) {
            Text(
                text = stringResource(R.string.setting_increase_symbol),
                style = MaterialTheme.typography.titleLarge,
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
