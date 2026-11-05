package com.abhijith.mindlol.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.abhijith.mindlol.MindLolApplication
import com.abhijith.mindlol.data.repository.CheatRepository
import com.abhijith.mindlol.domain.CheatStats
import com.abhijith.mindlol.domain.Streaks
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class ProfileUiState(
    val loading: Boolean = true,
    val stats: CheatStats = CheatStats()
)

/** Streaks and cheat counts, recomputed whenever a refresh saves new snapshots or cheats. */
class ProfileViewModel(cheats: CheatRepository) : ViewModel() {

    val uiState: StateFlow<ProfileUiState> =
        combine(cheats.periods(), cheats.cheats()) { periods, all ->
            ProfileUiState(loading = false, stats = Streaks.compute(periods, all, LocalDate.now()))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MindLolApplication
                ProfileViewModel(app.container.cheatRepository)
            }
        }
    }
}
