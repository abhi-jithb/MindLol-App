package com.abhijith.mindlol.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Everything a roast line can mention about one cheat. */
data class RoastContext(
    val appName: String,
    val type: CheatType,
    val startTime: LocalTime,
    val minutes: Int,
    /** This cheat's number this week (Monday to Sunday), counting only cheats that came before it. */
    val cheatsThisWeek: Int,
    /** Same, but only this app. 1 means it's the app's first cheat this week. */
    val sameAppThisWeek: Int,
    val bedtime: LocalTime,
    val wake: LocalTime,
    val limitMinutes: Int?,
    val intensity: RoastIntensity,
    /** Identifies the cheat, so the same cheat always gets the same line. */
    val seed: String
)

/** Picks one line from [RoastLines] for a cheat. Pure: same context, same line. */
object RoastGenerator {

    const val LATE_FROM_HOUR = 1
    const val LATE_UNTIL_HOUR = 6
    const val BIG_OVER_LIMIT_MINUTES = 30
    const val WEEK_COUNT_FROM = 3

    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun roast(context: RoastContext, lines: Map<RoastPool, List<String>> = RoastLines.lines): String {
        val pool = poolsFor(context).flatMap { lines[it].orEmpty() }
        if (pool.isEmpty()) return "You set this rule yourself. Remember that."
        // floorMod keeps the index positive even when hashCode is negative.
        val line = pool[Math.floorMod(context.seed.hashCode(), pool.size)]
        return fill(line, context)
    }

    /**
     * Which pools fit. The most specific situation wins (repeat app, many cheats, late night,
     * far over the limit); the general pool is the fallback. PERSONAL adds its own lines.
     */
    fun poolsFor(context: RoastContext): List<RoastPool> {
        val bedtime = context.type == CheatType.BEDTIME
        if (context.intensity == RoastIntensity.MILD) {
            return listOf(if (bedtime) RoastPool.MILD_BEDTIME else RoastPool.MILD_LIMIT)
        }
        val specific = buildList {
            if (context.sameAppThisWeek >= 2) add(if (bedtime) RoastPool.BEDTIME_REPEAT else RoastPool.LIMIT_REPEAT)
            if (context.cheatsThisWeek >= WEEK_COUNT_FROM) add(RoastPool.WEEK)
            if (bedtime && context.startTime.hour in LATE_FROM_HOUR until LATE_UNTIL_HOUR) add(RoastPool.BEDTIME_LATE)
            if (!bedtime && context.minutes >= BIG_OVER_LIMIT_MINUTES) add(RoastPool.LIMIT_BIG)
        }
        val base = specific.ifEmpty { listOf(if (bedtime) RoastPool.BEDTIME else RoastPool.LIMIT) }
        val personal = if (bedtime) RoastPool.PERSONAL_BEDTIME else RoastPool.PERSONAL_LIMIT
        return if (context.intensity == RoastIntensity.PERSONAL) base + personal else base
    }

    fun fill(line: String, c: RoastContext): String = line
        .replace("{app}", c.appName)
        .replace("{time}", c.startTime.format(timeFormat))
        .replace("{bed}", shortTime(c.bedtime))
        .replace("{wake}", shortTime(c.wake))
        .replace("{minutes}", c.minutes.toString())
        .replace("{limit}", (c.limitMinutes ?: 0).toString())
        .replace("{nth}", ordinal(c.cheatsThisWeek))
        .replace("{count}", c.cheatsThisWeek.toString())
        .replace("{appNth}", ordinal(c.sameAppThisWeek))

    /** "11" for 23:00, "11:30" for 23:30. */
    fun shortTime(t: LocalTime): String {
        val h = if (t.hour % 12 == 0) 12 else t.hour % 12
        return if (t.minute == 0) "$h" else "$h:${t.minute.toString().padStart(2, '0')}"
    }

    fun ordinal(n: Int): String {
        val suffix = if (n % 100 in 11..13) "th" else when (n % 10) {
            1 -> "st"
            2 -> "nd"
            3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }

    /**
     * Builds the context for [cheat] from all known cheats. Only cheats that started before it in
     * the same week count, so adding later cheats never changes an older cheat's line.
     */
    fun contextFor(
        cheat: Cheat,
        allCheats: List<Cheat>,
        appName: String,
        bedtime: LocalTime,
        wake: LocalTime,
        limitMinutes: Int?,
        intensity: RoastIntensity,
        zone: ZoneId
    ): RoastContext {
        val weekStart = cheat.periodDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val upToThis = allCheats.filter {
            !it.periodDate.isBefore(weekStart) && !it.periodDate.isAfter(weekStart.plusDays(6)) &&
                (it.startMillis < cheat.startMillis || it.key == cheat.key)
        }
        return RoastContext(
            appName = appName,
            type = cheat.type,
            startTime = Instant.ofEpochMilli(cheat.startMillis).atZone(zone).toLocalTime(),
            minutes = cheat.minutes,
            cheatsThisWeek = upToThis.size.coerceAtLeast(1),
            sameAppThisWeek = upToThis.count { it.packageName == cheat.packageName }.coerceAtLeast(1),
            bedtime = bedtime,
            wake = wake,
            limitMinutes = limitMinutes,
            intensity = intensity,
            seed = with(cheat.key) { "$packageName|$type|$periodDate|$firstSeenStartMillis" }
        )
    }
}
