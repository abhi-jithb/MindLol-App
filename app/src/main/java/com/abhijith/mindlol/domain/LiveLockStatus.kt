package com.abhijith.mindlol.domain

/** What the Settings "Live lock" row shows. */
enum class LiveLockState {
    /** Switched on in Accessibility and running. */
    ON,
    /** Switched off in Accessibility. Shown loudly: nothing is protected. */
    OFF,
    /** Switched on in Accessibility, but the service isn't running in MindLol right now. */
    NOT_RUNNING
}

/** Pure helpers for the Settings "Live lock" row and the "Add app" picker. */
object LiveLockStatus {

    /**
     * [enabledInSettings]: listed in Android's enabled accessibility services.
     * [running]: the service is connected in this process.
     * Running but not enabled happens for a moment while you switch it off: that's OFF.
     */
    fun state(enabledInSettings: Boolean, running: Boolean): LiveLockState = when {
        enabledInSettings && running -> LiveLockState.ON
        enabledInSettings -> LiveLockState.NOT_RUNNING
        else -> LiveLockState.OFF
    }

    fun status(state: LiveLockState): String = when (state) {
        LiveLockState.ON -> "On"
        LiveLockState.OFF -> "OFF"
        LiveLockState.NOT_RUNNING -> "Not running"
    }

    fun reason(state: LiveLockState): String = when (state) {
        LiveLockState.ON -> "Covers locked apps at bedtime and apps over their limit"
        LiveLockState.OFF -> "Nothing is locked while it's off. Tap to turn it on"
        LiveLockState.NOT_RUNNING -> "On in Accessibility, but not running. Switch it off and on again there"
    }

    /**
     * Is our service in Android's ENABLED_ACCESSIBILITY_SERVICES setting? The setting is a
     * ':'-separated list of "package/class" names; the class may be written short (".service.X").
     * Case is ignored, as Android does.
     *
     * @param ourComponent our own "package/full.class.Name".
     */
    fun isEnabled(enabledServicesSetting: String?, ourComponent: String): Boolean {
        if (enabledServicesSetting.isNullOrBlank()) return false
        val ours = expand(ourComponent) ?: return false
        return enabledServicesSetting.split(':').any { expand(it.trim())?.equals(ours, ignoreCase = true) == true }
    }

    /** "pkg/.service.X" → "pkg/pkg.service.X"; null if it isn't "package/class". */
    private fun expand(component: String): String? {
        val slash = component.indexOf('/')
        if (slash <= 0 || slash == component.length - 1) return null
        val pkg = component.substring(0, slash)
        val cls = component.substring(slash + 1)
        return "$pkg/" + if (cls.startsWith(".")) pkg + cls else cls
    }

    /**
     * The "Add app" picker: launcher apps that don't have a rule yet, minus the apps that can
     * never be locked (MindLol, system Settings, the dialer), so you can't add a rule that would
     * never apply.
     */
    fun addableApps(installed: List<InstalledApp>, alreadyShown: Collection<String>, neverLockable: Set<String>): List<InstalledApp> =
        installed.filter { it.packageName !in alreadyShown && it.packageName !in neverLockable }
}
