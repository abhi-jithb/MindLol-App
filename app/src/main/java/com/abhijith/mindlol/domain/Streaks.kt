package com.abhijith.mindlol.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

/** Profile numbers, all computed from saved snapshots and cheats. */
data class CheatStats(
    val cleanNightsCurrent: Int = 0,
    val cleanNightsBest: Int = 0,
    val underLimitCurrent: Int = 0,
    val underLimitBest: Int = 0,
    val cheatsThisWeek: Int = 0,
    val cheatsThisMonth: Int = 0,
    val cheatsTotal: Int = 0
)

/**
 * Streaks: a night is clean when it had locked apps and no bedtime cheat; a day is under its
 * limits when it had limits and no limit cheat. Periods with no rules are skipped (they neither
 * count nor break a streak). Only finalized periods count, except that a running period that
 * already has a cheat breaks the current streak straight away.
 */
object Streaks {

    fun compute(periods: List<RulesSnapshot>, cheats: List<Cheat>, today: LocalDate): CheatStats {
        val nights = streak(periods, cheats, PeriodKind.NIGHT, CheatType.BEDTIME) { it.lockedApps.isNotEmpty() }
        val days = streak(periods, cheats, PeriodKind.DAY, CheatType.LIMIT) { it.limits.isNotEmpty() }
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val month = YearMonth.from(today)
        return CheatStats(
            cleanNightsCurrent = nights.first,
            cleanNightsBest = nights.second,
            underLimitCurrent = days.first,
            underLimitBest = days.second,
            cheatsThisWeek = cheats.count { !it.periodDate.isBefore(monday) && !it.periodDate.isAfter(today) },
            cheatsThisMonth = cheats.count { YearMonth.from(it.periodDate) == month },
            cheatsTotal = cheats.size
        )
    }

    /** (current run, best run) of clean periods of one kind. */
    private fun streak(
        periods: List<RulesSnapshot>,
        cheats: List<Cheat>,
        kind: PeriodKind,
        type: CheatType,
        hasRules: (RulesSnapshot) -> Boolean
    ): Pair<Int, Int> {
        val cheatDates = cheats.filter { it.type == type }.map { it.periodDate }.toSet()
        val eligible = periods.filter { it.kind == kind && hasRules(it) }.sortedBy { it.date }

        var run = 0
        var best = 0
        for (p in eligible) {
            val cheated = p.date in cheatDates
            when {
                cheated -> run = 0                // a cheat breaks it, even before the period ends
                p.finalized -> run++              // clean and over: counts
                else -> Unit                      // clean so far but still running: not counted yet
            }
            best = maxOf(best, run)
        }
        return run to best
    }
}
