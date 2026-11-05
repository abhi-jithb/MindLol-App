package com.abhijith.mindlol.ui.screens.cheatlog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.abhijith.mindlol.MindLolApplication
import com.abhijith.mindlol.data.repository.CheatRepository
import com.abhijith.mindlol.domain.CheatEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class CheatLogUiState(
    val loading: Boolean = true,
    /** Newest first. */
    val entries: List<CheatEntry> = emptyList(),
    /** Cheats that started this week (Monday to now). */
    val cheatsThisWeek: Int = 0
)

class CheatLogViewModel(cheats: CheatRepository) : ViewModel() {

    val uiState: StateFlow<CheatLogUiState> = cheats.cheatLog().map { entries ->
        val zone = ZoneId.systemDefault()
        val monday = LocalDate.now(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        CheatLogUiState(
            loading = false,
            entries = entries,
            cheatsThisWeek = entries.count {
                !Instant.ofEpochMilli(it.cheat.startMillis).atZone(zone).toLocalDate().isBefore(monday)
            }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CheatLogUiState())

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MindLolApplication
                CheatLogViewModel(app.container.cheatRepository)
            }
        }
    }
}
