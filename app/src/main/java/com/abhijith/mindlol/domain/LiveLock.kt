package com.abhijith.mindlol.domain

import java.time.LocalDate
import kotlin.math.min

// The live lock's decisions (Phase 3b). Pure Kotlin: the accessibility service gathers the facts
// (which app is in front, saved snapshots, today's usage, unlock grants) and asks these functions.

/** Why an app is being covered. */
enum class LockReason { BEDTIME, LIMIT }

/**
 * Permission to use [packageName] for [minutes], earned by finishing every unlock step.
 * [grantedAtMillis] is when the last step was completed.
 */
data class UnlockGrant(
    val packageName: String,
    val reason: LockReason,
    val grantedAtMillis: Long,
    val minutes: Int
) {
    val endsAtMillis: Long get() = grantedAtMillis + minutes * 60_000L
}

sealed interface LockDecision {
    /**
     * Let the app run. [recheckAtMillis]: the next moment the answer could change while the app
     * stays in front (bedtime starts, the limit is reached, a grant runs out). Null = no change coming.
     */
    data class Allow(val recheckAtMillis: Long?) : LockDecision

    /** Cover the app. [periodDate] is the night (BEDTIME) or day (LIMIT) the lock belongs to. */
    data class Lock(val reason: LockReason, val periodDate: LocalDate) : LockDecision
}

/** A stretch of time when the live lock wasn't running (service off, or MindLol force-stopped). */
data class LockOffInterval(val startMillis: Long, val endMillis: Long)

object LiveLockDecider {

    /** Cheats for a disabled lock are stored under MindLol's own package (shown as "MindLol"). */
    const val LOCK_PACKAGE = "com.abhijith.mindlol"

    const val FIRST_COUNTDOWN_SECONDS = 10
    const val MAX_COUNTDOWN_SECONDS = 320

    private const val MINUTE_MS = 60_000L

    /**
     * Should [packageName], just brought to the front at [nowMillis], be covered?
     *
     * Rules come only from saved, frozen snapshots ("no rules, no lock"):
     * - BEDTIME: [night] is running and the app is in its locked apps.
     * - LIMIT: [today] has a limit for the app and [usedTodayMs] is over it (strictly more).
     * BEDTIME wins when both apply. [neverLockable] apps (MindLol, Settings, the dialer) and apps
     * with a running [grants] entry are always allowed.
     *
     * @param night the snapshot of the night that contains [nowMillis], or the next one (for recheck).
     * @param today today's DAY snapshot, if saved.
     * @param usedTodayMs this app's foreground time today, up to [nowMillis].
     */
    fun decide(
        packageName: String,
        nowMillis: Long,
        night: RulesSnapshot?,
        today: RulesSnapshot?,
        usedTodayMs: Long,
        grants: List<UnlockGrant>,
        neverLockable: Set<String>
    ): LockDecision {
        if (packageName in neverLockable) return LockDecision.Allow(null)

        val grant = grants
            .filter { it.packageName == packageName && nowMillis < it.endsAtMillis && nowMillis >= it.grantedAtMillis }
            .maxByOrNull { it.endsAtMillis }

        val lockedTonight = night != null && packageName in night.lockedApps
        val limitMs = today?.limits?.get(packageName)?.let { it * MINUTE_MS }
        val insideToday = today != null && nowMillis >= today.startMillis && nowMillis < today.endMillis

        if (grant == null) {
            // `night != null` inside the condition smart-casts it, so night.date is safe to use here.
            if (night != null && lockedTonight && nowMillis >= night.startMillis && nowMillis < night.endMillis) {
                return LockDecision.Lock(LockReason.BEDTIME, night.date)
            }
            if (today != null && insideToday && limitMs != null && usedTodayMs > limitMs) {
                return LockDecision.Lock(LockReason.LIMIT, today.date)
            }
        }

        // Allowed: when could that change while the app stays open?
        val candidates = listOfNotNull(
            grant?.endsAtMillis,
            night?.startMillis?.takeIf { lockedTonight && it > nowMillis },
            // The limit is crossed once usage passes it; +1 ms because "over" is strictly more.
            limitMs?.takeIf { insideToday && usedTodayMs <= it }?.let { nowMillis + (it - usedTodayMs) + 1 }
        )
        return LockDecision.Allow(candidates.minOrNull())
    }

    /**
     * Step 1's countdown before "I want to cheat" unlocks: 10 s, doubling with every unlock
     * already granted since the count last reset, capped at 320 s.
     */
    fun countdownSeconds(unlocksSinceReset: Int): Int {
        if (unlocksSinceReset <= 0) return FIRST_COUNTDOWN_SECONDS
        // Cap the shift so the doubling can never overflow.
        val doubled = FIRST_COUNTDOWN_SECONDS.toLong() shl min(unlocksSinceReset, 10)
        return min(doubled, MAX_COUNTDOWN_SECONDS.toLong()).toInt()
    }

    /**
     * The escalation count resets each night: it counts unlocks granted since the start of the
     * most recent night that has begun. Before any night has begun, everything counts.
     */
    fun unlocksSinceReset(grants: List<UnlockGrant>, nowMillis: Long, storedNights: List<RulesSnapshot>): Int {
        val resetAt = storedNights
            .filter { it.kind == PeriodKind.NIGHT && it.startMillis <= nowMillis }
            .maxOfOrNull { it.startMillis } ?: Long.MIN_VALUE
        return grants.count { it.grantedAtMillis in resetAt..nowMillis }
    }

    /**
     * LOCK_DISABLED cheats for one night: every stretch the lock was off that overlaps the night's
     * window (up to [nowMillis]). Off just before bedtime and still off after it counts too, from
     * bedtime on. Stretches closer than [CheatDetector.MERGE_GAP_MS] become one cheat.
     * A night with nothing locked has nothing to protect, so it never produces one.
     */
    fun lockDisabledCheats(night: RulesSnapshot, offIntervals: List<LockOffInterval>, nowMillis: Long): List<Cheat> {
        if (night.lockedApps.isEmpty()) return emptyList()
        val windowEnd = min(night.endMillis, nowMillis)
        val clipped = offIntervals
            .map { LockOffInterval(maxOf(it.startMillis, night.startMillis), min(it.endMillis, windowEnd)) }
            .filter { it.endMillis > it.startMillis }
            .sortedBy { it.startMillis }

        val merged = mutableListOf<MutableList<LockOffInterval>>()
        var groupEnd = Long.MIN_VALUE
        for (off in clipped) {
            if (merged.isNotEmpty() && off.startMillis - groupEnd < CheatDetector.MERGE_GAP_MS) merged.last() += off
            else merged += mutableListOf(off)
            groupEnd = maxOf(groupEnd, off.endMillis)
        }
        return merged.map { group ->
            Cheat(
                packageName = LOCK_PACKAGE,
                type = CheatType.LOCK_DISABLED,
                periodDate = night.date,
                startMillis = group.first().startMillis,
                endMillis = group.maxOf { it.endMillis },
                minutes = CheatDetector.roundUpMinutes(group.sumOf { it.endMillis - it.startMillis })
            )
        }
    }
}
