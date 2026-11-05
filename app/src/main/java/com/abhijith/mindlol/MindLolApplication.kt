package com.abhijith.mindlol

import android.app.Application
import com.abhijith.mindlol.worker.UsageSyncWorker

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
    }
}
