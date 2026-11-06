package org.isayme.callstats.call

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-local hand-off from the manifest broadcast receiver to the running foreground
 * service. Events are dropped when the service is not collecting, which is intentional:
 * the process may be created just to deliver a broadcast after the service was killed.
 */
object CallEventBus {
    private const val TAG = "CallEventBus"

    private val mutableEvents = MutableSharedFlow<PhoneStateEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<PhoneStateEvent> = mutableEvents.asSharedFlow()

    fun emit(event: PhoneStateEvent) {
        if (!mutableEvents.tryEmit(event)) {
            Log.w(TAG, "dropped ${event.state} event")
        }
    }
}
