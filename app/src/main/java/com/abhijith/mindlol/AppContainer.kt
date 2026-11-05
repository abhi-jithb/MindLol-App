package com.abhijith.mindlol

import android.content.Context
import android.util.Log
import com.abhijith.mindlol.data.db.MindLolDatabase
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.abhijith.mindlol.data.prefs.AppPrefs
import com.abhijith.mindlol.data.settings.SettingsRepository
import com.abhijith.mindlol.data.repository.CheatRepository
import com.abhijith.mindlol.data.repository.LiveLockRepository
import com.abhijith.mindlol.data.repository.UsageRepository
import com.abhijith.mindlol.data.usage.AppNames
import com.abhijith.mindlol.data.usage.HistoryImporter
import com.abhijith.mindlol.data.usage.SystemApps
import com.abhijith.mindlol.data.usage.UsageEventsReader
import com.abhijith.mindlol.data.usage.UsagePermission
import kotlinx.coroutines.CoroutineExceptionHandler
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

    /**
     * Coroutines that should outlive any single screen (sync, import). SupervisorJob: one failure doesn't cancel the rest.
     * The exception handler is a last safety net: anything a launched job forgets to catch is logged, not crashed on.
     */
    val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            Log.e("AppContainer", "Uncaught error in applicationScope", e)
        }
    )

    val prefs = AppPrefs(appContext)

    /**
     * The one DataStore for settings. DataStore must never have two instances for the same file
     * (it throws), and AppContainer exists once per process, so creating it here is safe.
     */
    private val settingsDataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create { appContext.preferencesDataStoreFile("settings") }
    }
    val settingsRepository: SettingsRepository by lazy { SettingsRepository(settingsDataStore) }

    val appNames = AppNames(appContext)
    val usageReader = UsageEventsReader(appContext)
    val historyImporter: HistoryImporter by lazy {
        HistoryImporter(appContext, database.usageDao(), prefs) { usageReader.excludedPackages }
    }

    val cheatRepository: CheatRepository by lazy {
        CheatRepository(database.rulesDao(), settingsRepository, usageReader, appNames, prefs)
    }

    val liveLockRepository: LiveLockRepository by lazy {
        LiveLockRepository(database.rulesDao(), database.lockDao(), usageReader, SystemApps(appContext), appNames, settingsRepository)
    }

    val usageRepository: UsageRepository by lazy {
        UsageRepository(database.usageDao(), usageReader, historyImporter, appNames, usagePermission, cheatRepository)
    }
}
