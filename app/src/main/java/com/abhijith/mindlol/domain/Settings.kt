package com.abhijith.mindlol.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

// The user's own rules. Pure Kotlin (no Android), so the rules engine and tests can use it.
// Times of day are stored as minutes since midnight: 23:00 is 1380, 08:00 is 480.

/** How hard the roasts hit. PERSONAL unlocks the more personal lines and question cards. */
enum class RoastIntensity { MILD, SAVAGE, PERSONAL }

/**
 * A one-night sleep window that replaces the normal one, for nights you know will run late.
 * It stops applying at [expiresAtMillis], which is the wake time that ends that night.
 */
data class TonightOverride(
    val bedtimeMinutes: Int,
    val wakeMinutes: Int,
    val expiresAtMillis: Long
)

data class Settings(
    val bedtimeMinutes: Int = DEFAULT_BEDTIME,
    val wakeMinutes: Int = DEFAULT_WAKE,
    /** The last override that was set. It is kept after it expires so past nights can still use it. */
    val tonightOverride: TonightOverride? = null,
    /** Package names of apps that are locked inside the sleep window. */
    val lockedApps: Set<String> = emptySet(),
    /** Daily limit in minutes, by package name. Apps with no limit are simply absent. */
    val appLimits: Map<String, Int> = emptyMap(),
    val roastIntensity: RoastIntensity = RoastIntensity.SAVAGE
) {
    /** The override, but only while it still applies. */
    fun activeOverride(nowMillis: Long): TonightOverride? =
        tonightOverride?.takeIf { nowMillis < it.expiresAtMillis }

    companion object {
        const val DEFAULT_BEDTIME = 23 * 60
        const val DEFAULT_WAKE = 8 * 60
    }
}

object SleepTime {
    const val MINUTES_PER_DAY = 24 * 60

    fun toLocalTime(minutes: Int): LocalTime = LocalTime.of(minutes / 60, minutes % 60)

    fun fromLocalTime(time: LocalTime): Int = time.hour * 60 + time.minute

    /**
     * When a "tonight" override set at [now] should stop: the next time the clock reaches
     * [wakeMinutes]. Set at 21:00 with wake 10:00, it ends tomorrow 10:00. Set at 01:00 (the
     * night already running), it ends today 10:00.
     */
    fun overrideExpiry(now: ZonedDateTime, wakeMinutes: Int): ZonedDateTime {
        val wakeToday = now.toLocalDate().atTime(toLocalTime(wakeMinutes)).atZone(now.zone)
        return if (wakeToday.isAfter(now)) wakeToday else wakeToday.plusDays(1)
    }

    fun overrideExpiryMillis(nowMillis: Long, wakeMinutes: Int, zone: ZoneId): Long =
        overrideExpiry(Instant.ofEpochMilli(nowMillis).atZone(zone), wakeMinutes).toInstant().toEpochMilli()

    /** Length of the window from bedtime to wake, crossing midnight if needed. */
    fun windowLength(bedtimeMinutes: Int, wakeMinutes: Int): Int =
        Math.floorMod(wakeMinutes - bedtimeMinutes, MINUTES_PER_DAY)
}

/** A launcher app the user can lock or limit. */
data class InstalledApp(
    val packageName: String,
    val label: String
)
