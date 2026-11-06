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

/** The unlock steps, in order. [number] is what lock_event.stepReached stores. */
enum class LockStep(val number: Int) {
    STARE(1),
    CONFESSION(2),
    INTERROGATION(3),
    BARGAIN(4),
    LAST_CHANCE(5)
}

/** How one lock screen ended. */
enum class LockOutcome {
    /** Every step done: the app was opened for the chosen minutes. */
    UNLOCKED,
    /** "Go back to sleep" (or "Walk away" on a limit lock) pressed: the app was closed. */
    WENT_BACK,
    /** Left another way (Home, screen off, switched app) before finishing. */
    LEFT
}

/**
 * One run of the accessibility service, from connect to disconnect (one lock_session row).
 * [lastAliveMillis] is a heartbeat, written only while it matters ([LiveLockDecider.heartbeatMatters]).
 * [disconnectedAtMillis] is null when the service died without warning (force-stop, kill, power loss).
 * [bootAtMillis] is when the phone last booted, as seen at connect time.
 */
data class LockSession(
    val connectedAtMillis: Long,
    val lastAliveMillis: Long,
    val disconnectedAtMillis: Long?,
    val bootAtMillis: Long
)

/** A stretch with the screen on (from SCREEN_INTERACTIVE to SCREEN_NON_INTERACTIVE usage events). */
data class ScreenOnInterval(val startMillis: Long, val endMillis: Long)

object LiveLockDecider {

    /** Cheats for a disabled lock are stored under MindLol's own package (shown as "MindLol"). */
    const val LOCK_PACKAGE = "com.abhijith.mindlol"

    const val FIRST_COUNTDOWN_SECONDS = 10
    const val MAX_COUNTDOWN_SECONDS = 320

    /** How often the service writes its heartbeat while it matters. */
    const val HEARTBEAT_INTERVAL_MS = 5 * 60_000L

    /**
     * After a death without warning, the service is assumed alive until this long after its last
     * heartbeat (two missed beats), so a heartbeat that's a little late never looks like a gap.
     */
    const val HEARTBEAT_SLACK_MS = 2 * HEARTBEAT_INTERVAL_MS

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
     * If [packageName] stays in front from [nowMillis], when will it lock, and why?
     * Follows [decide]'s recheck times one after another (bedtime starts, the limit is reached,
     * a grant ends), adding the passing time to today's usage, until one of them is a Lock.
     *
     * Null when it's already locked, never lockable, or nothing in the given snapshots would lock it
     * (for example a grant that ends after wake time). [maxSteps] stops a runaway loop.
     */
    fun upcomingLock(
        packageName: String,
        nowMillis: Long,
        night: RulesSnapshot?,
        today: RulesSnapshot?,
        usedTodayMs: Long,
        grants: List<UnlockGrant>,
        neverLockable: Set<String>,
        maxSteps: Int = 6
    ): UpcomingLock? {
        val first = decide(packageName, nowMillis, night, today, usedTodayMs, grants, neverLockable)
        var at = (first as? LockDecision.Allow)?.recheckAtMillis ?: return null
        repeat(maxSteps) {
            if (at <= nowMillis) return null
            // The app stayed in front the whole time, so its usage grew by the same amount.
            val used = usedTodayMs + (at - nowMillis)
            when (val d = decide(packageName, at, night, today, used, grants, neverLockable)) {
                is LockDecision.Lock -> {
                    val grantEnded = grants.any { it.packageName == packageName && it.endsAtMillis == at }
                    val cause = when {
                        grantEnded -> LockCause.GRANT_END
                        d.reason == LockReason.BEDTIME -> LockCause.BEDTIME
                        else -> LockCause.LIMIT
                    }
                    return UpcomingLock(at, d.reason, cause)
                }
                is LockDecision.Allow -> {
                    val next = d.recheckAtMillis ?: return null
                    if (next <= at) return null // no progress: stop rather than loop
                    at = next
                }
            }
        }
        return null
    }

    /**
     * How long the live lock waits before asking again about the app in front. Never negative,
     * and at least [MIN_RECHECK_DELAY_MS], so a recheck time that's already passed (or a clock
     * change) can't make it ask in a tight loop.
     */
    fun recheckDelayMillis(recheckAtMillis: Long, nowMillis: Long): Long =
        (recheckAtMillis - nowMillis).coerceAtLeast(MIN_RECHECK_DELAY_MS)

    const val MIN_RECHECK_DELAY_MS = 500L

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
     * The escalation count resets twice a day, at bedtime and at wake time, so nights and days
     * escalate separately: it counts unlocks granted since the latest bedtime or wake time that
     * has passed (from saved NIGHT snapshots). Before any of those, everything counts.
     */
    fun unlocksSinceReset(grants: List<UnlockGrant>, nowMillis: Long, storedNights: List<RulesSnapshot>): Int {
        val resetAt = storedNights
            .filter { it.kind == PeriodKind.NIGHT }
            .flatMap { listOf(it.startMillis, it.endMillis) }
            .filter { it <= nowMillis }
            .maxOrNull() ?: Long.MIN_VALUE
        return grants.count { it.grantedAtMillis in resetAt..nowMillis }
    }

    /**
     * The heartbeat is written only while the lock has something to protect: inside a saved
     * night window that has locked apps, or on a day whose saved snapshot has any limit.
     * Outside those times nothing can be cheated, so a stale heartbeat proves nothing.
     */
    fun heartbeatMatters(nowMillis: Long, stored: List<RulesSnapshot>): Boolean = stored.any {
        nowMillis >= it.startMillis && nowMillis < it.endMillis && when (it.kind) {
            PeriodKind.NIGHT -> it.lockedApps.isNotEmpty()
            PeriodKind.DAY -> it.limits.isNotEmpty()
        }
    }

    /**
     * When the lock was off AND the screen was on: the only time a disabled lock can be used to cheat.
     *
     * Gaps come from the saved service sessions (sorted by connect time):
     * - Between two sessions: from the end of one to the start of the next. A clean stop ends at
     *   [LockSession.disconnectedAtMillis]; a death without warning ends [HEARTBEAT_SLACK_MS] after
     *   its last heartbeat. If the phone booted in between, the gap stops at that boot: the time
     *   from boot until the service reconnects is the system starting up, not you.
     * - After the last session, if the service isn't running now: from its end until [nowMillis].
     *
     * Only the parts of each gap where the screen was on count. A phone that was off, a dead
     * battery, or a service killed while you slept (screen off) is never a cheat.
     *
     * Never accuse without evidence: [screenOn] is null when screen data isn't available (screen
     * events need Android 9, API 28; minSdk is 26), and then no gap counts at all.
     */
    fun lockOffIntervals(
        sessions: List<LockSession>,
        screenOn: List<ScreenOnInterval>?,
        nowMillis: Long,
        runningNow: Boolean
    ): List<LockOffInterval> {
        if (screenOn == null) return emptyList()
        val sorted = sessions.sortedBy { it.connectedAtMillis }
        val gaps = mutableListOf<LockOffInterval>()
        sorted.forEachIndexed { i, session ->
            val end = session.disconnectedAtMillis ?: (session.lastAliveMillis + HEARTBEAT_SLACK_MS)
            val next = sorted.getOrNull(i + 1)
            val gapEnd = when {
                next == null -> if (runningNow) null else nowMillis
                next.bootAtMillis > end -> next.bootAtMillis // the phone restarted: stop at boot
                else -> next.connectedAtMillis
            }
            if (gapEnd != null && gapEnd > end) gaps += LockOffInterval(end, gapEnd)
        }
        // Keep only the screen-on parts of each gap.
        return gaps.flatMap { gap ->
            screenOn.mapNotNull { on ->
                val start = maxOf(gap.startMillis, on.startMillis)
                val end = min(gap.endMillis, on.endMillis)
                if (end > start) LockOffInterval(start, end) else null
            }
        }.sortedBy { it.startMillis }
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
