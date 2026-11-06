package com.abhijith.mindlol.domain

/**
 * Finds cheats in one frozen period. Knows nothing about storage or what was saved before;
 * [RulesPlanner] decides which periods to judge and merges results with saved cheats.
 */
object CheatDetector {

    /**
     * Two sessions of the same app closer than this (end of one → start of next) are one cheat.
     * Android splits one real scroll into several sessions when you glance at a notification.
     */
    const val MERGE_GAP_MS = 5 * 60_000L

    private const val MINUTE_MS = 60_000L

    /** Locked apps used inside the night's window. No grace period: any time counts. */
    fun bedtimeCheats(night: RulesSnapshot, sessions: List<AppSession>): List<Cheat> {
        val inside = sessions
            .filter { it.packageName in night.lockedApps }
            .mapNotNull { clip(it, night.startMillis, night.endMillis) }
            .sortedBy { it.startMillis }

        return inside.groupBy { it.packageName }.flatMap { (pkg, appSessions) ->
            mergeClose(appSessions).map { group ->
                Cheat(
                    packageName = pkg,
                    type = CheatType.BEDTIME,
                    periodDate = night.date,
                    startMillis = group.first().startMillis,
                    endMillis = group.last().endMillis,
                    // Only time actually in the app counts, not the gaps between merged sessions.
                    minutes = roundUpMinutes(group.sumOf { it.endMillis - it.startMillis })
                )
            }
        }.sortedBy { it.startMillis }
    }

    /**
     * Apps used past their daily limit. One cheat per app per day, starting at the moment the
     * limit was crossed. Only time after the limit counts as the cheat's minutes.
     */
    fun limitCheats(day: RulesSnapshot, sessions: List<AppSession>): List<Cheat> =
        day.limits.mapNotNull { (pkg, limitMinutes) ->
            val limitMs = limitMinutes * MINUTE_MS
            val used = sessions
                .filter { it.packageName == pkg }
                .mapNotNull { clip(it, day.startMillis, day.endMillis) }
                .sortedBy { it.startMillis }

            var total = 0L
            var crossedAt: Long? = null
            for (s in used) {
                val length = s.endMillis - s.startMillis
                if (crossedAt == null && total + length > limitMs) crossedAt = s.startMillis + (limitMs - total)
                total += length
            }
            crossedAt?.let {
                Cheat(
                    packageName = pkg,
                    type = CheatType.LIMIT,
                    periodDate = day.date,
                    startMillis = it,
                    endMillis = used.last().endMillis,
                    minutes = roundUpMinutes(total - limitMs)
                )
            }
        }.sortedBy { it.startMillis }

    /** 10 seconds is still a cheat, so it shows as 1 minute, never 0. */
    fun roundUpMinutes(ms: Long): Int = ((ms + MINUTE_MS - 1) / MINUTE_MS).toInt().coerceAtLeast(1)

    /** True when two time ranges overlap or are less than [MERGE_GAP_MS] apart. */
    fun isClose(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Boolean =
        bStart < aEnd + MERGE_GAP_MS && aStart < bEnd + MERGE_GAP_MS

    private fun clip(s: AppSession, from: Long, to: Long): AppSession? {
        val start = maxOf(s.startMillis, from)
        val end = minOf(s.endMillis, to)
        return if (end > start) s.copy(startMillis = start, endMillis = end) else null
    }

    /** Groups one app's sessions (sorted by start) so each group's gaps are all under the merge gap. */
    private fun mergeClose(sorted: List<AppSession>): List<List<AppSession>> {
        val groups = mutableListOf<MutableList<AppSession>>()
        var groupEnd = Long.MIN_VALUE
        for (s in sorted) {
            if (groups.isNotEmpty() && s.startMillis - groupEnd < MERGE_GAP_MS) {
                groups.last() += s
            } else {
                groups += mutableListOf(s)
            }
            groupEnd = maxOf(groupEnd, s.endMillis)
        }
        return groups
    }
}
