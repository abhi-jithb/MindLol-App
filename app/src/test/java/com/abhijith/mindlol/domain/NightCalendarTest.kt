package com.abhijith.mindlol.domain

import com.abhijith.mindlol.domain.RulesTestTime.at
import com.abhijith.mindlol.domain.RulesTestTime.fri
import com.abhijith.mindlol.domain.RulesTestTime.thu
import com.abhijith.mindlol.domain.RulesTestTime.wed
import com.abhijith.mindlol.domain.RulesTestTime.zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightCalendarTest {

    @Test
    fun normalNight_startsAtBedtimeOnEveningDate_endsAtWakeNextMorning() {
        val night = NightCalendar.night(thu, Settings(), zone)
        assertEquals(at(thu, 23), night.startMillis)
        assertEquals(at(fri, 8), night.endMillis)
        assertFalse(night.overrideApplied)
    }

    @Test
    fun afterMidnightBedtime_belongsToPreviousEvening() {
        val settings = Settings(bedtimeMinutes = 2 * 60, wakeMinutes = 10 * 60)
        val night = NightCalendar.night(thu, settings, zone)
        assertEquals(at(fri, 2), night.startMillis)
        assertEquals(at(fri, 10), night.endMillis)
    }

    @Test
    fun override_appliesOnlyToTheNightItEnds() {
        // Set on Thursday evening: ends Friday 10:00, so it is Thursday's night.
        val override = TonightOverride(2 * 60, 10 * 60, expiresAtMillis = at(fri, 10))
        val settings = Settings(tonightOverride = override)

        val thursday = NightCalendar.night(thu, settings, zone)
        assertTrue(thursday.overrideApplied)
        assertEquals(at(fri, 2), thursday.startMillis)
        assertEquals(at(fri, 10), thursday.endMillis)

        val wednesday = NightCalendar.night(wed, settings, zone)
        assertFalse(wednesday.overrideApplied)
        assertEquals(at(wed, 23), wednesday.startMillis)

        val friday = NightCalendar.night(fri, settings, zone)
        assertFalse(friday.overrideApplied)
        assertEquals(at(fri, 23), friday.startMillis)
    }

    @Test
    fun equalBedAndWake_givesEmptyWindow() {
        val night = NightCalendar.night(thu, Settings(bedtimeMinutes = 8 * 60, wakeMinutes = 8 * 60), zone)
        assertEquals(night.startMillis, night.endMillis)
    }

    @Test
    fun day_runsMidnightToMidnight_withLimits() {
        val day = NightCalendar.day(thu, Settings(appLimits = mapOf("x" to 30)), zone)
        assertEquals(at(thu, 0), day.startMillis)
        assertEquals(at(fri, 0), day.endMillis)
        assertEquals(mapOf("x" to 30), day.limits)
    }
}
