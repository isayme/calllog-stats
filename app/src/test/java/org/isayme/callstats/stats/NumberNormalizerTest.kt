package org.isayme.callstats.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberNormalizerTest {
    @Test
    fun `strips spaces and dashes`() {
        assertEquals("17621203857", NumberNormalizer.normalize("176 2120-3857", true))
    }

    @Test
    fun `drops 0086 and 86 country codes for eleven digit numbers`() {
        assertEquals("17621203857", NumberNormalizer.normalize("008617621203857", true))
        assertEquals("17621203857", NumberNormalizer.normalize("+8617621203857", true))
    }

    @Test
    fun `strict mode keeps the country code`() {
        assertEquals("8617621203857", NumberNormalizer.normalize("+8617621203857", false))
    }

    @Test
    fun `short numbers are matched exactly`() {
        assertEquals("110", NumberNormalizer.normalize("110", true))
        assertEquals("10086", NumberNormalizer.normalize("10086", true))
    }

    @Test
    fun `truncates extensions`() {
        assertEquals("01012345678", NumberNormalizer.normalize("01012345678,123", true))
        assertEquals("01012345678", NumberNormalizer.normalize("01012345678;42", false))
    }

    @Test
    fun `hidden numbers are not matchable`() {
        assertNull(NumberNormalizer.normalize("-1", true))
        assertNull(NumberNormalizer.normalize("-2", true))
        assertNull(NumberNormalizer.normalize("-3", true))
        assertNull(NumberNormalizer.normalize(null, true))
        assertNull(NumberNormalizer.normalize("", true))
    }
}
