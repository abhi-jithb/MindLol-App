package com.abhijith.mindlol.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Where each night and day starts and ends, from a set of [Settings].
 *
 * Night D ends at the wake time on D+1 and starts one window length before that:
 * 23:00 → 08:00 gives D 23:00 → D+1 08:00; an override of 02:00 → 10:00 gives D+1 02:00 → D+1 10:00.
 * Both are "night D", named after the evening.
 */
object NightCalendar {

    /** Night [date] built from [settings]: the override's window if it belongs to this night, else the normal one. */
    fun night(date: LocalDate, settings: Settings, zone: ZoneId): RulesSnapshot {
        val override = settings.tonightOverride?.takeIf { overrideNightDate(it.expiresAtMillis, zone) == date }
        val bed = override?.bedtimeMinutes ?: settings.bedtimeMinutes
        val wake = override?.wakeMinutes ?: settings.wakeMinutes
        val end = date.plusDays(1).atTime(SleepTime.toLocalTime(wake)).atZone(zone)
        val start = end.minusMinutes(SleepTime.windowLength(bed, wake).toLong())
        return RulesSnapshot(
            kind = PeriodKind.NIGHT,
            date = date,
            startMillis = start.toInstant().toEpochMilli(),
            endMillis = end.toInstant().toEpochMilli(),
            overrideApplied = override != null,
            lockedApps = settings.lockedApps
        )
    }

    /** Day [date], midnight to midnight, with today's limits from [settings]. */
    fun day(date: LocalDate, settings: Settings, zone: ZoneId): RulesSnapshot = RulesSnapshot(
        kind = PeriodKind.DAY,
        date = date,
        startMillis = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        endMillis = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        limits = settings.appLimits
    )

    fun build(kind: PeriodKind, date: LocalDate, settings: Settings, zone: ZoneId): RulesSnapshot =
        when (kind) {
            PeriodKind.NIGHT -> night(date, settings, zone)
            PeriodKind.DAY -> day(date, settings, zone)
        }

    /** An override ends at the wake time of its night, so its night is the day before it expires. */
    private fun overrideNightDate(expiresAtMillis: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(expiresAtMillis).atZone(zone).toLocalDate().minusDays(1)
}
