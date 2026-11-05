package com.abhijith.mindlol.data.repository

import com.abhijith.mindlol.data.db.AppUsageEntity
import com.abhijith.mindlol.data.db.DailySummaryEntity
import com.abhijith.mindlol.data.db.UsageSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DayResolverTest {

    private val d1 = LocalDate.of(2026, 11, 2)
    private val d2 = d1.plusDays(1)
    private val d3 = d1.plusDays(2)

    private fun summary(date: LocalDate, source: UsageSource, ms: Long, end: LocalDate = date, unlocks: Int? = null) =
        DailySummaryEntity(date, source, ms, unlocks, end)

    @Test
    fun eventsBeatImportedDaily() {
        val days = DayResolver.resolve(
            d1, d1,
            listOf(summary(d1, UsageSource.IMPORTED_DAILY, 999), summary(d1, UsageSource.EVENTS, 100, unlocks = 5)),
            listOf(AppUsageEntity(d1, "yt", UsageSource.EVENTS, 100, d1), AppUsageEntity(d1, "yt", UsageSource.IMPORTED_DAILY, 999, d1))
        )
        val day = days.single()
        assertEquals(100L, day.totalMs)
        assertEquals(mapOf("yt" to 100L), day.perAppMs)
        assertEquals(5, day.unlocks)
        assertTrue(day.precise)
    }

    @Test
    fun weeklyBucket_isSpreadEvenly_andMarkedEstimate() {
        val days = DayResolver.resolve(
            d1, d3,
            listOf(summary(d1, UsageSource.IMPORTED_WEEKLY, 300, end = d3)),
            listOf(AppUsageEntity(d1, "yt", UsageSource.IMPORTED_WEEKLY, 300, d3))
        )
        assertEquals(listOf(100L, 100L, 100L), days.map { it.totalMs })
        assertEquals(100L, days[1].perAppMs["yt"])
        assertFalse(days[1].precise)
        assertNull(days[1].unlocks)
    }

    @Test
    fun dayWithNoSource_hasNoData() {
        val day = DayResolver.resolve(d2, d2, emptyList(), emptyList()).single()
        assertFalse(day.hasData)
        assertEquals(0L, day.totalMs)
    }
}
