package com.abhijith.mindlol.data.usage

import com.abhijith.mindlol.domain.AppSession
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SessionPairingTest {

    private val ist = ZoneId.of("Asia/Kolkata")
    private val day = LocalDate.of(2026, 11, 5)
    private val dayStart = ms(day, 0, 0)
    private val dayEnd = ms(day.plusDays(1), 0, 0)

    private fun ms(date: LocalDate, h: Int, m: Int): Long =
        LocalDateTime.of(date, java.time.LocalTime.of(h, m)).atZone(ist).toInstant().toEpochMilli()

    private fun min(n: Int) = n * 60_000L

    private fun ev(pkg: String, h: Int, m: Int, kind: EventKind, cls: String = "Main", date: LocalDate = day) =
        UsageEventRecord(pkg, cls, ms(date, h, m), kind)

    @Test
    fun emptyInput_givesEmptyDay() {
        val result = SessionPairing.dailyUsage(emptyList(), dayStart, dayEnd, ist)
        assertEquals(1, result.size)
        assertEquals(day, result[0].date)
        assertEquals(0L, result[0].totalMs)
        assertEquals(emptyMap<String, Long>(), result[0].perAppMs)
        assertEquals(0, result[0].unlocks)
    }

    @Test
    fun simpleSession_countsResumeToPause() {
        val events = listOf(
            ev("insta", 10, 0, EventKind.RESUMED),
            ev("insta", 10, 25, EventKind.PAUSED)
        )
        val result = SessionPairing.dailyUsage(events, dayStart, dayEnd, ist).single()
        assertEquals(min(25), result.perAppMs["insta"])
        assertEquals(min(25), result.totalMs)
    }

    @Test
    fun sessionCrossingMidnight_isSplitBetweenDays() {
        val next = day.plusDays(1)
        val events = listOf(
            ev("yt", 23, 50, EventKind.RESUMED),
            ev("yt", 0, 20, EventKind.PAUSED, date = next)
        )
        val result = SessionPairing.dailyUsage(events, dayStart, ms(next.plusDays(1), 0, 0), ist)
        assertEquals(2, result.size)
        assertEquals(min(10), result[0].perAppMs["yt"])
        assertEquals(min(20), result[1].perAppMs["yt"])
    }

    @Test
    fun appOpenBeforeRange_isCountedFromMidnight() {
        // Lead-in events from the previous evening are given, but only today is asked for.
        val events = listOf(
            ev("yt", 23, 30, EventKind.RESUMED, date = day.minusDays(1)),
            ev("yt", 0, 15, EventKind.PAUSED)
        )
        val result = SessionPairing.dailyUsage(events, dayStart, dayEnd, ist).single()
        assertEquals(min(15), result.perAppMs["yt"])
    }

    @Test
    fun orphanPause_withNoEarlierEvents_countsFromRangeStart() {
        val events = listOf(ev("yt", 0, 5, EventKind.PAUSED))
        val result = SessionPairing.dailyUsage(events, dayStart, dayEnd, ist).single()
        assertEquals(min(5), result.perAppMs["yt"])
    }

    @Test
    fun missingPause_stillOpen_countsUntilRangeEnd() {
        val now = ms(day, 14, 30)
        val events = listOf(ev("insta", 14, 0, EventKind.RESUMED))
        val result = SessionPairing.dailyUsage(events, dayStart, now, ist).single()
        assertEquals(min(30), result.perAppMs["insta"])
    }

    @Test
    fun missingPause_closedByScreenOff() {
        val events = listOf(
            ev("insta", 22, 0, EventKind.RESUMED),
            ev("android", 22, 40, EventKind.SCREEN_OFF)
        )
        val result = SessionPairing.dailyUsage(events, dayStart, dayEnd, ist).single()
        assertEquals(min(40), result.perAppMs["insta"])
    }

    @Test
    fun overlappingApps_eachCounted_totalCountsOverlapOnce() {
        // Split screen: YouTube 10:00-10:30, WhatsApp 10:20-10:40.
        val events = listOf(
            ev("yt", 10, 0, EventKind.RESUMED),
            ev("wa", 10, 20, EventKind.RESUMED),
            ev("yt", 10, 30, EventKind.PAUSED),
            ev("wa", 10, 40, EventKind.PAUSED)
        )
        val result = SessionPairing.dailyUsage(events, dayStart, dayEnd, ist).single()
        assertEquals(min(30), result.perAppMs["yt"])
        assertEquals(min(20), result.perAppMs["wa"])
        assertEquals(min(40), result.totalMs)
    }

    @Test
    fun twoActivitiesOfOneApp_areNotDoubleCounted() {
        // Feed resumes, then Reels resumes before Feed pauses (normal activity handover).
        val events = listOf(
            ev("insta", 9, 0, EventKind.RESUMED, cls = "Feed"),
            ev("insta", 9, 10, EventKind.RESUMED, cls = "Reels"),
            ev("insta", 9, 10, EventKind.PAUSED, cls = "Feed"),
            ev("insta", 9, 30, EventKind.PAUSED, cls = "Reels")
        )
        val result = SessionPairing.dailyUsage(events, dayStart, dayEnd, ist).single()
        assertEquals(min(30), result.perAppMs["insta"])
    }

    @Test
    fun excludedPackages_stayPerApp_butLeaveTotal() {
        val events = listOf(
            ev("launcher", 8, 0, EventKind.RESUMED),
            ev("launcher", 8, 10, EventKind.PAUSED),
            ev("insta", 8, 10, EventKind.RESUMED),
            ev("insta", 8, 20, EventKind.PAUSED)
        )
        val result = SessionPairing.dailyUsage(events, dayStart, dayEnd, ist, setOf("launcher")).single()
        assertEquals(min(10), result.perAppMs["launcher"])
        assertEquals(min(10), result.totalMs)
    }

    @Test
    fun unlocks_areCountedPerDay_insideRangeOnly() {
        val events = listOf(
            ev("android", 23, 0, EventKind.UNLOCK, date = day.minusDays(1)), // lead-in, ignored
            ev("android", 7, 0, EventKind.UNLOCK),
            ev("android", 12, 0, EventKind.UNLOCK)
        )
        val result = SessionPairing.dailyUsage(events, dayStart, dayEnd, ist).single()
        assertEquals(2, result.unlocks)
    }

    @Test
    fun dayWithNoUsage_inMiddleOfRange_isStillReturned() {
        val events = listOf(
            ev("yt", 10, 0, EventKind.RESUMED),
            ev("yt", 10, 5, EventKind.PAUSED),
            ev("yt", 10, 0, EventKind.RESUMED, date = day.plusDays(2)),
            ev("yt", 10, 5, EventKind.PAUSED, date = day.plusDays(2))
        )
        val result = SessionPairing.dailyUsage(events, dayStart, ms(day.plusDays(3), 0, 0), ist)
        assertEquals(listOf(day, day.plusDays(1), day.plusDays(2)), result.map { it.date })
        assertEquals(0L, result[1].totalMs)
    }

    @Test
    fun hourlyTotals_splitSessionsAtHourBoundaries() {
        val events = listOf(
            ev("yt", 9, 50, EventKind.RESUMED),
            ev("yt", 10, 20, EventKind.PAUSED)
        )
        val hours = SessionPairing.hourlyTotals(events, dayStart, dayEnd, ist)
        assertEquals(24, hours.size)
        assertEquals(min(10), hours[9])
        assertEquals(min(20), hours[10])
        assertEquals(min(30), hours.sum())
    }

    @Test
    fun hourlyTotals_countOverlapOnce_andSkipExcluded() {
        val events = listOf(
            ev("yt", 14, 0, EventKind.RESUMED),
            ev("wa", 14, 10, EventKind.RESUMED),
            ev("yt", 14, 20, EventKind.PAUSED),
            ev("wa", 14, 30, EventKind.PAUSED),
            ev("launcher", 15, 0, EventKind.RESUMED),
            ev("launcher", 15, 30, EventKind.PAUSED)
        )
        val hours = SessionPairing.hourlyTotals(events, dayStart, dayEnd, ist, setOf("launcher"))
        assertEquals(min(30), hours[14])
        assertEquals(0L, hours[15])
    }

    @Test
    fun sessions_returnsPairedIntervalsWithExactTimes() {
        val events = listOf(
            ev("insta", 0, 30, EventKind.RESUMED),
            ev("insta", 0, 50, EventKind.PAUSED),
            ev("yt", 1, 0, EventKind.RESUMED) // still open at the end of the range
        )
        val result = SessionPairing.sessions(events, dayStart, ms(day, 2, 0))
        assertEquals(
            listOf(
                AppSession("insta", ms(day, 0, 30), ms(day, 0, 50)),
                AppSession("yt", ms(day, 1, 0), ms(day, 2, 0))
            ),
            result
        )
    }
}
