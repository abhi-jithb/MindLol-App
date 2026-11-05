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
    fun unlockCount_resetsWhenANewNightStarts() {
        val wedNight = NightCalendar.night(wed, settings, zone)
        val grants = listOf(
            UnlockGrant(INSTA, LockReason.BEDTIME, at(thu, 1), 5),   // Wednesday night
            UnlockGrant(INSTA, LockReason.BEDTIME, at(thu, 2), 5),   // Wednesday night
            UnlockGrant(INSTA, LockReason.BEDTIME, at(fri, 0, 30), 5) // Thursday night
        )
        assertEquals(2, LiveLockDecider.unlocksSinceReset(grants, at(thu, 3), listOf(wedNight, thuNight)))
        assertEquals(1, LiveLockDecider.unlocksSinceReset(grants, at(fri, 1), listOf(wedNight, thuNight)))
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
}
