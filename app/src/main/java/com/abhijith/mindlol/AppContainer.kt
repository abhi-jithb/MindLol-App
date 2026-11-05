package com.abhijith.mindlol

import android.content.Context
import com.abhijith.mindlol.data.usage.UsagePermission

/**
 * Builds and holds the app's long-lived objects, so each exists once.
 * Screens receive what they need from here (manual dependency passing, no Hilt).
 * Think of it as a hand-written Provider at the top of the tree.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val usagePermission = UsagePermission(appContext)
}
