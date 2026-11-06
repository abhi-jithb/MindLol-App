package com.abhijith.mindlol.data.db

import com.abhijith.mindlol.domain.Cheat
import com.abhijith.mindlol.domain.CheatType
import com.abhijith.mindlol.domain.PeriodKind
import com.abhijith.mindlol.domain.RulesSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class RulesEntitiesTest {

    private val thu = LocalDate.of(2026, 11, 5)

    @Test
    fun nightSnapshot_roundTrip() {
        val night = RulesSnapshot(PeriodKind.NIGHT, thu, 1_000, 2_000, overrideApplied = true,
            lockedApps = setOf("com.instagram.android", "com.google.android.youtube"), finalized = true)
        assertEquals(night, night.toEntity().toDomain())
    }

    @Test
    fun daySnapshot_roundTrip_andLimitsStoredAsText() {
        val day = RulesSnapshot(PeriodKind.DAY, thu, 0, 86_400_000, limits = mapOf("com.x" to 30, "com.y" to 45))
        val entity = day.toEntity()
        assertEquals("com.x=30,com.y=45", entity.limits)
        assertEquals("", entity.lockedApps)
        assertEquals(day, entity.toDomain())
    }

    @Test
    fun cheat_roundTrip_keepsFirstSeenStart() {
        val cheat = Cheat("com.x", CheatType.BEDTIME, thu, startMillis = 500, endMillis = 900, minutes = 7, firstSeenStartMillis = 600)
        assertEquals(cheat, cheat.toEntity().toDomain())
    }
}
