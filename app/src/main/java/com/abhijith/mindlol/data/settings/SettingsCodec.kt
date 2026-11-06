package com.abhijith.mindlol.data.settings

import com.abhijith.mindlol.domain.AppTheme
import com.abhijith.mindlol.domain.RoastIntensity

/**
 * Turns settings that DataStore can't store directly into types it can, and back.
 * Pure Kotlin so it can be unit tested.
 *
 * Per-app limits are a Map<package, minutes>. DataStore Preferences has no map type, so each
 * entry becomes one string "package=minutes" inside a string set, e.g.
 * {"com.instagram.android=45", "com.google.android.youtube=60"}.
 * '=' is safe as the separator because package names only use letters, digits, '_' and '.'.
 */
object SettingsCodec {

    fun encodeLimits(limits: Map<String, Int>): Set<String> =
        limits.filterValues { it > 0 }.map { (pkg, minutes) -> "$pkg=$minutes" }.toSet()

    /** Broken or non-positive entries are skipped instead of crashing the whole settings read. */
    fun decodeLimits(stored: Set<String>): Map<String, Int> = stored.mapNotNull { entry ->
        val pkg = entry.substringBefore('=', missingDelimiterValue = "")
        val minutes = entry.substringAfter('=', missingDelimiterValue = "").toIntOrNull()
        if (pkg.isNotEmpty() && minutes != null && minutes > 0) pkg to minutes else null
    }.toMap()

    /** Unknown names (e.g. from a future version) fall back to the default. */
    fun decodeIntensity(stored: String?): RoastIntensity =
        RoastIntensity.entries.firstOrNull { it.name == stored } ?: RoastIntensity.SAVAGE

    /** Unknown names fall back to the default look. */
    fun decodeTheme(stored: String?): AppTheme =
        AppTheme.entries.firstOrNull { it.name == stored } ?: AppTheme.PURE_BLACK

    /** A set of names as one text column: "a,b,c". Package names never contain commas. */
    fun joinList(items: Collection<String>): String = items.sorted().joinToString(",")

    fun splitList(text: String): List<String> = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}
