package com.abhijith.mindlol.domain

import com.abhijith.mindlol.domain.RulesTestTime.INSTA
import com.abhijith.mindlol.domain.RulesTestTime.YT
import com.abhijith.mindlol.domain.RulesTestTime.at
import com.abhijith.mindlol.domain.RulesTestTime.fri
import com.abhijith.mindlol.domain.RulesTestTime.minutes
import com.abhijith.mindlol.domain.RulesTestTime.thu
import com.abhijith.mindlol.domain.RulesTestTime.wed
import com.abhijith.mindlol.domain.RulesTestTime.zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLockDeciderTest {

    private val settings = Settings(lockedApps = setOf(INSTA), appLimits = mapOf(YT to 30))
    private val thuNight = NightCalendar.night(thu, settings, zone)          // Thu 23:00 → Fri 08:00
    private val thuDay = NightCalendar.day(thu, settings, zone)
    private val friDay = NightCalendar.day(fri, settings, zone)
    private val never = setOf(LiveLockDecider.LOCK_PACKAGE, "com.android.settings", "com.android.dialer")

    private fun decide(
        pkg: String,
        now: Long,
        night: RulesSnapshot? = thuNight,
        today: RulesSnapshot? = thuDay,
        used: Long = 0,
        grants: List<UnlockGrant> = emptyList()
    ) = LiveLockDecider.decide(pkg, now, night, today, used, grants, never)

    // ---------- Bedtime window ----------

    @Test
    fun lockedAppInsideWindow_isLockedForBedtime() {
        assertEquals(LockDecision.Lock(LockReason.BEDTIME, thu), decide(INSTA, at(fri, 1), today = friDay))
    }

    @Test
    fun lockedAppBeforeWindow_isAllowed_untilBedtime() {
        assertEquals(LockDecision.Allow(at(thu, 23)), decide(INSTA, at(thu, 22)))
    }

    @Test
    fun lockedAppAfterWake_isAllowed() {
        assertEquals(LockDecision.Allow(null), decide(INSTA, at(fri, 8), today = friDay))
    }

    @Test
    fun unlockedAppInsideWindow_isAllowed() {
        assertEquals(LockDecision.Allow(null), decide("com.whatsapp", at(fri, 1), today = friDay))
    }

    @Test
    fun noSavedNight_neverLocks() {
        assertEquals(LockDecision.Allow(null), decide(INSTA, at(fri, 1), night = null, today = null))
    }

    @Test
    fun overrideNight_usesItsFrozenWindow() {
        val override = TonightOverride(2 * 60, 10 * 60, expiresAtMillis = at(fri, 10))
        val night = NightCalendar.night(thu, settings.copy(tonightOverride = override), zone) // Fri 02:00 → 10:00
        assertEquals(LockDecision.Allow(at(fri, 2)), decide(INSTA, at(fri, 1), night = night, today = friDay))
        assertEquals(LockDecision.Lock(LockReason.BEDTIME, thu), decide(INSTA, at(fri, 9), night = night, today = friDay))
    }

    // ---------- Daily limit ----------

    @Test
    fun appOverLimit_isLockedForLimit() {
        assertEquals(LockDecision.Lock(LockReason.LIMIT, thu), decide(YT, at(thu, 15), used = minutes(31)))
    }

    @Test
    fun appExactlyAtLimit_isAllowed_untilOneMsMore() {
        assertEquals(LockDecision.Allow(at(thu, 15) + 1), decide(YT, at(thu, 15), used = minutes(30)))
    }

    @Test
    fun appUnderLimit_recheckWhenLimitWouldBeCrossed() {
        assertEquals(LockDecision.Allow(at(thu, 15, 10) + 1), decide(YT, at(thu, 15), used = minutes(20)))
    }

    @Test
    fun lockedAndOverLimitInsideWindow_bedtimeWins() {
        val both = NightCalendar.night(thu, settings.copy(lockedApps = setOf(INSTA, YT)), zone)
        val day = NightCalendar.day(thu, settings, zone)
        assertEquals(LockDecision.Lock(LockReason.BEDTIME, thu), decide(YT, at(thu, 23, 30), night = both, today = day, used = minutes(90)))
    }

    // ---------- Grants and never-lockable apps ----------

    @Test
    fun runningGrant_allows_untilItEnds() {
        val grant = UnlockGrant(INSTA, LockReason.BEDTIME, at(fri, 1), minutes = 5)
        assertEquals(LockDecision.Allow(at(fri, 1, 5)), decide(INSTA, at(fri, 1, 2), today = friDay, grants = listOf(grant)))
    }

    @Test
    fun expiredGrant_locksAgain() {
        val grant = UnlockGrant(INSTA, LockReason.BEDTIME, at(fri, 1), minutes = 5)
        assertEquals(LockDecision.Lock(LockReason.BEDTIME, thu), decide(INSTA, at(fri, 1, 5), today = friDay, grants = listOf(grant)))
    }

    @Test
    fun grantForAnotherApp_doesNotHelp() {
        val grant = UnlockGrant(YT, LockReason.BEDTIME, at(fri, 1), minutes = 10)
        assertEquals(LockDecision.Lock(LockReason.BEDTIME, thu), decide(INSTA, at(fri, 1, 2), today = friDay, grants = listOf(grant)))
    }

    @Test
    fun neverLockableApps_areAlwaysAllowed_evenIfLocked() {
        val night = NightCalendar.night(thu, settings.copy(lockedApps = never), zone)
        never.forEach { pkg -> assertEquals(LockDecision.Allow(null), decide(pkg, at(fri, 1), night = night, today = friDay)) }
    }

    // ---------- Escalation ----------

    @Test
    fun countdown_doublesPerUnlock_cappedAt320() {
        assertEquals(listOf(10, 20, 40, 80, 160, 320, 320, 320), (0..7).map(LiveLockDecider::countdownSeconds))
        assertEquals(320, LiveLockDecider.countdownSeconds(1_000))
    }

    @Test
    fun unlockCount_resetsAtBedtime() {
        val wedNight = NightCalendar.night(wed, settings, zone)
        val grants = listOf(
            UnlockGrant(INSTA, LockReason.LIMIT, at(thu, 15), 5),     // Thursday daytime
            UnlockGrant(INSTA, LockReason.BEDTIME, at(fri, 0, 30), 5) // Thursday night
        )
        assertEquals(1, LiveLockDecider.unlocksSinceReset(grants, at(fri, 1), listOf(wedNight, thuNight)))
    }

    @Test
    fun unlockCount_resetsAtWakeTime_soDayAndNightEscalateSeparately() {
        val grants = listOf(
            UnlockGrant(INSTA, LockReason.BEDTIME, at(fri, 1), 5),   // Thursday night
            UnlockGrant(INSTA, LockReason.BEDTIME, at(fri, 2), 5),   // Thursday night
            UnlockGrant(YT, LockReason.LIMIT, at(fri, 15), 5)        // Friday daytime, after waking at 08:00
        )
        assertEquals(2, LiveLockDecider.unlocksSinceReset(grants, at(fri, 7), listOf(thuNight)))
        assertEquals(1, LiveLockDecider.unlocksSinceReset(grants, at(fri, 16), listOf(thuNight)))
    }

    @Test
    fun unlockCount_beforeAnyBedtimeOrWake_countsEverything() {
        val grants = listOf(UnlockGrant(INSTA, LockReason.LIMIT, at(thu, 10), 5))
        assertEquals(1, LiveLockDecider.unlocksSinceReset(grants, at(thu, 12), emptyList()))
    }

    // ---------- Heartbeat: only while it matters ----------

    @Test
    fun heartbeatMatters_insideNightWithLocks_orOnDayWithLimits_only() {
        val noLimitDay = NightCalendar.day(fri, Settings(), zone)
        assertTrue(LiveLockDecider.heartbeatMatters(at(fri, 1), listOf(thuNight)))
        assertTrue(LiveLockDecider.heartbeatMatters(at(thu, 15), listOf(thuDay)))                       // YouTube has a limit today
        assertEquals(false, LiveLockDecider.heartbeatMatters(at(fri, 15), listOf(thuNight, noLimitDay))) // day without limits
        assertEquals(false, LiveLockDecider.heartbeatMatters(at(fri, 1), listOf(NightCalendar.night(thu, Settings(), zone)))) // nothing locked
    }

    // ---------- Off intervals: screen-on parts of gaps only ----------

    private val boot = at(wed, 9)
    private fun lockSession(connected: Long, lastAlive: Long, disconnected: Long? = null, bootAt: Long = boot) =
        LockSession(connected, lastAlive, disconnected, bootAt)

    private fun cheatsFor(sessions: List<LockSession>, screenOn: List<ScreenOnInterval>?, now: Long, running: Boolean) =
        LiveLockDecider.lockDisabledCheats(thuNight, LiveLockDecider.lockOffIntervals(sessions, screenOn, now, running), now)

    @Test
    fun staleHeartbeatOutsideWhatMatters_isNotAGap_whileStillRunning() {
        // Alive since Thursday morning; no limits all day, so the last heartbeat is 08:00. Still running at 23:30.
        val sessions = listOf(lockSession(at(thu, 7), at(thu, 8)))
        val screenOn = listOf(ScreenOnInterval(at(thu, 23), at(thu, 23, 30)))
        assertTrue(LiveLockDecider.lockOffIntervals(sessions, screenOn, at(thu, 23, 30), runningNow = true).isEmpty())
    }

    @Test
    fun phoneOffAllNight_noCheat() {
        // Last heartbeat 23:20, the phone was switched off, booted at 07:30, service back at 07:31.
        val sessions = listOf(
            lockSession(at(thu, 7), at(thu, 23, 20)),
            lockSession(at(fri, 7, 31), at(fri, 7, 31), bootAt = at(fri, 7, 30))
        )
        val screenOn = listOf(ScreenOnInterval(at(thu, 23), at(thu, 23, 15)), ScreenOnInterval(at(fri, 7, 30), at(fri, 9)))
        assertTrue(cheatsFor(sessions, screenOn, at(fri, 12), running = true).isEmpty())
    }

    @Test
    fun serviceKilledWhileScreenOff_noCheat() {
        // Killed in the night while you slept (screen off 23:30 → 08:15, after the 08:00 wake time);
        // back when the app opened at 08:30.
        val sessions = listOf(
            lockSession(at(thu, 7), at(fri, 2)),
            lockSession(at(fri, 8, 30), at(fri, 8, 30))
        )
        val screenOn = listOf(ScreenOnInterval(at(thu, 23), at(thu, 23, 30)), ScreenOnInterval(at(fri, 8, 15), at(fri, 9)))
        assertTrue(cheatsFor(sessions, screenOn, at(fri, 12), running = true).isEmpty())
    }

    @Test
    fun serviceOffWhileScreenOn_isCheat() {
        // Turned the lock off at 00:30 (clean disconnect), used the phone until 01:30, never turned it back on.
        val sessions = listOf(lockSession(at(thu, 7), at(fri, 0, 30), disconnected = at(fri, 0, 30)))
        val screenOn = listOf(ScreenOnInterval(at(fri, 0), at(fri, 1, 30)))
        val cheat = cheatsFor(sessions, screenOn, at(fri, 12), running = false).single()
        assertEquals(at(fri, 0, 30), cheat.startMillis)
        assertEquals(60, cheat.minutes)
    }

    @Test
    fun gapPartlyScreenOn_countsOnlyScreenOnMinutes() {
        // Force-stopped: last heartbeat 01:00, so off from 01:10 until the service returned at 05:00.
        // Screen on 01:20–01:50 and 03:00–03:15 inside the gap: 45 minutes, not 3h50.
        val sessions = listOf(
            lockSession(at(thu, 7), at(fri, 1)),
            lockSession(at(fri, 5), at(fri, 5))
        )
        val screenOn = listOf(
            ScreenOnInterval(at(fri, 0, 50), at(fri, 1, 5)),  // before the gap: lock still alive
            ScreenOnInterval(at(fri, 1, 20), at(fri, 1, 50)),
            ScreenOnInterval(at(fri, 3), at(fri, 3, 15))
        )
        val cheats = cheatsFor(sessions, screenOn, at(fri, 12), running = true)
        assertEquals(2, cheats.size) // 1:50 → 3:00 is more than the 5-minute merge gap
        assertEquals(45, cheats.sumOf { it.minutes })
    }

    @Test
    fun rebootAfterTurningLockOff_countsOnlyUntilBoot() {
        // Lock turned off cleanly at 01:00, screen on until 02:00, phone restarted at 03:00, service back 03:01.
        val sessions = listOf(
            lockSession(at(thu, 7), at(fri, 1), disconnected = at(fri, 1)),
            lockSession(at(fri, 3, 1), at(fri, 3, 1), bootAt = at(fri, 3))
        )
        val screenOn = listOf(ScreenOnInterval(at(fri, 0, 40), at(fri, 2)), ScreenOnInterval(at(fri, 3), at(fri, 3, 20)))
        val cheat = cheatsFor(sessions, screenOn, at(fri, 12), running = true).single()
        assertEquals(60, cheat.minutes) // 01:00–02:00; 03:00–03:01 is boot, not a cheat
    }

    // ---------- LOCK_DISABLED ----------

    @Test
    fun lockTurnedOffDuringNight_isLockDisabledCheat() {
        val cheat = LiveLockDecider.lockDisabledCheats(thuNight, listOf(LockOffInterval(at(fri, 1), at(fri, 1, 40))), at(fri, 12)).single()
        assertEquals(CheatType.LOCK_DISABLED, cheat.type)
        assertEquals(LiveLockDecider.LOCK_PACKAGE, cheat.packageName)
        assertEquals(thu, cheat.periodDate)
        assertEquals(at(fri, 1), cheat.startMillis)
        assertEquals(40, cheat.minutes)
    }

    @Test
    fun lockOffBeforeBedtimeAndStillOff_countsFromBedtime_untilNow() {
        val cheat = LiveLockDecider.lockDisabledCheats(thuNight, listOf(LockOffInterval(at(thu, 21), at(fri, 9))), at(fri, 0, 30)).single()
        assertEquals(at(thu, 23), cheat.startMillis)
        assertEquals(at(fri, 0, 30), cheat.endMillis)
        assertEquals(90, cheat.minutes)
    }

    @Test
    fun lockOffOnlyOutsideWindow_isNotACheat() {
        val off = listOf(LockOffInterval(at(thu, 14), at(thu, 22)), LockOffInterval(at(fri, 9), at(fri, 11)))
        assertTrue(LiveLockDecider.lockDisabledCheats(thuNight, off, at(fri, 12)).isEmpty())
    }

    @Test
    fun lockOffWithNothingLocked_isNotACheat() {
        val empty = NightCalendar.night(thu, Settings(), zone)
        assertTrue(LiveLockDecider.lockDisabledCheats(empty, listOf(LockOffInterval(at(fri, 1), at(fri, 2))), at(fri, 12)).isEmpty())
    }

    @Test
    fun lockOffTwiceCloseTogether_isOneCheat_farApart_isTwo() {
        val close = listOf(LockOffInterval(at(fri, 1), at(fri, 1, 10)), LockOffInterval(at(fri, 1, 12), at(fri, 1, 20)))
        assertEquals(1, LiveLockDecider.lockDisabledCheats(thuNight, close, at(fri, 12)).size)
        val far = listOf(LockOffInterval(at(fri, 1), at(fri, 1, 10)), LockOffInterval(at(fri, 3), at(fri, 3, 5)))
        assertEquals(2, LiveLockDecider.lockDisabledCheats(thuNight, far, at(fri, 12)).size)
    }

    @Test
    fun lockDisabledCheat_extendsOnLaterRefresh_noDuplicate() {
        val first = LiveLockDecider.lockDisabledCheats(thuNight, listOf(LockOffInterval(at(fri, 1), at(fri, 2))), at(fri, 2))
        val later = LiveLockDecider.lockDisabledCheats(thuNight, listOf(LockOffInterval(at(fri, 1), at(fri, 3))), at(fri, 3))
        val merged = RulesPlanner.mergeWithSaved(later, first).single()
        assertEquals(first.single().key, merged.key)
        assertEquals(120, merged.minutes)
    }

    @Test
    fun noScreenData_noLockDisabledCheat() {
        // The same "lock off at 00:30 and never back" gap as serviceOffWhileScreenOn_isCheat,
        // but without screen data (Android 8, or events missing): no evidence, no cheat.
        val sessions = listOf(lockSession(at(thu, 7), at(fri, 0, 30), disconnected = at(fri, 0, 30)))
        assertTrue(cheatsFor(sessions, screenOn = null, now = at(fri, 12), running = false).isEmpty())
    }
}
