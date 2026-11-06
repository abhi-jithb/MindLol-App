package com.abhijith.mindlol.domain

import com.abhijith.mindlol.domain.RulesTestTime.INSTA
import com.abhijith.mindlol.domain.RulesTestTime.YT
import com.abhijith.mindlol.domain.RulesTestTime.at
import com.abhijith.mindlol.domain.RulesTestTime.fri
import com.abhijith.mindlol.domain.RulesTestTime.minutes
import com.abhijith.mindlol.domain.RulesTestTime.thu
import com.abhijith.mindlol.domain.RulesTestTime.zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class WarningsTest {

    private val settings = Settings(lockedApps = setOf(INSTA), appLimits = mapOf(YT to 30))
    private val thuNight = NightCalendar.night(thu, settings, zone) // Thu 23:00 → Fri 08:00
    private val thuDay = NightCalendar.day(thu, settings, zone)
    private val friDay = NightCalendar.day(fri, settings, zone)
    private val never = setOf(LiveLockDecider.LOCK_PACKAGE, "com.android.settings")

    private fun upcoming(pkg: String, now: Long, used: Long = 0, grants: List<UnlockGrant> = emptyList(), night: RulesSnapshot? = thuNight, today: RulesSnapshot? = thuDay) =
        LiveLockDecider.upcomingLock(pkg, now, night, today, used, grants, never)

    // ---------- upcomingLock: when will the app in front lock? ----------

    @Test
    fun lockedApp_beforeBedtime_locksAtBedtime() {
        assertEquals(UpcomingLock(at(thu, 23), LockReason.BEDTIME, LockCause.BEDTIME), upcoming(INSTA, at(thu, 22, 40)))
    }

    @Test
    fun limitedApp_locksWhenItsUsageWouldPassTheLimit() {
        // 26 of 30 minutes used at 15:00: 4 more minutes in front, then over by 1 ms.
        val lock = upcoming(YT, at(thu, 15), used = minutes(26))
        assertEquals(UpcomingLock(at(thu, 15, 4) + 1, LockReason.LIMIT, LockCause.LIMIT), lock)
    }

    @Test
    fun alreadyLocked_neverLockable_orNoRules_meansNoUpcomingLock() {
        assertNull(upcoming(INSTA, at(thu, 23, 30)))                       // already inside the window
        assertNull(upcoming("com.android.settings", at(thu, 22, 50)))      // never lockable
        assertNull(upcoming(INSTA, at(thu, 22), night = null))             // no saved night
        assertNull(upcoming("com.whatsapp", at(thu, 22, 50)))              // neither locked nor limited
    }

    @Test
    fun grantEndingInsideTheNight_locksAtTheGrantEnd() {
        val grant = UnlockGrant(INSTA, LockReason.BEDTIME, at(fri, 1), 5)
        assertEquals(UpcomingLock(at(fri, 1, 5), LockReason.BEDTIME, LockCause.GRANT_END), upcoming(INSTA, at(fri, 1, 1), grants = listOf(grant), today = friDay))
    }

    @Test
    fun grantEndingAfterWake_neverLocks() {
        val grant = UnlockGrant(INSTA, LockReason.BEDTIME, at(fri, 7, 55), 10) // ends 08:05, after wake at 08:00
        assertNull(upcoming(INSTA, at(fri, 7, 56), grants = listOf(grant), today = friDay))
    }

    @Test
    fun grantOnALimitedAppStillOverItsLimit_locksAtTheGrantEnd() {
        val grant = UnlockGrant(YT, LockReason.LIMIT, at(thu, 15), 5)
        assertEquals(UpcomingLock(at(thu, 15, 5), LockReason.LIMIT, LockCause.GRANT_END), upcoming(YT, at(thu, 15, 1), used = minutes(40), grants = listOf(grant)))
    }

    // ---------- Pill: warnings at 5:00 and 1:00 ----------

    private val bedtimeLock = UpcomingLock(at(thu, 23), LockReason.BEDTIME, LockCause.BEDTIME)

    @Test
    fun noPill_untilFiveMinutesLeft_thenAWarningThatStaysUntilTapped() {
        val early = WarningPlanner.pill(at(thu, 22, 50), bedtimeLock, null)
        assertNull(early.mode)
        assertEquals(at(thu, 22, 55), early.nextChangeAtMillis) // the 5-min threshold

        val at5 = WarningPlanner.pill(at(thu, 22, 55), bedtimeLock, early.memory)
        assertEquals(PillMode.WARNING, at5.mode)
        val stillUp = WarningPlanner.pill(at(thu, 22, 57), bedtimeLock, at5.memory)
        assertEquals(PillMode.WARNING, stillUp.mode) // no tap yet
    }

    @Test
    fun dismissedAtFive_comesBackAtOne_andNeverRepeatsAfterThat() {
        var plan = WarningPlanner.pill(at(thu, 22, 55), bedtimeLock, null)
        val tapped = WarningPlanner.tap(at(thu, 22, 55, 5), requireNotNull(plan.memory), PillMode.WARNING)
        plan = WarningPlanner.pill(at(thu, 22, 56), bedtimeLock, tapped)
        assertNull(plan.mode)
        assertEquals(at(thu, 22, 59), plan.nextChangeAtMillis)

        plan = WarningPlanner.pill(at(thu, 22, 59), bedtimeLock, plan.memory)
        assertEquals(PillMode.WARNING, plan.mode)
        val tappedAgain = WarningPlanner.tap(at(thu, 22, 59, 2), requireNotNull(plan.memory), PillMode.WARNING)
        plan = WarningPlanner.pill(at(thu, 22, 59, 30), bedtimeLock, tappedAgain)
        assertNull(plan.mode)
        assertNull(plan.nextChangeAtMillis)
    }

    @Test
    fun openedWithThreeMinutesLeft_warnsAtOnce_withTheRealTime() {
        val now = at(thu, 22, 57)
        val plan = WarningPlanner.pill(now, bedtimeLock, null)
        assertEquals(PillMode.WARNING, plan.mode)
        assertEquals("3:00 left · Got it", WarningText.pill(PillMode.WARNING, bedtimeLock.atMillis - now))
    }

    @Test
    fun openedWithThirtySecondsLeft_warnsOnlyOnce() {
        var plan = WarningPlanner.pill(at(thu, 22, 59, 30), bedtimeLock, null)
        assertEquals(PillMode.WARNING, plan.mode)
        val tapped = WarningPlanner.tap(at(thu, 22, 59, 31), requireNotNull(plan.memory), PillMode.WARNING)
        plan = WarningPlanner.pill(at(thu, 22, 59, 40), bedtimeLock, tapped)
        assertNull(plan.mode) // both thresholds were used up by the one warning
    }

    @Test
    fun aDifferentLock_startsFresh() {
        val shown = WarningPlanner.pill(at(thu, 22, 55), bedtimeLock, null).memory
        val other = UpcomingLock(at(thu, 23, 30), LockReason.BEDTIME, LockCause.BEDTIME)
        val plan = WarningPlanner.pill(at(thu, 22, 55), other, shown)
        assertNull(plan.mode)
        assertEquals(other, plan.memory?.lock)
    }

    @Test
    fun atOrAfterTheLock_noPill() {
        assertNull(WarningPlanner.pill(at(thu, 23), bedtimeLock, null).mode)
        assertNull(WarningPlanner.pill(at(thu, 23, 1), bedtimeLock, null).mode)
    }

    // ---------- Pill during a grant: countdown, chip after 10 s ----------

    private val grantLock = UpcomingLock(at(fri, 1, 5), LockReason.BEDTIME, LockCause.GRANT_END) // a 5-min grant from 01:00

    @Test
    fun grant_countdownShrinksToAChipAfterTenSeconds() {
        val start = at(fri, 1)
        val first = WarningPlanner.pill(start, grantLock, null)
        assertEquals(PillMode.COUNTDOWN, first.mode)
        assertEquals(start + 10_000, first.nextChangeAtMillis)
        assertEquals(PillMode.COUNTDOWN, WarningPlanner.pill(start + 9_999, grantLock, first.memory).mode)
        assertEquals(PillMode.CHIP, WarningPlanner.pill(start + 10_000, grantLock, first.memory).mode)
    }

    @Test
    fun grant_tappingTheChipExpandsItForAnotherTenSeconds() {
        val start = at(fri, 1)
        val mem = requireNotNull(WarningPlanner.pill(start, grantLock, null).memory)
        val tapAt = start + 30_000
        val expanded = WarningPlanner.tap(tapAt, mem, PillMode.CHIP)
        assertEquals(PillMode.COUNTDOWN, WarningPlanner.pill(tapAt + 5_000, grantLock, expanded).mode)
        assertEquals(PillMode.CHIP, WarningPlanner.pill(tapAt + 10_000, grantLock, expanded).mode)
        // Tapping the full countdown shrinks it straight away.
        val shrunk = WarningPlanner.tap(tapAt + 1_000, expanded, PillMode.COUNTDOWN)
        assertEquals(PillMode.CHIP, WarningPlanner.pill(tapAt + 1_001, grantLock, shrunk).mode)
    }

    @Test
    fun grant_skipsTheFiveMinuteWarningYouJustChose_butWarnsAtOneMinute() {
        val start = at(fri, 1)
        val first = WarningPlanner.pill(start, grantLock, null)
        assertEquals(PillMode.COUNTDOWN, first.mode) // not a WARNING: you chose 5 minutes just now
        val chip = WarningPlanner.pill(at(fri, 1, 2), grantLock, first.memory)
        assertEquals(PillMode.CHIP, chip.mode)
        val oneLeft = WarningPlanner.pill(at(fri, 1, 4), grantLock, chip.memory)
        assertEquals(PillMode.WARNING, oneLeft.mode)
        // Dismissed: back to the small chip, not the full countdown.
        val tapped = WarningPlanner.tap(at(fri, 1, 4, 3), requireNotNull(oneLeft.memory), PillMode.WARNING)
        assertEquals(PillMode.CHIP, WarningPlanner.pill(at(fri, 1, 4, 4), grantLock, tapped).mode)
    }

    @Test
    fun tenMinuteGrant_warnsAtFiveAndAtOne() {
        val lock = UpcomingLock(at(fri, 1, 10), LockReason.BEDTIME, LockCause.GRANT_END)
        val first = WarningPlanner.pill(at(fri, 1), lock, null)
        assertEquals(PillMode.COUNTDOWN, first.mode)
        assertEquals(PillMode.WARNING, WarningPlanner.pill(at(fri, 1, 5), lock, first.memory).mode)
    }

    // ---------- Bedtime notification ----------

    @Test
    fun bedtimeNotice_onlyInTheFifteenMinutesBefore_oncePerNight_onlyWithLockedApps() {
        assertFalse(WarningPlanner.bedtimeNoticeDue(at(thu, 22, 44, 59), thuNight, null))
        assertTrue(WarningPlanner.bedtimeNoticeDue(at(thu, 22, 45), thuNight, null))
        assertTrue(WarningPlanner.bedtimeNoticeDue(at(thu, 22, 59, 59), thuNight, null))
        assertFalse(WarningPlanner.bedtimeNoticeDue(at(thu, 23), thuNight, null))
        assertFalse(WarningPlanner.bedtimeNoticeDue(at(thu, 22, 50), thuNight, alreadyShownForNight = thu))
        val nothingLocked = NightCalendar.night(thu, Settings(), zone)
        assertFalse(WarningPlanner.bedtimeNoticeDue(at(thu, 22, 50), nothingLocked, null))
        assertFalse(WarningPlanner.bedtimeNoticeDue(at(thu, 22, 50), null, null))
    }

    @Test
    fun bedtimeNoticeAt_isFifteenMinutesBeforeBedtime() {
        assertEquals(at(thu, 22, 45), WarningPlanner.bedtimeNoticeAt(at(thu, 20), thuNight))
        assertNull(WarningPlanner.bedtimeNoticeAt(at(thu, 22, 50), thuNight)) // window already open
        assertNull(WarningPlanner.bedtimeNoticeAt(at(thu, 20), NightCalendar.night(thu, Settings(), zone)))
    }

    // ---------- Words: real numbers only ----------

    @Test
    fun countdown_roundsDown_andIsNeverNegative() {
        assertEquals("4:59", WarningText.countdown(299_999))
        assertEquals("5:00", WarningText.countdown(300_000))
        assertEquals("0:59", WarningText.countdown(59_500))
        assertEquals("0:00", WarningText.countdown(400))
        assertEquals("0:00", WarningText.countdown(-5_000))
        assertEquals("10:00", WarningText.countdown(600_000))
    }

    @Test
    fun pillTexts() {
        assertEquals("4:59 left · Got it", WarningText.pill(PillMode.WARNING, 299_999))
        assertEquals("4:59 left", WarningText.pill(PillMode.COUNTDOWN, 299_999))
        assertEquals("4:59", WarningText.pill(PillMode.CHIP, 299_999))
    }

    @Test
    fun minutes_roundToNearest_andSayLessThanAMinuteUnderThirtySeconds() {
        assertEquals("5 min", WarningText.minutes(299_000))
        assertEquals("3 min", WarningText.minutes(180_000))
        assertEquals("1 min", WarningText.minutes(30_000))
        assertEquals("less than a minute", WarningText.minutes(29_999))
        assertEquals("5 min left on Instagram", WarningText.limitNotification("Instagram", 300_000))
        assertEquals("3 min left on Instagram", WarningText.limitNotification("Instagram", 180_000))
    }

    @Test
    fun bedtimeNotificationText_namesTheAppsAndTheRealTime() {
        val bed = LocalTime.of(23, 0)
        assertEquals("Bedtime in 15 min. Instagram locks at 23:00.", WarningText.bedtimeNotification(minutes(15), bed, listOf("Instagram")))
        assertEquals("Bedtime in 12 min. Instagram and 2 more lock at 23:00.", WarningText.bedtimeNotification(minutes(12), bed, listOf("Instagram", "YouTube", "Reddit")))
        assertEquals("Bedtime in 15 min. Instagram locks at 23:30.", WarningText.bedtimeNotification(minutes(15), LocalTime.of(23, 30), listOf("Instagram")))
    }
}
