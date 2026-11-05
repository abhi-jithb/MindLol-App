package com.abhijith.mindlol

import android.app.Application
import com.abhijith.mindlol.worker.UsageSyncWorker
import kotlinx.coroutines.launch

/** Created once per process, before any screen. Owns the [AppContainer]. */
class MindLolApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        UsageSyncWorker.schedule(this)
        // Rewrite the next night's and day's rules whenever settings change.
        container.cheatRepository.watchSettings(container.applicationScope)
        // One-time: remove cheats judged before any rules existed (does nothing after the first run).
        container.applicationScope.launch { container.cheatRepository.cleanUpPreRulesDataOnce() }
    }
}
