package com.abhijith.mindlol.data.settings

import com.abhijith.mindlol.domain.AppTheme
import com.abhijith.mindlol.domain.RoastIntensity
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsCodecTest {

    @Test
    fun limits_roundTrip() {
        val limits = mapOf("com.instagram.android" to 45, "com.google.android.youtube" to 60)
        assertEquals(limits, SettingsCodec.decodeLimits(SettingsCodec.encodeLimits(limits)))
    }

    @Test
    fun encode_writesPackageEqualsMinutes_andDropsZero() {
        val encoded = SettingsCodec.encodeLimits(mapOf("com.x" to 20, "com.off" to 0))
        assertEquals(setOf("com.x=20"), encoded)
    }

    @Test
    fun decode_skipsBrokenEntries() {
        val stored = setOf("com.ok=30", "noequals", "=15", "com.text=abc", "com.neg=-5", "com.zero=0", "com.empty=")
        assertEquals(mapOf("com.ok" to 30), SettingsCodec.decodeLimits(stored))
    }

    @Test
    fun decode_emptySet_isEmptyMap() {
        assertEquals(emptyMap<String, Int>(), SettingsCodec.decodeLimits(emptySet()))
    }

    @Test
    fun intensity_knownName_isRead() {
        assertEquals(RoastIntensity.PERSONAL, SettingsCodec.decodeIntensity("PERSONAL"))
        assertEquals(RoastIntensity.MILD, SettingsCodec.decodeIntensity("MILD"))
    }

    @Test
    fun intensity_missingOrUnknown_defaultsToSavage() {
        assertEquals(RoastIntensity.SAVAGE, SettingsCodec.decodeIntensity(null))
        assertEquals(RoastIntensity.SAVAGE, SettingsCodec.decodeIntensity("NUCLEAR"))
    }

    @Test
    fun theme_knownName_isRead_unknownOrMissing_isPureBlack() {
        assertEquals(AppTheme.SOFT_BLACK, SettingsCodec.decodeTheme("SOFT_BLACK"))
        assertEquals(AppTheme.HIGH_CONTRAST, SettingsCodec.decodeTheme("HIGH_CONTRAST"))
        assertEquals(AppTheme.PURE_BLACK, SettingsCodec.decodeTheme(null))
        assertEquals(AppTheme.PURE_BLACK, SettingsCodec.decodeTheme("NEON"))
    }
}
