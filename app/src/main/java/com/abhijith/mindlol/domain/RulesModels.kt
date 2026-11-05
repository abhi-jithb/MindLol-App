package com.abhijith.mindlol.domain

import java.time.LocalDate

// Shapes used by the rules engine (NightCalendar, CheatDetector, RulesPlanner).
// Pure Kotlin: times are epoch milliseconds, dates are local calendar dates.

/** One stretch of time an app was on screen. */
data class AppSession(
    val packageName: String,
    val startMillis: Long,
    val endMillis: Long
)

/**
 * NIGHT: one sleep window, named after the evening it starts (23:00 Thu → 08:00 Fri is Thursday).
 * DAY: one calendar day, midnight to midnight, used for daily limits.
 */
enum class PeriodKind { NIGHT, DAY }

/**
 * BEDTIME: a locked app used inside the sleep window. LIMIT: an app used past its daily limit.
 * LOCK_DISABLED: the live lock was turned off (or MindLol force-stopped) during a sleep window.
 */
enum class CheatType { BEDTIME, LIMIT, LOCK_DISABLED }

/**
 * The rules frozen for one night or day. Once [startMillis] has passed, the row never changes
 * its rules again; only [finalized] flips to true after [endMillis].
 */
data class RulesSnapshot(
    val kind: PeriodKind,
    val date: LocalDate,
    val startMillis: Long,
    val endMillis: Long,
    /** NIGHT only: true when the "tonight is different" window was used. */
    val overrideApplied: Boolean = false,
    /** NIGHT only: apps locked that night. */
    val lockedApps: Set<String> = emptySet(),
    /** DAY only: daily limit in minutes by package. */
    val limits: Map<String, Int> = emptyMap(),
    /** True once the period has ended and its result is final. */
    val finalized: Boolean = false
) {
    fun hasStarted(nowMillis: Long) = nowMillis >= startMillis
    fun hasEnded(nowMillis: Long) = nowMillis >= endMillis
}

/**
 * One broken rule.
 *
 * Identity (what makes two cheats "the same one"), see [key]:
 * - LIMIT: one per app per day, so (package, LIMIT, day). [startMillis] is when the limit was
 *   crossed, stored as data only.
 * - BEDTIME and LOCK_DISABLED: (package, type, night, [firstSeenStartMillis]). The first-seen start
 *   never changes, even if a later refresh finds the cheat really began earlier and [startMillis] moves back.
 */
data class Cheat(
    val packageName: String,
    val type: CheatType,
    val periodDate: LocalDate,
    val startMillis: Long,
    val endMillis: Long,
    /** BEDTIME: minutes inside the window. LIMIT: minutes over the limit. LOCK_DISABLED: minutes the lock was off. Rounded up, at least 1. */
    val minutes: Int,
    /** BEDTIME: the start when this cheat was first saved. LIMIT: always [NO_FIRST_SEEN]. */
    val firstSeenStartMillis: Long = if (type == CheatType.LIMIT) NO_FIRST_SEEN else startMillis
) {
    val key: CheatKey get() = CheatKey(packageName, type, periodDate, firstSeenStartMillis)

    companion object {
        const val NO_FIRST_SEEN = 0L
    }
}

data class CheatKey(
    val packageName: String,
    val type: CheatType,
    val periodDate: LocalDate,
    val firstSeenStartMillis: Long
)

/** What one refresh should save: snapshot rows to upsert, and cheats to insert or extend. */
data class PlanResult(
    val snapshots: List<RulesSnapshot>,
    val cheats: List<Cheat>
)
