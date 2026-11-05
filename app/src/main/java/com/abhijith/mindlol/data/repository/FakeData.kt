package com.abhijith.mindlol.data.repository

import com.abhijith.mindlol.domain.AppRule
import com.abhijith.mindlol.domain.AppUsage
import com.abhijith.mindlol.domain.CheatEntry
import com.abhijith.mindlol.domain.PermissionInfo
import com.abhijith.mindlol.domain.SettingsValues
import com.abhijith.mindlol.domain.StatsPeriod
import com.abhijith.mindlol.domain.Streak
import com.abhijith.mindlol.domain.TodaySummary
import com.abhijith.mindlol.domain.TotalStat
import com.abhijith.mindlol.domain.UsageSeries
import java.time.LocalTime

/**
 * Every fake value in Phase 1 lives here and nowhere else.
 * Screens read only from this object, so Phase 2 can replace it with a real repository.
 * "Today" is Thursday, 5 Nov 2026.
 */
object FakeData {

    // How each period's total splits across apps. Shares add up to 100.
    private val appShares = listOf(
        "Instagram" to 39,
        "YouTube" to 28,
        "WhatsApp" to 16,
        "Chrome" to 10,
        "X" to 7
    )

    /** Splits [total] minutes by appShares. The last app takes the rounding remainder. */
    private fun breakdown(total: Int): List<AppUsage> {
        val firsts = appShares.dropLast(1).map { (name, share) -> AppUsage(name, total * share / 100) }
        val rest = total - firsts.sumOf { it.minutes }
        return firsts + AppUsage(appShares.last().first, rest)
    }

    // ---------- Stats series ----------

    // Minutes per hour today, midnight to 11 PM. Sums to 134 (2h 14m).
    private val hourlyToday = listOf(
        3, 0, 0, 0, 0, 0, 0, 4, 9, 6, 5, 8,
        10, 7, 6, 5, 8, 9, 11, 14, 12, 9, 4, 4
    )

    private val day = UsageSeries(
        period = StatsPeriod.DAY,
        labels = listOf(
            "12a", "1a", "2a", "3a", "4a", "5a", "6a", "7a", "8a", "9a", "10a", "11a",
            "12p", "1p", "2p", "3p", "4p", "5p", "6p", "7p", "8p", "9p", "10p", "11p"
        ),
        minutes = hourlyToday,
        apps = breakdown(hourlyToday.sum()),
        fullLabels = (0..23).map { h ->
            val hour12 = if (h % 12 == 0) 12 else h % 12
            "$hour12 ${if (h < 12) "AM" else "PM"}"
        }
    )

    // Last 7 days, ending today (Thursday).
    private val weekMinutes = listOf(196, 241, 227, 158, 172, 149, 134)
    private val week = UsageSeries(
        period = StatsPeriod.WEEK,
        labels = listOf("Fri", "Sat", "Sun", "Mon", "Tue", "Wed", "Thu"),
        minutes = weekMinutes,
        apps = breakdown(weekMinutes.sum())
    )

    // Last 30 days, 7 Oct to 5 Nov.
    private val monthMinutes = listOf(
        212, 188, 254, 231, 199, 176, 205, 263, 240, 219,
        187, 172, 230, 248, 201, 166, 158, 214, 236, 192,
        181, 170, 209, 196, 241, 227, 158, 172, 149, 134
    )
    private val month = UsageSeries(
        period = StatsPeriod.MONTH,
        labels = (7..31).map { "$it" } + (1..5).map { "$it" },
        minutes = monthMinutes,
        apps = breakdown(monthMinutes.sum()),
        fullLabels = (7..31).map { "$it Oct" } + (1..5).map { "$it Nov" }
    )

    // Last 12 months, total minutes per month. November is only 5 days in.
    private val yearMinutes = listOf(
        6120, 5480, 6390, 6010, 5760, 5230, 5940, 6480, 6150, 5890, 6215, 900
    )
    private val year = UsageSeries(
        period = StatsPeriod.YEAR,
        labels = listOf("D", "J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N"),
        minutes = yearMinutes,
        apps = breakdown(yearMinutes.sum()),
        fullLabels = listOf("Dec", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov")
    )

    fun series(period: StatsPeriod): UsageSeries = when (period) {
        StatsPeriod.DAY -> day
        StatsPeriod.WEEK -> week
        StatsPeriod.MONTH -> month
        StatsPeriod.YEAR -> year
    }

    // ---------- Home ----------

    // Fake "current time". Used to pick the mascot's mood (SLEEPY near bedtime).
    val now: LocalTime = LocalTime.of(14, 32)

    val today = TodaySummary(
        totalMinutes = day.totalMinutes,
        reelsCount = 87,
        cheatsToday = 2,
        topApps = day.apps.take(3)
    )

    // ---------- Cheat log ----------

    val cheatLog = listOf(
        CheatEntry("Today", "12:47 AM", "Instagram", "Bedtime was 11. Instagram disagreed."),
        CheatEntry("Today", "9:12 AM", "YouTube", "\"Just one video\" before work. It was four."),
        CheatEntry("Yesterday", "1:40 AM", "Instagram", "3rd time this week you outsmarted yourself at 1:40 AM."),
        CheatEntry("Yesterday", "6:05 PM", "X", "20-minute limit. You saw it as a suggestion."),
        CheatEntry("Tue", "11:58 PM", "YouTube", "Two minutes to bedtime. You chose violence."),
        CheatEntry("Mon", "2:15 AM", "Instagram", "The reels will still be there tomorrow. So will your eye bags."),
        CheatEntry("Sun", "10:30 AM", "Instagram", "Unlocked it 6 times before breakfast. Breakfast lost."),
        CheatEntry("Sat", "12:20 AM", "WhatsApp", "Group chat at midnight. Nobody said anything worth it.")
    )

    // ---------- Profile ----------

    val streaks = listOf(
        Streak("Under daily limit", currentDays = 3, bestDays = 11),
        Streak("In bed on time", currentDays = 0, bestDays = 6),
        Streak("No reels before 10 AM", currentDays = 1, bestDays = 4)
    )

    val trackingSince = "12 Oct 2026"

    val allTimeTotals = listOf(
        TotalStat("Screen time", "81h"),
        TotalStat("Reels watched", "4,120"),
        TotalStat("Cheats", "37"),
        TotalStat("Phone pickups", "2,980")
    )

    // A reel is counted as about 20 seconds.
    val lifeCosts = listOf(
        "412 reels ≈ one movie you didn't watch",
        "4,120 reels ≈ 23 hours of strangers' lives",
        "81 hours ≈ 10 working days, given to glass",
        "37 cheats ≈ 37 promises to yourself, renegotiated"
    )

    // ---------- Settings ----------

    val settings = SettingsValues(
        wakeTime = LocalTime.of(7, 0),
        bedTime = LocalTime.of(23, 0),
        appRules = listOf(
            AppRule("Instagram", dailyLimitMinutes = 45, lockedAtBedtime = true),
            AppRule("YouTube", dailyLimitMinutes = 60, lockedAtBedtime = true),
            AppRule("X", dailyLimitMinutes = 20, lockedAtBedtime = false),
            AppRule("WhatsApp", dailyLimitMinutes = null, lockedAtBedtime = false)
        ),
        permissions = listOf(
            PermissionInfo("Notifications", "To roast you when you cheat", "Not granted"),
            PermissionInfo("Display over other apps", "To show the lock screen", "Not needed yet")
        ),
        themeOptions = listOf("Pure black", "Soft black", "High contrast"),
        selectedTheme = 0
    )
}
