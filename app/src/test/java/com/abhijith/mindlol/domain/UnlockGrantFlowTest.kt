package com.abhijith.mindlol.domain

import com.abhijith.mindlol.data.db.LockEventEntity
import com.abhijith.mindlol.data.db.toGrant
import com.abhijith.mindlol.domain.RulesTestTime.INSTA
import com.abhijith.mindlol.domain.RulesTestTime.at
import com.abhijith.mindlol.domain.RulesTestTime.fri
import com.abhijith.mindlol.domain.RulesTestTime.thu
import com.abhijith.mindlol.domain.RulesTestTime.zone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Item 8: a finished lock screen (lock_event UNLOCKED) becomes a grant, the app runs until the
 * grant ends, the live lock rechecks at that moment, and the next countdown escalates.
 */
class UnlockGrantFlowTest {

    private val settings = Settings(lockedApps = setOf(INSTA))
    private val thuNight = NightCalendar.night(thu, settings, zone) // Thu 23:00 → Fri 08:00
    private val friDay = NightCalendar.day(fri, settings, zone)
    private val never = setOf(LiveLockDecider.LOCK_PACKAGE)

    private fun event(outcome: LockOutcome, minutes: Int?, grantedAt: Long?) = LockEventEntity(
        id = 1, packageName = INSTA, reason = LockReason.BEDTIME, periodDate = thu, shownAtMillis = at(fri, 0, 59),
        stepReached = 5, outcome = outcome, countdownSeconds = 10, grantedMinutes = minutes, grantedAtMillis = grantedAt,
        cardId = "brain", cardAnswer = "Yes"
    )

    private fun decide(now: Long, grants: List<UnlockGrant>) =
        LiveLockDecider.decide(INSTA, now, thuNight, friDay, 0, grants, never)

    @Test
    fun unlockedRow_becomesGrant_andAllowsTheAppUntilItEnds_thenRechecksExactlyThen() {
        val grant = event(LockOutcome.UNLOCKED, 5, at(fri, 1)).toGrant()
        assertEquals(UnlockGrant(INSTA, LockReason.BEDTIME, at(fri, 1), 5), grant)
        val grants = listOfNotNull(grant)
        assertEquals(LockDecision.Allow(at(fri, 1, 5)), decide(at(fri, 1), grants))
        assertEquals(LockDecision.Allow(at(fri, 1, 5)), decide(at(fri, 1, 4), grants))
    }

    @Test
    fun whenTheGrantRunsOut_theAppIsLockedAgain() {
        val grants = listOfNotNull(event(LockOutcome.UNLOCKED, 2, at(fri, 1)).toGrant())
        assertEquals(LockDecision.Lock(LockReason.BEDTIME, thu), decide(at(fri, 1, 2), grants))
    }

    @Test
    fun rowsThatDidNotFinish_areNeverGrants() {
        assertNull(event(LockOutcome.LEFT, null, null).toGrant())
        assertNull(event(LockOutcome.WENT_BACK, null, null).toGrant())
        // Even with minutes somehow filled in, only UNLOCKED counts.
        assertNull(event(LockOutcome.LEFT, 10, at(fri, 1)).toGrant())
    }

    @Test
    fun eachGrantTonight_doublesTheNextCountdown() {
        val grants = listOf(
            UnlockGrant(INSTA, LockReason.BEDTIME, at(fri, 0, 10), 2),
            UnlockGrant(INSTA, LockReason.BEDTIME, at(fri, 1), 5)
        )
        val stored = listOf(thuNight)
        assertEquals(10, LiveLockDecider.countdownSeconds(LiveLockDecider.unlocksSinceReset(emptyList(), at(fri, 0), stored)))
        assertEquals(20, LiveLockDecider.countdownSeconds(LiveLockDecider.unlocksSinceReset(grants.take(1), at(fri, 0, 30), stored)))
        assertEquals(40, LiveLockDecider.countdownSeconds(LiveLockDecider.unlocksSinceReset(grants, at(fri, 2), stored)))
        // Reset B: after waking at 08:00 the night's unlocks no longer count.
        assertEquals(10, LiveLockDecider.countdownSeconds(LiveLockDecider.unlocksSinceReset(grants, at(fri, 9), stored)))
    }

    @Test
    fun recheckDelay_isTheTimeLeft_neverNegative_neverATightLoop() {
        assertEquals(300_000L, LiveLockDecider.recheckDelayMillis(at(fri, 1, 5), at(fri, 1)))
        assertEquals(LiveLockDecider.MIN_RECHECK_DELAY_MS, LiveLockDecider.recheckDelayMillis(at(fri, 1), at(fri, 1)))
        assertEquals(LiveLockDecider.MIN_RECHECK_DELAY_MS, LiveLockDecider.recheckDelayMillis(at(fri, 1), at(fri, 2)))
    }
}
