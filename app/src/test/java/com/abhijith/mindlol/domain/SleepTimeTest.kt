package com.abhijith.mindlol.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class SleepTimeTest {

    private val ist = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 11, 5)

    private fun at(date: LocalDate, h: Int, m: Int): ZonedDateTime =
        LocalDateTime.of(date, LocalTime.of(h, m)).atZone(ist)

    @Test
    fun expiry_setInEvening_endsTomorrowAtWake() {
        assertEquals(at(day.plusDays(1), 10, 0), SleepTime.overrideExpiry(at(day, 21, 0), 10 * 60))
    }

    @Test
    fun expiry_setAfterMidnight_endsThisMorning() {
        assertEquals(at(day, 10, 0), SleepTime.overrideExpiry(at(day, 1, 30), 10 * 60))
    }

    @Test
    fun expiry_setExactlyAtWake_endsTomorrow() {
        assertEquals(at(day.plusDays(1), 8, 0), SleepTime.overrideExpiry(at(day, 8, 0), 8 * 60))
    }

    @Test
    fun windowLength_crossesMidnight() {
        assertEquals(9 * 60, SleepTime.windowLength(23 * 60, 8 * 60))
        assertEquals(8 * 60, SleepTime.windowLength(2 * 60, 10 * 60))
        assertEquals(0, SleepTime.windowLength(8 * 60, 8 * 60))
    }

    @Test
    fun minutes_toAndFromLocalTime() {
        assertEquals(LocalTime.of(23, 0), SleepTime.toLocalTime(1380))
        assertEquals(1380, SleepTime.fromLocalTime(LocalTime.of(23, 0)))
    }

    @Test
    fun activeOverride_goneOnceExpired() {
        val override = TonightOverride(2 * 60, 10 * 60, expiresAtMillis = 1_000L)
        val settings = Settings(tonightOverride = override)
        assertEquals(override, settings.activeOverride(nowMillis = 999L))
        assertNull(settings.activeOverride(nowMillis = 1_000L))
    }

    @Test
    fun defaults_matchProductRules() {
        val s = Settings()
        assertEquals(23 * 60, s.bedtimeMinutes)
        assertEquals(8 * 60, s.wakeMinutes)
        assertEquals(RoastIntensity.SAVAGE, s.roastIntensity)
        assertEquals(emptySet<String>(), s.lockedApps)
        assertEquals(AppTheme.PURE_BLACK, s.theme)
    }
}
