package com.abhijith.mindlol.data.usage

import android.content.Context
import android.content.pm.PackageManager

/**
 * Turns "com.instagram.android" into "Instagram". Results are cached.
 * Only apps visible through the manifest's <queries> resolve; others fall back to the package name.
 */
class AppNames(private val context: Context) {
    private val cache = mutableMapOf<String, String>()

    fun labelFor(packageName: String): String = cache.getOrPut(packageName) {
        try {
            val pm = context.packageManager
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }
}
