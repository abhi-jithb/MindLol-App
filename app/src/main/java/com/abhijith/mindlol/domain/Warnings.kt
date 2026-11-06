package com.abhijith.mindlol.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

// Warnings before a lock (Step 3, item W1). Pure Kotlin: the live lock service asks these functions
// what to show (a pill over the app, or a notification) and when to check again.
// Rule: every number shown is computed from real times. No fixed "5 min" text when 3 are left.

/** What will make the app in front lock, if it stays in front. */
enum class LockCause { BEDTIME, LIMIT, GRANT_END }

/**
 * The app in front will lock at [atMillis] for [reason], because of [cause]. [periodDate] is the
 * night (BEDTIME) or day (LIMIT) the lock belongs to.
 */
data class UpcomingLock(val atMillis: Long, val reason: LockReason, val cause: LockCause, val periodDate: LocalDate) {
    /**
     * The same lock as [other] for the pill's memory. A limit's lock time moves later whenever you
     * leave the app (usage stops growing), so for a LIMIT only the day counts: its warnings show
     * once per app per day. Bedtime and grant ends are fixed times.
     */
    fun sameAs(other: UpcomingLock): Boolean =
        reason == other.reason && cause == other.cause && periodDate == other.periodDate &&
            (cause == LockCause.LIMIT || atMillis == other.atMillis)
}

/** How the pill looks right now. */
enum class PillMode {
    /** A warning ("4:59 left · Got it"). Stays until tapped. */
    WARNING,
    /** The countdown during an unlock grant, full size ("4:59 left"). Shrinks to a chip after 10 s. */
    COUNTDOWN,
    /** The grant countdown shrunk to a small "4:59". Tap to expand. */
    CHIP
}

/**
 * What the pill remembers about one upcoming lock. A different lock (another app, a new grant,
 * a changed time) starts a fresh memory.
 *
 * @param firstSeenMillis when this lock was first seen. Thresholds already passed then don't fire
 *   for a grant (you just unlocked), but do fire once for an app you open close to its lock.
 * @param shownThresholds the warning thresholds (in ms before the lock) already shown.
 * @param warningShowing a warning pill is up and waiting for "Got it".
 * @param expandedAtMillis when the grant countdown was last shown full size (grant start, or a tap on the chip).
 */
data class PillMemory(
    val lock: UpcomingLock,
    val firstSeenMillis: Long,
    val shownThresholds: Set<Long> = emptySet(),
    val warningShowing: Boolean = false,
    val expandedAtMillis: Long = firstSeenMillis
)

/** The pill to show now (null = none), the updated memory, and the next moment that changes anything. */
data class PillPlan(val mode: PillMode?, val memory: PillMemory?, val nextChangeAtMillis: Long?)

object WarningPlanner {

    const val MINUTE_MS = 60_000L

    /** Warning pills at 5 and 1 minutes before a lock. */
    val PILL_THRESHOLDS_MS: List<Long> = listOf(5 * MINUTE_MS, 1 * MINUTE_MS)

    /** The bedtime notification, up to 15 minutes before bedtime. */
    const val BEDTIME_NOTICE_MS = 15 * MINUTE_MS

    /** The grant countdown shrinks to a chip after this long at full size. */
    const val CHIP_AFTER_MS = 10_000L

    /**
     * The pill for the app in front.
     *
     * - A threshold (5:00, 1:00) crossed and not yet shown → WARNING, which stays until [tap].
     *   Opening an app with 3 min left shows the 5-min warning at once (with the real 3:00).
     *   When both are already passed, only one warning shows (the 1-min one).
     * - During a grant, thresholds already passed when the grant started never fire: you just
     *   chose that many minutes. Otherwise the countdown shows: COUNTDOWN for 10 s, then CHIP.
     * - No upcoming lock, or it's already here → no pill.
     */
    fun pill(now: Long, upcoming: UpcomingLock?, memory: PillMemory?): PillPlan {
        if (upcoming == null || now >= upcoming.atMillis) return PillPlan(null, null, null)
        var mem = if (memory != null && memory.lock.sameAs(upcoming)) memory.copy(lock = upcoming) else PillMemory(upcoming, firstSeenMillis = now)
        val left = upcoming.atMillis - now
        val isGrant = upcoming.cause == LockCause.GRANT_END

        // Thresholds that have been reached and not yet shown. For a grant, those already passed
        // at the grant's start are skipped (marked shown without showing).
        val reached = PILL_THRESHOLDS_MS.filter { left <= it && it !in mem.shownThresholds }
        if (reached.isNotEmpty()) {
            // First sight of a grant: these were already passed when you chose the minutes.
            val skipAll = isGrant && mem.firstSeenMillis == now
            mem = mem.copy(
                shownThresholds = mem.shownThresholds + reached,
                warningShowing = mem.warningShowing || !skipAll
            )
        }

        val nextThreshold = PILL_THRESHOLDS_MS.filter { it !in mem.shownThresholds && left > it }
            .maxOrNull()?.let { upcoming.atMillis - it }

        if (mem.warningShowing) return PillPlan(PillMode.WARNING, mem, nextThreshold)
        if (!isGrant) return PillPlan(null, mem, nextThreshold)

        val shrinkAt = mem.expandedAtMillis + CHIP_AFTER_MS
        val mode = if (now < shrinkAt) PillMode.COUNTDOWN else PillMode.CHIP
        val next = listOfNotNull(nextThreshold, shrinkAt.takeIf { now < it }).minOrNull()
        return PillPlan(mode, mem, next)
    }

    /**
     * A tap on the pill. A warning is dismissed ("Got it"); the next threshold can still show it
     * again. A full countdown shrinks to the chip; the chip expands for another 10 s.
     */
    fun tap(now: Long, memory: PillMemory, mode: PillMode): PillMemory = when (mode) {
        PillMode.WARNING -> memory.copy(warningShowing = false, expandedAtMillis = now - CHIP_AFTER_MS)
        PillMode.COUNTDOWN -> memory.copy(expandedAtMillis = now - CHIP_AFTER_MS)
        PillMode.CHIP -> memory.copy(expandedAtMillis = now)
    }

    /**
     * Should the bedtime notification show now? Once per night ([alreadyShownForNight] is the night
     * it was last shown for), only for a saved night with locked apps, during the 15 minutes before
     * bedtime.
     */
    fun bedtimeNoticeDue(now: Long, night: RulesSnapshot?, alreadyShownForNight: LocalDate?): Boolean =
        night != null &&
            night.kind == PeriodKind.NIGHT &&
            night.lockedApps.isNotEmpty() &&
            night.date != alreadyShownForNight &&
            now < night.startMillis &&
            now >= night.startMillis - BEDTIME_NOTICE_MS

    /** When the bedtime notification window opens (null if it already has, or nothing is locked). */
    fun bedtimeNoticeAt(now: Long, night: RulesSnapshot?): Long? {
        if (night == null || night.lockedApps.isEmpty()) return null
        val at = night.startMillis - BEDTIME_NOTICE_MS
        return at.takeIf { it > now }
    }
}

/** The words of the warnings. Every number comes from real times. */
object WarningText {

    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    /** "4:59" for 4 min 59.x s left. Rounded down, never negative. */
    fun countdown(leftMillis: Long): String {
        val totalSeconds = (leftMillis.coerceAtLeast(0L) / 1000)
        return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
    }

    fun pill(mode: PillMode, leftMillis: Long): String = when (mode) {
        PillMode.WARNING -> "${countdown(leftMillis)} left · Got it"
        PillMode.COUNTDOWN -> "${countdown(leftMillis)} left"
        PillMode.CHIP -> countdown(leftMillis)
    }

    /**
     * Minutes as people say them: rounded to the nearest minute ("5 min" at 4:59), and
     * "less than a minute" under 30 seconds.
     */
    fun minutes(leftMillis: Long): String {
        val m = Math.round(leftMillis.coerceAtLeast(0L) / 60_000.0)
        return if (m < 1) "less than a minute" else "$m min"
    }

    /** Fallback when the pill can't show: "5 min left on Instagram". */
    fun limitNotification(appName: String, leftMillis: Long): String = "${minutes(leftMillis)} left on $appName"

    /**
     * "Bedtime in 15 min. Instagram locks at 23:00." / "… Instagram and 2 more lock at 23:00."
     * [lockedNames] are tonight's locked apps, the one in front first if it's locked.
     */
    fun bedtimeNotification(leftMillis: Long, bedtime: LocalTime, lockedNames: List<String>): String {
        val apps = when (lockedNames.size) {
            0 -> "Your locked apps lock"
            1 -> "${lockedNames[0]} locks"
            else -> "${lockedNames[0]} and ${lockedNames.size - 1} more lock"
        }
        return "Bedtime in ${minutes(leftMillis)}. $apps at ${bedtime.format(clock)}."
    }
}
