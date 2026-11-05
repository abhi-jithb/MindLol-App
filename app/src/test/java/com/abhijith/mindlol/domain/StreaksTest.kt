package com.abhijith.mindlol.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class StreaksTest {

    private val today = LocalDate.of(2026, 11, 5) // Thursday
    private val insta = "com.instagram.android"

    private fun night(daysAgo: Long, finalized: Boolean = true, locked: Set<String> = setOf(insta)) =
        RulesSnapshot(PeriodKind.NIGHT, today.minusDays(daysAgo), 0, 1, lockedApps = locked, finalized = finalized)

    private fun day(daysAgo: Long, finalized: Boolean = true) =
        RulesSnapshot(PeriodKind.DAY, today.minusDays(daysAgo), 0, 1, limits = mapOf(insta to 30), finalized = finalized)

    private fun cheat(daysAgo: Long, type: CheatType = CheatType.BEDTIME) =
        Cheat(insta, type, today.minusDays(daysAgo), 0, 1, 1, firstSeenStartMillis = daysAgo + 1)

    @Test
    fun noData_allZero() {
        assertEquals(CheatStats(), Streaks.compute(emptyList(), emptyList(), today))
    }

    @Test
    fun cleanNightsInARow_countBackFromLatest_andBestRemembersLongerRun() {
        val nights = (6L downTo 1L).map { night(it) }
        // Cheat 4 nights ago: run before it was 2 (6, 5), after it 3 (3, 2, 1).
        val stats = Streaks.compute(nights, listOf(cheat(4)), today)
        assertEquals(3, stats.cleanNightsCurrent)
        assertEquals(3, stats.cleanNightsBest)

        // Cheat 2 nights ago: best run is 6, 5, 4, 3 (4 nights); current is just last night.
        val stats2 = Streaks.compute(nights, listOf(cheat(2)), today)
        assertEquals(1, stats2.cleanNightsCurrent)
        assertEquals(4, stats2.cleanNightsBest)
    }

    @Test
    fun runningNightWithCheat_breaksStreakNow_runningCleanNightNotCountedYet() {
        val nights = listOf(night(2), night(1), night(0, finalized = false))
        assertEquals(2, Streaks.compute(nights, emptyList(), today).cleanNightsCurrent)
        assertEquals(0, Streaks.compute(nights, listOf(cheat(0)), today).cleanNightsCurrent)
    }

    @Test
    fun nightsWithNothingLocked_areSkipped() {
        val nights = listOf(night(3), night(2, locked = emptySet()), night(1))
        assertEquals(2, Streaks.compute(nights, emptyList(), today).cleanNightsCurrent)
    }

    @Test
    fun limitStreak_usesOnlyLimitCheats() {
        val days = listOf(day(3), day(2), day(1))
        val stats = Streaks.compute(days, listOf(cheat(2, CheatType.LIMIT), cheat(1, CheatType.BEDTIME)), today)
        assertEquals(1, stats.underLimitCurrent)
        assertEquals(1, stats.underLimitBest)
    }

    @Test
    fun weekAndMonthCounts() {
        // Monday 2 Nov is this week; Sunday 1 Nov is last week but this month; 31 Oct is last month.
        val cheats = listOf(cheat(3), cheat(0), cheat(4), cheat(5))
        val stats = Streaks.compute(emptyList(), cheats, today)
        assertEquals(2, stats.cheatsThisWeek)
        assertEquals(3, stats.cheatsThisMonth)
        assertEquals(4, stats.cheatsTotal)
    }
}
