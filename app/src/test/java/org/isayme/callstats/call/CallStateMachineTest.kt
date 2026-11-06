package org.isayme.callstats.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStateMachineTest {
    private val machine = CallStateMachine()

    @Test
    fun `incoming call starts with the number`() {
        val events = machine.onEvent(PhoneState.RINGING, "13800138000")

        assertEquals(
            listOf(CallEvent.Started(CallDirection.INCOMING, "13800138000")),
            events,
        )
        assertEquals(CallPhase.RINGING, machine.currentPhase)
        assertEquals("13800138000", machine.currentNumber)
    }

    @Test
    fun `incoming call resolves a number that arrives on the second broadcast`() {
        machine.onEvent(PhoneState.RINGING, null)

        val events = machine.onEvent(PhoneState.RINGING, "13800138000")

        assertEquals(
            listOf(CallEvent.NumberResolved(CallDirection.INCOMING, "13800138000")),
            events,
        )
    }

    @Test
    fun `duplicate empty broadcast does not clear the known number`() {
        machine.onEvent(PhoneState.RINGING, "13800138000")

        val events = machine.onEvent(PhoneState.RINGING, "")

        assertTrue(events.isEmpty())
        assertEquals("13800138000", machine.currentNumber)
    }

    @Test
    fun `answering an incoming call reports answered`() {
        machine.onEvent(PhoneState.RINGING, "13800138000")

        val events = machine.onEvent(PhoneState.OFFHOOK, null)

        assertEquals(
            listOf(CallEvent.Answered(CallDirection.INCOMING, "13800138000")),
            events,
        )
        assertEquals(CallPhase.OFFHOOK, machine.currentPhase)
    }

    @Test
    fun `outgoing call is detected on idle to offhook`() {
        val events = machine.onEvent(PhoneState.OFFHOOK, "13900139000")

        assertEquals(
            listOf(CallEvent.Started(CallDirection.OUTGOING, "13900139000")),
            events,
        )
        assertEquals(CallDirection.OUTGOING, machine.currentDirection)
    }

    @Test
    fun `outgoing number is resolved when the first broadcast has no number`() {
        machine.onEvent(PhoneState.OFFHOOK, null)

        val events = machine.onEvent(PhoneState.OFFHOOK, "13900139000")

        assertEquals(
            listOf(CallEvent.NumberResolved(CallDirection.OUTGOING, "13900139000")),
            events,
        )
    }

    @Test
    fun `idle after a call ends resets the machine`() {
        machine.onEvent(PhoneState.RINGING, "13800138000")

        val events = machine.onEvent(PhoneState.IDLE, null)

        assertEquals(listOf(CallEvent.Ended), events)
        assertEquals(CallPhase.IDLE, machine.currentPhase)
        assertEquals(null, machine.currentNumber)
    }

    @Test
    fun `call waiting during an active call starts a new incoming call`() {
        machine.onEvent(PhoneState.OFFHOOK, "13900139000")

        val events = machine.onEvent(PhoneState.RINGING, "13800138000")

        assertEquals(listOf(CallEvent.WaitingStarted("13800138000")), events)
        assertEquals("13800138000", machine.currentNumber)
    }

    @Test
    fun `answering a waiting call reports answered`() {
        machine.onEvent(PhoneState.OFFHOOK, "13900139000")
        machine.onEvent(PhoneState.RINGING, "13800138000")

        val events = machine.onEvent(PhoneState.OFFHOOK, null)

        assertEquals(
            listOf(CallEvent.Answered(CallDirection.INCOMING, "13800138000")),
            events,
        )
    }
}
