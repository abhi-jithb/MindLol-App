package com.abhijith.mindlol

import android.content.Context
import com.abhijith.mindlol.data.db.MindLolDatabase
import com.abhijith.mindlol.data.prefs.AppPrefs
import com.abhijith.mindlol.data.usage.HistoryImporter
import com.abhijith.mindlol.data.usage.UsageEventsReader
import com.abhijith.mindlol.data.usage.UsagePermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Builds and holds the app's long-lived objects, so each exists once.
 * Screens receive what they need from here (manual dependency passing, no Hilt).
 * Think of it as a hand-written Provider at the top of the tree.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val usagePermission = UsagePermission(appContext)

    // `by lazy`: built on first use, not at app start, so launch stays fast.
    val database: MindLolDatabase by lazy { MindLolDatabase.build(appContext) }

    /** Coroutines that should outlive any single screen (sync, import). SupervisorJob: one failure doesn't cancel the rest. */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val prefs = AppPrefs(appContext)
    val usageReader = UsageEventsReader(appContext)
    val historyImporter: HistoryImporter by lazy {
        HistoryImporter(appContext, database.usageDao(), prefs) { usageReader.excludedPackages }
    }
}
