package org.isayme.callstats.util

import org.junit.Assert.assertEquals
import org.junit.Test

class NumberMaskingTest {
    @Test
    fun `masks regular numbers`() {
        assertEquals("176***57", NumberMasking.mask("17621203857"))
    }

    @Test
    fun `handles empty and sentinel values`() {
        assertEquals("(empty)", NumberMasking.mask(null))
        assertEquals("(empty)", NumberMasking.mask(""))
        assertEquals("(private)", NumberMasking.mask("-1"))
        assertEquals("(unknown)", NumberMasking.mask("-2"))
    }

    @Test
    fun `masks short numbers entirely`() {
        assertEquals("***", NumberMasking.mask("110"))
    }
}
