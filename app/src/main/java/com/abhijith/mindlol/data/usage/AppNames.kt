package com.abhijith.mindlol.data.usage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.abhijith.mindlol.domain.InstalledApp
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns "com.instagram.android" into "Instagram". Results are cached.
 * Only apps visible through the manifest's <queries> resolve; others fall back to the package name.
 */
class AppNames(private val context: Context) {
    // Concurrent: Settings and the repository can both ask for names at the same time on IO threads.
    private val cache = ConcurrentHashMap<String, String>()

    fun labelFor(packageName: String): String = cache.getOrPut(packageName) {
        try {
            val pm = context.packageManager
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    /**
     * Every app with a home-screen icon, A to Z, except MindLol itself. These are the apps you can
     * lock or limit. Visible thanks to the LAUNCHER entry in the manifest's <queries>.
     * Slow-ish (asks the system for every app), so call it off the main thread.
     */
    fun launcherApps(): List<InstalledApp> {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcherIntent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
            .filter { it != context.packageName }
            .map { InstalledApp(it, labelFor(it)) }
            .sortedBy { it.label.lowercase() }
    }
}
