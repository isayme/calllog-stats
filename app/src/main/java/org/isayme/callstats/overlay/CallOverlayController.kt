package org.isayme.callstats.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.util.Log
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.isayme.callstats.R
import org.isayme.callstats.call.CallDirection
import org.isayme.callstats.data.OverlayPosition
import org.isayme.callstats.data.SettingsRepository
import org.isayme.callstats.stats.CallStats
import org.isayme.callstats.ui.theme.CallStatsTheme

/**
 * Owns the TYPE_APPLICATION_OVERLAY window. All window operations run on the main thread.
 * The view is created when a call starts and removed when it ends, so no overlay is kept
 * alive while idle.
 */
class CallOverlayController(
    private val context: Context,
    private val repository: SettingsRepository,
) {
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val state = MutableStateFlow<OverlayState?>(null)

    private var composeView: ComposeView? = null
    private var viewOwner: OverlayViewOwner? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    private var expanded = true
    private var userExpanded = false
    private var collapseJob: Job? = null
    private var savedPosition: OverlayPosition? = null
    private var positionLoaded = false

    fun show(number: String, direction: CallDirection, stats: CallStats?) {
        mainScope.launch {
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "overlay permission missing, skipping window")
                return@launch
            }
            ensureView()
            expanded = true
            userExpanded = false
            cancelAutoCollapse()
            state.value = OverlayState(
                number = number,
                direction = direction,
                stats = stats,
                expanded = expanded,
            )
            applySavedPosition()
            composeView?.post { clampPosition() }
        }
    }

    fun updateStats(stats: CallStats) {
        mainScope.launch {
            state.update { current -> current?.copy(stats = stats) }
        }
    }

    /** Called when an incoming call is answered; collapses the card after a short delay. */
    fun onAnswered() {
        mainScope.launch {
            cancelAutoCollapse()
            collapseJob = mainScope.launch {
                delay(AUTO_COLLAPSE_DELAY_MS)
                if (!userExpanded) {
                    expanded = false
                    state.update { current -> current?.copy(expanded = false) }
                }
            }
        }
    }

    fun hide() {
        mainScope.launch { removeView() }
    }

    fun destroy() {
        mainScope.launch {
            removeView()
            mainScope.cancel()
        }
    }

    private fun ensureView() {
        if (composeView != null) {
            return
        }
        val owner = OverlayViewOwner()
        val themedContext = ContextThemeWrapper(context, R.style.Theme_CallStats)
        val view = ComposeView(themedContext).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent {
                CallStatsTheme {
                    val overlayState by state.collectAsState()
                    overlayState?.let { current ->
                        CallOverlayContent(
                            state = current,
                            onDrag = ::dragBy,
                            onDragEnd = ::persistPosition,
                            onToggleExpanded = ::toggleExpanded,
                        )
                    }
                }
            }
        }
        val params = createLayoutParams()
        try {
            windowManager.addView(view, params)
            owner.resume()
        } catch (error: Exception) {
            Log.e(TAG, "unable to add overlay window", error)
            owner.destroy()
            return
        }
        composeView = view
        viewOwner = owner
        layoutParams = params
    }

    private fun createLayoutParams(): WindowManager.LayoutParams {
        val density = context.resources.displayMetrics.density
        return WindowManager.LayoutParams(
            (CARD_WIDTH_DP * density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = savedPosition?.x ?: (MARGIN_DP * density).toInt()
            y = savedPosition?.y ?: (TOP_MARGIN_DP * density).toInt()
        }
    }

    private suspend fun applySavedPosition() {
        if (!positionLoaded) {
            savedPosition = repository.overlayPosition.first()
            positionLoaded = true
        }
        val position = savedPosition ?: return
        val params = layoutParams ?: return
        params.x = position.x
        params.y = position.y
        composeView?.let { view ->
            runCatching { windowManager.updateViewLayout(view, params) }
                .onFailure { Log.w(TAG, "failed to apply saved overlay position", it) }
        }
    }

    /**
     * With END gravity the window's right edge is at screenWidth - x, so dragging left
     * increases x. Positive dy moves the window down.
     */
    private fun dragBy(deltaX: Float, deltaY: Float) {
        val params = layoutParams ?: return
        val view = composeView ?: return
        val metrics = context.resources.displayMetrics
        val maxX = (metrics.widthPixels - view.width).coerceAtLeast(0)
        val maxY = (metrics.heightPixels - view.height).coerceAtLeast(0)
        params.x = (params.x - deltaX.toInt()).coerceIn(0, maxX)
        params.y = (params.y + deltaY.toInt()).coerceIn(0, maxY)
        runCatching { windowManager.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "failed to move overlay", it) }
    }

    private fun clampPosition() {
        val params = layoutParams ?: return
        val view = composeView ?: return
        if (view.width == 0 || view.height == 0) {
            return
        }
        val metrics = context.resources.displayMetrics
        params.x = params.x.coerceIn(0, (metrics.widthPixels - view.width).coerceAtLeast(0))
        params.y = params.y.coerceIn(0, (metrics.heightPixels - view.height).coerceAtLeast(0))
        runCatching { windowManager.updateViewLayout(view, params) }
            .onFailure { Log.w(TAG, "failed to clamp overlay", it) }
    }

    private fun persistPosition() {
        val params = layoutParams ?: return
        savedPosition = OverlayPosition(params.x, params.y)
        mainScope.launch {
            repository.saveOverlayPosition(params.x, params.y)
        }
    }

    private fun toggleExpanded() {
        expanded = !expanded
        userExpanded = true
        cancelAutoCollapse()
        state.update { current -> current?.copy(expanded = expanded) }
    }

    private fun cancelAutoCollapse() {
        collapseJob?.cancel()
        collapseJob = null
    }

    private fun removeView() {
        cancelAutoCollapse()
        composeView?.let { view ->
            runCatching { windowManager.removeView(view) }
                .onFailure { Log.w(TAG, "failed to remove overlay", it) }
        }
        viewOwner?.destroy()
        composeView = null
        viewOwner = null
        layoutParams = null
        state.value = null
    }

    private companion object {
        const val TAG = "CallOverlayController"
        const val CARD_WIDTH_DP = 300
        const val MARGIN_DP = 16
        const val TOP_MARGIN_DP = 64
        const val AUTO_COLLAPSE_DELAY_MS = 5_000L
    }
}
