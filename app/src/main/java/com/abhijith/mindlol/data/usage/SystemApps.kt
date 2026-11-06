package com.abhijith.mindlol.data.usage

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

/** System apps the live lock treats specially. */
class SystemApps(private val context: Context) {

    /**
     * Apps that can never be locked: MindLol itself, system Settings (where the lock is turned
     * off) and the Phone/dialer. Keeps an escape route open at all times.
     */
    fun neverLockable(): Set<String> = buildSet {
        add(context.packageName)
        add("com.android.settings")
        context.packageManager.resolveActivity(Intent(Settings.ACTION_SETTINGS), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.let(::add)
        context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage?.let(::add)
    }

    /**
     * Windows that come to the front without being "the app you're using": the keyboard and the
     * status bar / notification shade. The lock ignores them, so typing doesn't look like an app switch.
     */
    fun notApps(): Set<String> = buildSet {
        add("com.android.systemui")
        context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList
            ?.forEach { add(it.packageName) }
    }
}
