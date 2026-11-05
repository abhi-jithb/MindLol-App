package com.abhijith.mindlol.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Decides which rules judge which period, and what to save. Two product rules drive it:
 *
 * 1. "Once a period starts, its rules are locked." A night's snapshot is frozen at bedtime, a
 *    day's at midnight. Settings changed later apply from the next night/day.
 * 2. "Once a period has ended, its result is final." Finalized periods are never judged again,
 *    and saved cheats are only ever extended, never deleted.
 *
 * Snapshots are written ahead of time (on every settings change and refresh), so a night is
 * frozen with the right rules even if no refresh runs during it.
 *
 * 3. "No rules, no cheats." A period that started before the first snapshot was ever saved
 *    ([plan]'s rulesSinceMillis) is never judged: no rules existed then.
 */
object RulesPlanner {

    /**
     * Snapshot rows for every night and day that hasn't started yet, built from the current
     * [settings]. Already-started rows are frozen and left out.
     *
     * If a change moves a not-yet-started period's start into the past (bedtime moved to 22:00
     * at 22:30), the window starts now: rules never apply to time before they were set.
     */
    fun upcomingSnapshots(
        nowMillis: Long,
        zone: ZoneId,
        settings: Settings,
        stored: List<RulesSnapshot>
    ): List<RulesSnapshot> {
        val byKey = stored.associateBy { it.kind to it.date }
        val today = today(nowMillis, zone)
        return PeriodKind.entries.flatMap { kind ->
            (-1L..1L).mapNotNull { offset ->
                val date = today.plusDays(offset)
                val saved = byKey[kind to date]
                val fresh = NightCalendar.build(kind, date, settings, zone)
                when {
                    saved != null && saved.hasStarted(nowMillis) -> null // frozen
                    saved == null && fresh.hasStarted(nowMillis) -> null // started with no row: plan() falls back
                    else -> {
                        val start = maxOf(fresh.startMillis, nowMillis)
                        fresh.copy(startMillis = start, endMillis = maxOf(fresh.endMillis, start))
                    }
                }
            }
        }
    }

    /**
     * Called on every refresh. Judges every night and day from [firstDate] to today that isn't
     * finalized yet, and returns what to save.
     *
     * @param stored saved snapshot rows (at least those from [firstDate] on).
     * @param storedCheats saved cheats of the periods being judged, so new findings extend them.
     * @param sessions app sessions covering the periods, with a session still open ending at [nowMillis].
     * @param rulesSinceMillis when snapshots were first ever saved. Periods that started before it are skipped.
     */
    fun plan(
        nowMillis: Long,
        zone: ZoneId,
        settings: Settings,
        firstDate: LocalDate,
        stored: List<RulesSnapshot>,
        storedCheats: List<Cheat>,
        sessions: List<AppSession>,
        rulesSinceMillis: Long
    ): PlanResult {
        val upcoming = upcomingSnapshots(nowMillis, zone, settings, stored)
        val rows = (stored + upcoming).associateBy { it.kind to it.date } // upcoming replaces unfrozen stored rows
        val today = today(nowMillis, zone)

        val snapshots = upcoming.toMutableList()
        val cheats = mutableListOf<Cheat>()
        val seen = sessions.map { it.copy(endMillis = minOf(it.endMillis, nowMillis)) } // nothing after "now"

        for (kind in PeriodKind.entries) {
            var date = firstDate
            while (!date.isAfter(today)) {
                val saved = rows[kind to date]
                // No row and already started: the app didn't run since before it began. Best guess: current settings.
                val row = saved ?: NightCalendar.build(kind, date, settings, zone)
                val beforeAnyRules = row.startMillis < rulesSinceMillis
                if (!beforeAnyRules && !row.finalized && row.hasStarted(nowMillis)) {
                    val found = when (kind) {
                        PeriodKind.NIGHT -> CheatDetector.bedtimeCheats(row, seen)
                        PeriodKind.DAY -> CheatDetector.limitCheats(row, seen)
                    }
                    val existing = storedCheats.filter { it.periodDate == date && it.type == typeOf(kind) }
                    cheats += mergeWithSaved(found, existing)
                    snapshots.removeAll { it.kind == kind && it.date == date }
                    snapshots += row.copy(finalized = row.hasEnded(nowMillis))
                }
                date = date.plusDays(1)
            }
        }
        return PlanResult(snapshots, cheats)
    }

    /**
     * True while tonight's window is running, so its rules (and the override) are locked.
     * Uses the saved row when there is one, because that is what tonight is judged by.
     */
    fun isTonightLocked(nowMillis: Long, zone: ZoneId, settings: Settings, stored: List<RulesSnapshot>): Boolean {
        val today = today(nowMillis, zone)
        return listOf(today.minusDays(1), today).any { date ->
            val night = stored.firstOrNull { it.kind == PeriodKind.NIGHT && it.date == date }
                ?: NightCalendar.night(date, settings, zone)
            night.hasStarted(nowMillis) && !night.hasEnded(nowMillis)
        }
    }

    /**
     * Combines newly found cheats with the ones already saved for the same period, so re-syncs
     * never duplicate:
     * - LIMIT: same app and day is the same cheat. Keep the saved crossing time; minutes and end can grow.
     * - BEDTIME: a found cheat that overlaps or is within [CheatDetector.MERGE_GAP_MS] of a saved one
     *   extends it (start earlier, end later, more minutes). Otherwise it is new.
     * Returns only cheats to insert or update; saved cheats are never removed.
     */
    fun mergeWithSaved(found: List<Cheat>, saved: List<Cheat>): List<Cheat> {
        val working = saved.associateBy { it.key }.toMutableMap()
        val changed = linkedMapOf<CheatKey, Cheat>()

        for (cheat in found.sortedBy { it.startMillis }) {
            val match = working.values.firstOrNull { it.isSameCheatAs(cheat) }
            val result = if (match == null) {
                cheat
            } else {
                match.copy(
                    startMillis = if (cheat.type != CheatType.LIMIT) minOf(match.startMillis, cheat.startMillis) else match.startMillis,
                    endMillis = maxOf(match.endMillis, cheat.endMillis),
                    minutes = maxOf(match.minutes, cheat.minutes)
                )
            }
            working[result.key] = result
            if (result != match) changed[result.key] = result
        }
        return changed.values.toList()
    }

    private fun Cheat.isSameCheatAs(other: Cheat): Boolean =
        packageName == other.packageName && type == other.type && periodDate == other.periodDate && when (type) {
            CheatType.LIMIT -> true
            // Time-based cheats: the same one if they overlap or are within the merge gap.
            CheatType.BEDTIME, CheatType.LOCK_DISABLED ->
                CheatDetector.isClose(startMillis, endMillis, other.startMillis, other.endMillis)
        }

    private fun typeOf(kind: PeriodKind) = when (kind) {
        PeriodKind.NIGHT -> CheatType.BEDTIME
        PeriodKind.DAY -> CheatType.LIMIT
    }

    private fun today(nowMillis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
}
