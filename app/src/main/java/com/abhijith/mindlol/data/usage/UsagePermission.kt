package com.abhijith.mindlol.data.usage

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the user has turned on "Usage access" for MindLol in system Settings.
 *
 * There is no runtime popup for this permission. Android tracks it as an "app op",
 * so we ask AppOpsManager. Call [refresh] whenever the app comes back to the
 * foreground, because the user may have just flipped the switch in Settings.
 */
class UsagePermission(private val context: Context) {

    private val _granted = MutableStateFlow(check())

    /** Observable current value, like a store/stream screens can subscribe to. */
    val granted: StateFlow<Boolean> = _granted.asStateFlow()

    /** Re-reads the system state and pushes it to [granted] if it changed. */
    fun refresh() {
        _granted.value = check()
    }

    fun check(): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return when (mode) {
            AppOpsManager.MODE_ALLOWED -> true
            // MODE_DEFAULT means "fall back to the normal permission check".
            AppOpsManager.MODE_DEFAULT ->
                context.checkCallingOrSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS) ==
                    PackageManager.PERMISSION_GRANTED
            else -> false
        }
    }
}
