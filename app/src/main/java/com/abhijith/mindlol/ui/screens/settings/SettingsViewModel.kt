package com.abhijith.mindlol.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.abhijith.mindlol.MindLolApplication
import com.abhijith.mindlol.data.repository.CheatRepository
import com.abhijith.mindlol.data.settings.SettingsRepository
import com.abhijith.mindlol.data.usage.AppNames
import com.abhijith.mindlol.domain.AppRule
import com.abhijith.mindlol.domain.AppTheme
import com.abhijith.mindlol.domain.InstalledApp
import com.abhijith.mindlol.domain.RoastIntensity
import com.abhijith.mindlol.domain.Settings
import com.abhijith.mindlol.domain.SleepTime
import com.abhijith.mindlol.domain.TonightOverride
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalTime
import java.time.ZoneId

data class SettingsUiState(
    val settings: Settings = Settings(),
    /** The override only while it still applies; null once its wake time has passed. */
    val activeOverride: TonightOverride? = null,
    /** One row per app that has a lock or a limit, plus apps just added this visit. */
    val rules: List<AppRule> = emptyList(),
    /** Launcher apps not shown in [rules] yet, for the "Add app" picker. */
    val addableApps: List<InstalledApp> = emptyList()
)

/**
 * Reads settings from [SettingsRepository] and writes every change straight back.
 * There is no "Save" button: DataStore saves each change, and the Flow redraws the screen.
 */
class SettingsViewModel(
    private val repository: SettingsRepository,
    private val appNames: AppNames,
    private val cheats: CheatRepository
) : ViewModel() {

    /** A short note to show once (a snackbar), e.g. "Applies from tomorrow". Null when there is none. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    fun messageShown() {
        _message.value = null
    }

    private val installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())

    // Apps picked with "Add app" this visit. Kept so a row doesn't vanish the moment you
    // switch its lock off before setting a limit.
    private val addedThisVisit = MutableStateFlow<List<String>>(emptyList())

    init {
        viewModelScope.launch {
            installedApps.value = withContext(Dispatchers.IO) { appNames.launcherApps() }
        }
    }

    val uiState: StateFlow<SettingsUiState> =
        combine(repository.settings, installedApps, addedThisVisit) { settings, apps, added ->
            val labels = apps.associate { it.packageName to it.label }
            val shown = (settings.lockedApps + settings.appLimits.keys + added).distinct()
            SettingsUiState(
                settings = settings,
                activeOverride = settings.activeOverride(System.currentTimeMillis()),
                rules = shown
                    .map { pkg ->
                        AppRule(
                            packageName = pkg,
                            appName = labels[pkg] ?: pkg,
                            dailyLimitMinutes = settings.appLimits[pkg],
                            lockedAtBedtime = pkg in settings.lockedApps
                        )
                    }
                    .sortedBy { it.appName.lowercase() },
                addableApps = apps.filter { it.packageName !in shown }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    // Night rules (bedtime, wake, locks) always save. If tonight has started they apply from tomorrow night.
    fun setBedtime(time: LocalTime) = saveNightRule { repository.setBedtime(SleepTime.fromLocalTime(time)) }

    fun setWake(time: LocalTime) = saveNightRule { repository.setWake(SleepTime.fromLocalTime(time)) }

    /** Turning it on starts from the normal window; edit the times after. */
    fun setTonightEnabled(enabled: Boolean) {
        val s = uiState.value.settings
        saveOverride(if (enabled) newOverride(s.bedtimeMinutes, s.wakeMinutes) else null)
    }

    fun setTonightBedtime(time: LocalTime) {
        val current = uiState.value.activeOverride ?: return
        saveOverride(newOverride(SleepTime.fromLocalTime(time), current.wakeMinutes))
    }

    fun setTonightWake(time: LocalTime) {
        val current = uiState.value.activeOverride ?: return
        saveOverride(newOverride(current.bedtimeMinutes, SleepTime.fromLocalTime(time)))
    }

    fun addApp(packageName: String) = addedThisVisit.update { it + packageName }

    fun setLocked(packageName: String, locked: Boolean) = saveNightRule { repository.setLocked(packageName, locked) }

    /** Today's limits were frozen at midnight, so a change always applies from tomorrow. */
    fun setLimit(packageName: String, minutes: Int) = save {
        repository.setLimit(packageName, minutes)
        if (cheats.isTodayLocked()) _message.value = APPLIES_TOMORROW
    }

    fun setRoastIntensity(intensity: RoastIntensity) = save { repository.setRoastIntensity(intensity) }

    /** Takes effect at once: MainActivity follows the same settings Flow. */
    fun setTheme(theme: AppTheme) = save { repository.setTheme(theme) }

    // The expiry is worked out from "now", so changing the wake time moves it too.
    private fun newOverride(bedtimeMinutes: Int, wakeMinutes: Int) = TonightOverride(
        bedtimeMinutes = bedtimeMinutes,
        wakeMinutes = wakeMinutes,
        expiresAtMillis = SleepTime.overrideExpiryMillis(System.currentTimeMillis(), wakeMinutes, ZoneId.systemDefault())
    )

    /** The one setting that is refused, not postponed: tonight's override can't change once tonight started. */
    private fun saveOverride(override: TonightOverride?) = save {
        if (cheats.isTonightLocked()) {
            _message.value = TONIGHT_LOCKED
        } else {
            repository.setTonightOverride(override)
        }
    }

    private fun saveNightRule(block: suspend () -> Unit) = save {
        block()
        if (cheats.isTonightLocked()) _message.value = APPLIES_TOMORROW_NIGHT
    }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        const val TONIGHT_LOCKED = "Tonight's rules are locked in. Nice try."
        const val APPLIES_TOMORROW_NIGHT = "Applies from tomorrow night"
        const val APPLIES_TOMORROW = "Applies from tomorrow"

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MindLolApplication
                SettingsViewModel(app.container.settingsRepository, app.container.appNames, app.container.cheatRepository)
            }
        }
    }
}
