package com.abhijith.mindlol

import android.app.Application

/** Created once per process, before any screen. Owns the [AppContainer]. */
class MindLolApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
