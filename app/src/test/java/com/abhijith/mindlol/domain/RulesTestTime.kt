package com.abhijith.mindlol.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Shared clock helpers for the rules tests. Thursday 5 Nov 2026, India time. */
object RulesTestTime {
    val zone: ZoneId = ZoneId.of("Asia/Kolkata")
    val thu: LocalDate = LocalDate.of(2026, 11, 5)
    val fri: LocalDate = thu.plusDays(1)
    val wed: LocalDate = thu.minusDays(1)

    const val INSTA = "com.instagram.android"
    const val YT = "com.google.android.youtube"

    fun at(date: LocalDate, h: Int, m: Int = 0, s: Int = 0): Long =
        LocalDateTime.of(date, LocalTime.of(h, m, s)).atZone(zone).toInstant().toEpochMilli()

    fun session(pkg: String, start: Long, end: Long) = AppSession(pkg, start, end)

    fun minutes(n: Int) = n * 60_000L
}
