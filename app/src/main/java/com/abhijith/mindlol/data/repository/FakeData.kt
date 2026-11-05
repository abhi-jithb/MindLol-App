package com.abhijith.mindlol.data.repository

import com.abhijith.mindlol.domain.AppRule
import com.abhijith.mindlol.domain.CheatEntry
import com.abhijith.mindlol.domain.PermissionInfo
import com.abhijith.mindlol.domain.SettingsValues
import com.abhijith.mindlol.domain.Streak
import com.abhijith.mindlol.domain.TotalStat
import java.time.LocalTime

/**
 * The fake values that are still fake: reels, cheats, roasts, streaks, life-cost lines and
 * settings. Usage numbers moved to UsageRepository in Phase 2; the rest becomes real in Phases 3–4.
 */
object FakeData {

    // ---------- Home extras (Phase 3–4 will make these real) ----------

    val reelsToday = 87
    val cheatsToday = 2

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
