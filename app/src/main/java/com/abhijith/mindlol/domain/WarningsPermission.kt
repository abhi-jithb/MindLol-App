package com.abhijith.mindlol.domain

/**
 * The Settings "Warnings" row: can MindLol show warning notifications, and what does a tap do?
 * Pure, so it's JVM-tested. Android facts are passed in by the screen.
 */
enum class WarningsAccess {
    /** Notifications can show. */
    ALLOWED,
    /** Android 13+: not granted yet. A tap asks with the system dialog. */
    ASK,
    /** Turned off in system settings (the app, or its "Warnings" channel). A tap opens those settings. */
    OFF_IN_SETTINGS
}

object WarningsPermission {

    /** Android 13 (API 33) is where POST_NOTIFICATIONS became a runtime permission. */
    const val RUNTIME_PERMISSION_SDK = 33

    fun access(sdkInt: Int, permissionGranted: Boolean, notificationsEnabled: Boolean, channelEnabled: Boolean): WarningsAccess = when {
        sdkInt >= RUNTIME_PERMISSION_SDK && !permissionGranted -> WarningsAccess.ASK
        !notificationsEnabled || !channelEnabled -> WarningsAccess.OFF_IN_SETTINGS
        else -> WarningsAccess.ALLOWED
    }

    /**
     * After the dialog says no: if Android won't show the dialog again ([canAskAgain] false, i.e.
     * shouldShowRequestPermissionRationale is false after a denial), the only way is system settings.
     */
    fun openSettingsAfterDenial(canAskAgain: Boolean): Boolean = !canAskAgain

    fun status(access: WarningsAccess): String = when (access) {
        WarningsAccess.ALLOWED -> "Allowed"
        WarningsAccess.ASK -> "Tap to allow"
        WarningsAccess.OFF_IN_SETTINGS -> "Off in settings"
    }
}
