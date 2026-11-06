package org.isayme.callstats.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import org.isayme.callstats.MainActivity
import org.isayme.callstats.R
import org.isayme.callstats.call.CallEvent
import org.isayme.callstats.call.CallEventBus
import org.isayme.callstats.call.CallStateMachine
import org.isayme.callstats.call.PhoneStateEvent
import org.isayme.callstats.data.AppSettings
import org.isayme.callstats.data.SettingsRepository
import org.isayme.callstats.overlay.CallOverlayController
import org.isayme.callstats.overlay.CallOverlayCoordinator
import org.isayme.callstats.util.NumberMasking

/**
 * Foreground service skeleton for M1. Call state handling is added in M2; for now the
 * service only owns its lifecycle, notification and the persisted enabled flag.
 */
class CallMonitorService : Service() {
    private lateinit var repository: SettingsRepository
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val stateMachine = CallStateMachine()
    private var missingNumberCount = 0
    private var numberMissingForCurrentCall = false
    private lateinit var overlayController: CallOverlayController
    private lateinit var overlayCoordinator: CallOverlayCoordinator

    override fun onCreate() {
        super.onCreate()
        repository = SettingsRepository(applicationContext)
        overlayController = CallOverlayController(applicationContext, repository)
        overlayCoordinator = CallOverlayCoordinator(
            context = applicationContext,
            overlay = overlayController,
            repository = repository,
        )
        createNotificationChannel()
        MonitorStatus.setRunning(true)

        serviceScope.launch {
            merge(
                CallEventBus.events.map { MonitorInput.Event(it) },
                repository.settings.map { MonitorInput.Settings(it) },
            ).collect { input ->
                when (input) {
                    is MonitorInput.Event -> {
                        stateMachine.onEvent(input.event.state, input.event.number)
                            .forEach { event ->
                                logCallEvent(event)
                                overlayCoordinator.onCallEvent(event)
                            }
                    }

                    is MonitorInput.Settings -> overlayCoordinator.updateSettings(input.settings)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // The service is destroyed right after this, so persist on a process-wide scope.
            AppCoroutineScope.scope.launch { repository.setMonitorEnabled(false) }
            stopSelf()
            return START_NOT_STICKY
        }

        return try {
            startAsForeground()
            START_STICKY
        } catch (error: Exception) {
            Log.e(TAG, "unable to start foreground monitoring", error)
            stopSelf()
            START_NOT_STICKY
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        if (::overlayCoordinator.isInitialized) {
            overlayCoordinator.destroy()
        }
        if (::overlayController.isInitialized) {
            overlayController.destroy()
        }
        serviceScope.cancel()
        MonitorStatus.setRunning(false)
        super.onDestroy()
    }

    private fun logCallEvent(event: CallEvent) {
        when (event) {
            is CallEvent.Started -> {
                Log.i(
                    TAG,
                    "call started direction=${event.direction} number=${NumberMasking.mask(event.number)}",
                )
                // The platform always sends a first broadcast without the number extra,
                // so a missing number only counts if it is never resolved before the call ends.
                numberMissingForCurrentCall = event.number == null
            }

            is CallEvent.NumberResolved -> {
                numberMissingForCurrentCall = false
                Log.i(
                    TAG,
                    "number resolved direction=${event.direction} number=${NumberMasking.mask(event.number)}",
                )
            }

            is CallEvent.Answered -> {
                if (event.number != null) {
                    numberMissingForCurrentCall = false
                }
                Log.i(
                    TAG,
                    "call answered direction=${event.direction} number=${NumberMasking.mask(event.number)}",
                )
            }

            is CallEvent.WaitingStarted -> {
                numberMissingForCurrentCall = event.number == null
                Log.i(
                    TAG,
                    "call waiting number=${NumberMasking.mask(event.number)}",
                )
            }

            CallEvent.Ended -> {
                if (numberMissingForCurrentCall) {
                    missingNumberCount++
                    Log.w(TAG, "number unavailable for one call, total=$missingNumberCount")
                }
                numberMissingForCurrentCall = false
                Log.i(TAG, "call ended")
            }
        }
    }

    private fun startAsForeground() {
        // specialUse only exists from API 34; earlier versions accept the default type.
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            serviceType,
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_description)
            setShowBadge(false)
            enableVibration(false)
            setSound(null, null)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle(getString(R.string.notification_title))
        .setContentText(getString(R.string.notification_text))
        .setContentIntent(openAppIntent())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .addAction(
            R.drawable.ic_notification,
            getString(R.string.notification_action_stop),
            stopIntent(),
        )
        .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        REQUEST_OPEN_APP,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun stopIntent(): PendingIntent = PendingIntent.getService(
        this,
        REQUEST_STOP,
        Intent(this, CallMonitorService::class.java).setAction(ACTION_STOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private sealed interface MonitorInput {
        data class Event(val event: PhoneStateEvent) : MonitorInput
        data class Settings(val settings: AppSettings) : MonitorInput
    }

    companion object {
        const val ACTION_STOP = "org.isayme.callstats.action.STOP_MONITORING"

        private const val TAG = "CallMonitorService"
        private const val CHANNEL_ID = "call_monitor"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_OPEN_APP = 100
        private const val REQUEST_STOP = 101
    }
}
