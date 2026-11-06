package org.isayme.callstats.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AppSettingsTest {
    @Test
    fun `defaults match the product requirements`() {
        val settings = AppSettings()

        assertEquals(10, settings.repeatCallWindowMinutes)
        assertEquals(6, settings.statsPeriodMonths)
        assertEquals(true, settings.normalizeNumbers)
        assertEquals(true, settings.showOverlayOnOutgoing)
    }

    @Test
    fun `repeat call window is clamped to one through sixty minutes`() {
        assertEquals(1, AppSettings.clampRepeatCallWindow(0))
        assertEquals(1, AppSettings.clampRepeatCallWindow(-10))
        assertEquals(10, AppSettings.clampRepeatCallWindow(10))
        assertEquals(60, AppSettings.clampRepeatCallWindow(61))
    }

    @Test
    fun `stats period is clamped to one through twenty four months`() {
        assertEquals(1, AppSettings.clampStatsPeriod(0))
        assertEquals(6, AppSettings.clampStatsPeriod(6))
        assertEquals(24, AppSettings.clampStatsPeriod(25))
    }
}
