package com.abhijith.mindlol.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.abhijith.mindlol.MindLolApplication
import com.abhijith.mindlol.data.repository.CheatRepository
import com.abhijith.mindlol.data.repository.FakeData
import com.abhijith.mindlol.data.repository.UsageRepository
import com.abhijith.mindlol.data.settings.SettingsRepository
import com.abhijith.mindlol.domain.Settings
import com.abhijith.mindlol.domain.AppUsage
import com.abhijith.mindlol.domain.CheatEntry
import com.abhijith.mindlol.domain.StatsPeriod
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Everything Home draws. Usage and cheats are real; reels stay fake until Phase 4. */
data class HomeUiState(
    val loading: Boolean = true,
    val totalMinutes: Int = 0,
    val unlocks: Int? = null,
    val topApps: List<AppUsage> = emptyList(),
    val weekAverageMinutes: Int = 0,
    val hasData: Boolean = false,
    /** From Settings; the mascot gets sleepy near bedtime. */
    val bedtimeMinutes: Int = Settings.DEFAULT_BEDTIME,
    val reelsCount: Int = FakeData.reelsToday,
    /** Cheats that started today (local time). */
    val cheatsToday: Int = 0,
    /** The latest cheat, only if it started in the last hour: Home then shows the mascot reacting to it. */
    val recentCheat: CheatEntry? = null
)

/**
 * Holds Home's state and survives screen redraws and tab switches.
 * Like a React hook/store that outlives the component, or a Flutter ChangeNotifier.
 */
class HomeViewModel(
    repository: UsageRepository,
    settingsRepository: SettingsRepository,
    cheatRepository: CheatRepository
) : ViewModel() {

    // stateIn turns a Flow into a StateFlow that always has a current value.
    // WhileSubscribed(5_000): keep listening 5 s after the screen goes away (survives rotation).
    val uiState: StateFlow<HomeUiState> =
        combine(
            repository.todayUsage(),
            repository.series(StatsPeriod.WEEK),
            settingsRepository.settings,
            cheatRepository.cheatLog()
        ) { today, week, settings, cheats ->
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val todayDate = LocalDate.now(zone)
            HomeUiState(
                loading = false,
                totalMinutes = today.totalMinutes,
                unlocks = today.unlocks,
                topApps = today.topApps,
                weekAverageMinutes = week.averageMinutes,
                hasData = today.hasData,
                bedtimeMinutes = settings.bedtimeMinutes,
                cheatsToday = cheats.count { Instant.ofEpochMilli(it.cheat.startMillis).atZone(zone).toLocalDate() == todayDate },
                recentCheat = cheats.firstOrNull()?.takeIf { now - it.cheat.startMillis < RECENT_CHEAT_MS }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        private const val RECENT_CHEAT_MS = 60 * 60_000L

        /** Tells Compose how to build this ViewModel: take the repository from the AppContainer. */
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MindLolApplication
                HomeViewModel(app.container.usageRepository, app.container.settingsRepository, app.container.cheatRepository)
            }
        }
    }
}
