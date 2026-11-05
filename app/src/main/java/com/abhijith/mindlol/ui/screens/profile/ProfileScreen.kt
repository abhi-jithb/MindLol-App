package com.abhijith.mindlol.ui.screens.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abhijith.mindlol.data.repository.FakeData
import com.abhijith.mindlol.domain.CheatStats
import com.abhijith.mindlol.domain.Streak
import com.abhijith.mindlol.ui.components.Mascot
import com.abhijith.mindlol.ui.components.MascotMood
import com.abhijith.mindlol.ui.components.MascotReaction
import com.abhijith.mindlol.ui.components.SmallStat
import com.abhijith.mindlol.ui.components.revealOnEnter
import com.abhijith.mindlol.ui.theme.MindLolTheme
import com.abhijith.mindlol.ui.theme.Motion

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    ProfileContent(state)
}

/**
 * Stateless. Clean nights, the limit streak and the cheat counts are real (from Room);
 * the reels streak, screen-time totals and most life-cost lines stay fake until Phase 4.
 */
@Composable
fun ProfileContent(state: ProfileUiState) {
    val stats = state.stats
    val streaks = listOf(
        Streak("Clean nights", stats.cleanNightsCurrent, stats.cleanNightsBest),
        Streak("Under daily limit", stats.underLimitCurrent, stats.underLimitBest)
    ) + FakeData.streaks
    // A bad week earns the head shake; a clean run earns smugness.
    val reaction = if (stats.cheatsThisWeek >= 3) MascotReaction.HEAD_SHAKE else MascotReaction.NONE
    val mood = if (stats.cleanNightsCurrent > 0) MascotMood.SMUG else MascotMood.NEUTRAL

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (state.loading) return@Column
        Spacer(Modifier.height(32.dp))
        Mascot(mood = mood, reaction = reaction, size = 88.dp, modifier = Modifier.revealOnEnter(0))
        Spacer(Modifier.height(16.dp))
        Text(
            text = "${stats.cleanNightsCurrent}",
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "CLEAN NIGHTS IN A ROW",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        SectionLabel("STREAKS", Modifier.revealOnEnter(1))
        streaks.forEachIndexed { i, streak ->
            StreakRow(streak, Modifier.revealOnEnter(2 + i))
        }

        SectionLabel("CHEATS", Modifier.revealOnEnter(4))
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp).revealOnEnter(5)) {
            SmallStat(value = "${stats.cheatsThisWeek}", label = "THIS WEEK", modifier = Modifier.weight(1f))
            SmallStat(value = "${stats.cheatsThisMonth}", label = "THIS MONTH", modifier = Modifier.weight(1f))
            SmallStat(value = "${stats.cheatsTotal}", label = "ALL TIME", modifier = Modifier.weight(1f))
        }

        SectionLabel("SINCE ${FakeData.trackingSince.uppercase()}", Modifier.revealOnEnter(5))
        // Rows of two: a simple grid without a grid component.
        FakeData.allTimeTotals.chunked(2).forEachIndexed { i, pair ->
            Row(Modifier.fillMaxWidth().padding(vertical = 12.dp).revealOnEnter(6 + i)) {
                pair.forEach { stat ->
                    SmallStat(value = stat.value, label = stat.label.uppercase(), modifier = Modifier.weight(1f))
                }
            }
        }

        SectionLabel("WHAT IT COST YOU", Modifier.revealOnEnter(8))
        val costs = FakeData.lifeCosts + "${stats.cheatsTotal} cheats ≈ ${stats.cheatsTotal} promises to yourself, renegotiated"
        costs.forEachIndexed { i, line ->
            Column(Modifier.fillMaxWidth().revealOnEnter(9 + i)) {
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(vertical = 14.dp)
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.fillMaxWidth().padding(top = 40.dp, bottom = 8.dp)
    )
}

/** Streak name and numbers, with one dot per day of your best run. Filled dots are the current run. */
@Composable
private fun StreakRow(streak: Streak, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(streak.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(
                "${streak.currentDays}d · best ${streak.bestDays}d",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        StreakDots(current = streak.currentDays, best = streak.bestDays)
    }
}

@Composable
private fun StreakDots(current: Int, best: Int) {
    // Dots fill one by one, left to right.
    val filled = remember { Animatable(0f) }
    LaunchedEffect(current) {
        filled.animateTo(current.toFloat(), Motion.standard(Motion.EXTRA_LONG, delayMillis = Motion.MEDIUM))
    }
    val on = MaterialTheme.colorScheme.onBackground
    val off = MaterialTheme.colorScheme.outline
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        // Capped so a long best streak still fits on one line.
        repeat(best.coerceAtMost(MAX_DOTS)) { i ->
            // How far this dot is into being filled, 0..1.
            val fill = (filled.value - i).coerceIn(0f, 1f)
            Canvas(Modifier.size(8.dp)) {
                val r = size.minDimension / 2
                drawCircle(off, r - 0.5.dp.toPx(), style = Stroke(1.dp.toPx()))
                if (fill > 0f) drawCircle(on, r * fill)
            }
        }
    }
}

private const val MAX_DOTS = 21

@Preview(heightDp = 1200)
@Composable
private fun ProfileContentPreview() {
    MindLolTheme {
        ProfileContent(ProfileUiState(false, CheatStats(3, 6, 1, 4, 2, 7, 12)))
    }
}
