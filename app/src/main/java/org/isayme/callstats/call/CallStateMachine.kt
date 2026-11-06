package org.isayme.callstats.call

enum class PhoneState {
    IDLE,
    RINGING,
    OFFHOOK,
}

enum class CallDirection {
    INCOMING,
    OUTGOING,
}

enum class CallPhase {
    IDLE,
    RINGING,
    OFFHOOK,
}

/** A phone state broadcast, already mapped from the platform extras. */
data class PhoneStateEvent(
    val state: PhoneState,
    val number: String?,
)

sealed interface CallEvent {
    data class Started(val direction: CallDirection, val number: String?) : CallEvent
    data class NumberResolved(val direction: CallDirection, val number: String) : CallEvent
    data class Answered(val direction: CallDirection, val number: String?) : CallEvent
    data class WaitingStarted(val number: String?) : CallEvent
    data object Ended : CallEvent
}

/**
 * Pure state machine for call detection. The platform may deliver the same transition twice,
 * once with a number and once without, so empty numbers never overwrite a known number.
 *
 * Outgoing calls are recognized as IDLE -> OFFHOOK without a preceding RINGING transition.
 * OFFHOOK -> RINGING is treated as call waiting; the rejected-waiting-call case cannot be
 * distinguished from answering it using PHONE_STATE alone, so it is not modelled separately.
 */
class CallStateMachine {
    private var phase = CallPhase.IDLE
    private var direction: CallDirection? = null
    private var number: String? = null

    val currentPhase: CallPhase
        get() = phase

    val currentDirection: CallDirection?
        get() = direction

    val currentNumber: String?
        get() = number

    fun onEvent(state: PhoneState, rawNumber: String?): List<CallEvent> {
        val incomingNumber = rawNumber?.takeIf { it.isNotBlank() }
        return when (phase) {
            CallPhase.IDLE -> onIdle(state, incomingNumber)
            CallPhase.RINGING -> onRinging(state, incomingNumber)
            CallPhase.OFFHOOK -> onOffhook(state, incomingNumber)
        }
    }

    private fun onIdle(state: PhoneState, incomingNumber: String?): List<CallEvent> = when (state) {
        PhoneState.IDLE -> emptyList()

        PhoneState.RINGING -> {
            phase = CallPhase.RINGING
            direction = CallDirection.INCOMING
            number = incomingNumber
            listOf(CallEvent.Started(CallDirection.INCOMING, incomingNumber))
        }

        PhoneState.OFFHOOK -> {
            phase = CallPhase.OFFHOOK
            direction = CallDirection.OUTGOING
            number = incomingNumber
            listOf(CallEvent.Started(CallDirection.OUTGOING, incomingNumber))
        }
    }

    private fun onRinging(state: PhoneState, incomingNumber: String?): List<CallEvent> = when (state) {
        PhoneState.RINGING -> {
            if (incomingNumber != null && number == null) {
                number = incomingNumber
                listOf(CallEvent.NumberResolved(CallDirection.INCOMING, incomingNumber))
            } else {
                emptyList()
            }
        }

        PhoneState.OFFHOOK -> {
            // Answering the incoming call; the number may only be present on this broadcast.
            val resolvedNumber = number ?: incomingNumber
            phase = CallPhase.OFFHOOK
            number = resolvedNumber
            listOf(CallEvent.Answered(CallDirection.INCOMING, resolvedNumber))
        }

        PhoneState.IDLE -> {
            reset()
            listOf(CallEvent.Ended)
        }
    }

    private fun onOffhook(state: PhoneState, incomingNumber: String?): List<CallEvent> = when (state) {
        PhoneState.RINGING -> {
            // Call waiting while another call is active.
            phase = CallPhase.RINGING
            direction = CallDirection.INCOMING
            number = incomingNumber
            listOf(CallEvent.WaitingStarted(incomingNumber))
        }

        PhoneState.OFFHOOK -> {
            if (incomingNumber != null && number == null) {
                number = incomingNumber
                listOf(
                    CallEvent.NumberResolved(
                        direction ?: CallDirection.OUTGOING,
                        incomingNumber,
                    ),
                )
            } else {
                emptyList()
            }
        }

        PhoneState.IDLE -> {
            reset()
            listOf(CallEvent.Ended)
        }
    }

    private fun reset() {
        phase = CallPhase.IDLE
        direction = null
        number = null
    }
}
