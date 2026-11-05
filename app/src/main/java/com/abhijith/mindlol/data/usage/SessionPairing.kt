package com.abhijith.mindlol.data.usage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** The few event kinds the pairing logic cares about. Mapped from Android's UsageEvents. */
enum class EventKind {
    /** An activity of the app came to the front. */
    RESUMED,

    /** An activity of the app left the front (paused or stopped). */
    PAUSED,

    /** Screen turned off or the phone shut down: nothing is in the foreground any more. */
    SCREEN_OFF,

    /** The lock screen was dismissed. Counted as one unlock. */
    UNLOCK
}

/** One usage event, as plain data so tests can build them by hand. */
data class UsageEventRecord(
    val packageName: String,
    val className: String,
    val timeMs: Long,
    val kind: EventKind
)

/** One calendar day of usage in the phone's time zone. */
data class DayUsage(
    val date: LocalDate,
    /** Foreground time per package, in ms. Every package, including excluded ones. */
    val perAppMs: Map<String, Long>,
    /** Screen time for the day: the union of all app sessions, so overlaps count once. */
    val totalMs: Long,
    val unlocks: Int
)

/**
 * Turns a stream of RESUMED/PAUSED events into per-day foreground time.
 * Pure Kotlin with no Android types, so it can be unit-tested on the JVM.
 */
object SessionPairing {

    private data class Interval(val packageName: String, val start: Long, val end: Long)

    /** An app counts as "open" while at least one of its activities is resumed. */
    private class OpenApp(val since: Long, val activities: MutableSet<String>)

    /**
     * @param events events in any order. Events before [rangeStartMs] are allowed and recommended
     *   (a few hours of lead-in), so apps already open at midnight are paired correctly.
     * @param rangeStartMs start of the first day (local midnight).
     * @param rangeEndMs end of the range: next midnight, or "now" for today.
     * @param excludedFromTotal packages left out of [DayUsage.totalMs] (launcher, MindLol itself).
     *   They still appear in [DayUsage.perAppMs]; rankings filter them later.
     * @return one [DayUsage] per calendar day in the range, including days with no usage.
     */
    fun dailyUsage(
        events: List<UsageEventRecord>,
        rangeStartMs: Long,
        rangeEndMs: Long,
        zone: ZoneId,
        excludedFromTotal: Set<String> = emptySet()
    ): List<DayUsage> {
        if (rangeEndMs <= rangeStartMs) return emptyList()

        val sessions = mutableListOf<Interval>()
        val unlockTimes = mutableListOf<Long>()
        val open = mutableMapOf<String, OpenApp>()
        val seen = mutableSetOf<String>()

        fun close(pkg: String, at: Long) {
            open.remove(pkg)?.let { sessions += Interval(pkg, it.since, at) }
        }

        for (e in events.sortedBy { it.timeMs }) {
            when (e.kind) {
                EventKind.RESUMED -> {
                    val app = open[e.packageName]
                    if (app == null) open[e.packageName] = OpenApp(e.timeMs, mutableSetOf(e.className))
                    else app.activities += e.className // another activity of an app that is already open
                    seen += e.packageName
                }
                EventKind.PAUSED -> {
                    val app = open[e.packageName]
                    if (app != null) {
                        app.activities -= e.className
                        if (app.activities.isEmpty()) close(e.packageName, e.timeMs)
                    } else if (e.packageName !in seen) {
                        // A PAUSED with no RESUMED before it: the app was already open when our
                        // events begin. Count it from the start of the range.
                        sessions += Interval(e.packageName, rangeStartMs, e.timeMs)
                    }
                    seen += e.packageName
                }
                EventKind.SCREEN_OFF -> open.keys.toList().forEach { close(it, e.timeMs) }
                EventKind.UNLOCK -> unlockTimes += e.timeMs
            }
        }
        // No PAUSED yet: still on screen at the end of the range (usually "now").
        open.keys.toList().forEach { close(it, rangeEndMs) }

        // Clip everything to the requested range.
        val clipped = sessions.mapNotNull { s ->
            val start = maxOf(s.start, rangeStartMs)
            val end = minOf(s.end, rangeEndMs)
            if (end > start) s.copy(start = start, end = end) else null
        }

        val days = daysBetween(rangeStartMs, rangeEndMs, zone)
        val perApp = days.associateWith { mutableMapOf<String, Long>() }
        for (s in clipped) {
            for ((date, ms) in splitByDay(s.start, s.end, zone)) {
                val map = perApp.getValue(date)
                map[s.packageName] = (map[s.packageName] ?: 0L) + ms
            }
        }

        val totals = days.associateWith { 0L }.toMutableMap()
        for ((start, end) in union(clipped.filter { it.packageName !in excludedFromTotal })) {
            for ((date, ms) in splitByDay(start, end, zone)) totals[date] = totals.getValue(date) + ms
        }

        val unlocks = unlockTimes
            .filter { it in rangeStartMs until rangeEndMs }
            .groupingBy { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .eachCount()

        return days.map { date ->
            DayUsage(date, perApp.getValue(date), totals.getValue(date), unlocks[date] ?: 0)
        }
    }

    /** Merges overlapping intervals, so two apps on screen together count once. */
    private fun union(intervals: List<Interval>): List<Pair<Long, Long>> {
        val merged = mutableListOf<Pair<Long, Long>>()
        for (s in intervals.sortedBy { it.start }) {
            val last = merged.lastOrNull()
            if (last != null && s.start <= last.second) merged[merged.size - 1] = last.first to maxOf(last.second, s.end)
            else merged += s.start to s.end
        }
        return merged
    }

    /** Cuts [start, end) at each local midnight. 23:50 to 00:10 becomes 10 min + 10 min. */
    fun splitByDay(startMs: Long, endMs: Long, zone: ZoneId): List<Pair<LocalDate, Long>> {
        val parts = mutableListOf<Pair<LocalDate, Long>>()
        var cursor = startMs
        while (cursor < endMs) {
            val date = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val segmentEnd = minOf(endMs, nextMidnight)
            parts += date to (segmentEnd - cursor)
            cursor = segmentEnd
        }
        return parts
    }

    private fun daysBetween(startMs: Long, endMs: Long, zone: ZoneId): List<LocalDate> {
        val first = Instant.ofEpochMilli(startMs).atZone(zone).toLocalDate()
        val last = Instant.ofEpochMilli(endMs - 1).atZone(zone).toLocalDate()
        return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.toList()
    }
}
