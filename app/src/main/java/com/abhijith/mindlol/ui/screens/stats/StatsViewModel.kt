package com.abhijith.mindlol.ui.screens.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.abhijith.mindlol.MindLolApplication
import com.abhijith.mindlol.data.repository.UsageRepository
import com.abhijith.mindlol.domain.StatsPeriod
import com.abhijith.mindlol.domain.UsageSeries
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class StatsUiState(
    val period: StatsPeriod = StatsPeriod.WEEK,
    /** Null while the first numbers for [period] are loading. */
    val series: UsageSeries? = null
)

class StatsViewModel(private val repository: UsageRepository) : ViewModel() {

    private val period = MutableStateFlow(StatsPeriod.WEEK)

    // flatMapLatest: when the period changes, drop the old query and follow the new one.
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<StatsUiState> = period
        .flatMapLatest { p -> repository.series(p).map { StatsUiState(p, it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    val selectedPeriod: StateFlow<StatsPeriod> = period.asStateFlow()

    fun select(newPeriod: StatsPeriod) {
        period.value = newPeriod
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as MindLolApplication
                StatsViewModel(app.container.usageRepository)
            }
        }
    }
}
