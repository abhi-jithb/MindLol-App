package com.abhijith.mindlol.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abhijith.mindlol.domain.SleepTime
import com.abhijith.mindlol.domain.AppUsage
import com.abhijith.mindlol.ui.components.AppUsageRow
import com.abhijith.mindlol.ui.components.Mascot
import com.abhijith.mindlol.ui.components.MascotMood
import com.abhijith.mindlol.ui.components.SmallStat
import com.abhijith.mindlol.ui.components.formatCount
import com.abhijith.mindlol.ui.components.formatMinutes
import com.abhijith.mindlol.ui.components.pickMood
import com.abhijith.mindlol.ui.components.revealOnEnter
import com.abhijith.mindlol.ui.theme.MindLolTheme
import com.abhijith.mindlol.ui.theme.Motion
import java.time.LocalTime

/** Stateful wrapper: gets the ViewModel and follows its state. */
@Composable
fun HomeScreen(viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    // Collects only while the screen is at least STARTED, so nothing runs in the background.
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(state)
}

/** Stateless: draws whatever state it is given. Easy to preview. */
@Composable
fun HomeContent(state: HomeUiState) {
    val mood = pickMood(
        totalMinutes = state.totalMinutes,
        averageMinutes = state.weekAverageMinutes,
        cheatsToday = state.cheatsToday,
        now = LocalTime.now(),
        bedTime = SleepTime.toLocalTime(state.bedtimeMinutes)
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.loading) return@Column // first frame only; Room answers almost at once
        if (!state.hasData) {
            // Usage access is on, but the first sync hasn't written today's row yet.
            FirstSyncPlaceholder()
            return@Column
        }

        Spacer(Modifier.height(32.dp))
        Mascot(mood = mood, size = 120.dp, modifier = Modifier.revealOnEnter(0))
        Spacer(Modifier.height(24.dp))

        CountUpMinutes(state.totalMinutes)
        Text(
            text = "SCREEN TIME TODAY",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(40.dp))
        Row(modifier = Modifier.fillMaxWidth().revealOnEnter(1)) {
            SmallStat(value = formatCount(state.reelsCount), label = "REELS", modifier = Modifier.weight(1f))
            SmallStat(
                value = state.unlocks?.let { formatCount(it) } ?: "–",
                label = "UNLOCKS",
                modifier = Modifier.weight(1f)
            )
            SmallStat(value = formatCount(state.cheatsToday), label = "CHEATS", modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(48.dp))
        Text(
            text = "TOP APPS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().revealOnEnter(2)
        )
        Spacer(Modifier.height(16.dp))
        if (state.topApps.isEmpty()) {
            Text(
                text = "Nothing yet today. Suspiciously clean.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().revealOnEnter(3)
            )
        }
        val most = state.topApps.maxOfOrNull { it.minutes }?.coerceAtLeast(1) ?: 1
        state.topApps.forEachIndexed { i, app ->
            AppUsageRow(app = app, fraction = app.minutes.toFloat() / most, modifier = Modifier.revealOnEnter(3 + i))
            Spacer(Modifier.height(20.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun FirstSyncPlaceholder() {
    Spacer(Modifier.height(120.dp))
    Mascot(mood = MascotMood.SLEEPY, size = 120.dp, modifier = Modifier.revealOnEnter(0))
    Spacer(Modifier.height(24.dp))
    Text(
        text = "Reading your phone's diary…",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.revealOnEnter(1)
    )
    Spacer(Modifier.height(8.dp))
    Text(
        text = "Your first numbers appear in a moment.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.revealOnEnter(2)
    )
}

/**
 * The one big number. Counts up from 0 the first time Home is shown.
 * rememberSaveable keeps "already played" while you visit other tabs, so it doesn't replay.
 */
@Composable
private fun CountUpMinutes(totalMinutes: Int) {
    var played by rememberSaveable { mutableStateOf(false) }
    val value = remember { Animatable(if (played) totalMinutes.toFloat() else 0f) }
    LaunchedEffect(totalMinutes) {
        value.animateTo(totalMinutes.toFloat(), Motion.standard(Motion.COUNT_UP))
        played = true
    }
    Text(
        text = formatMinutes(value.value.toInt()),
        // "tnum" = tabular numbers: every digit is the same width, so the text doesn't wobble while counting.
        style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onBackground
    )
}

@Preview(heightDp = 800)
@Composable
private fun HomeContentPreview() {
    MindLolTheme {
        HomeContent(
            HomeUiState(
                loading = false,
                totalMinutes = 134,
                unlocks = 41,
                topApps = listOf(AppUsage("Instagram", 52), AppUsage("YouTube", 37), AppUsage("WhatsApp", 21)),
                weekAverageMinutes = 180,
                hasData = true
            )
        )
    }
}

@Preview(heightDp = 800)
@Composable
private fun HomeFirstSyncPreview() {
    MindLolTheme { HomeContent(HomeUiState(loading = false, hasData = false)) }
}
