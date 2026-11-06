package com.abhijith.mindlol.data.usage

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.abhijith.mindlol.domain.LiveLockStatus
import com.abhijith.mindlol.service.LockAccessibilityService

/** Reads whether the live lock is switched on in Android's Accessibility settings (read-only). */
object AccessibilityState {

    fun isLiveLockEnabled(context: Context): Boolean = try {
        val setting = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val ours = ComponentName(context, LockAccessibilityService::class.java).flattenToString()
        LiveLockStatus.isEnabled(setting, ours)
    } catch (e: Exception) {
        Log.w("Settings", "Couldn't read the accessibility setting", e)
        false
    }
}
