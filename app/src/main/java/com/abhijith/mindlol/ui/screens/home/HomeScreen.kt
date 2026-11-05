package com.abhijith.mindlol.ui.screens.home

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.abhijith.mindlol.data.repository.FakeData
import com.abhijith.mindlol.domain.AppUsage
import com.abhijith.mindlol.domain.StatsPeriod
import com.abhijith.mindlol.ui.components.Mascot
import com.abhijith.mindlol.ui.components.formatCount
import com.abhijith.mindlol.ui.components.formatMinutes
import com.abhijith.mindlol.ui.components.pickMood
import com.abhijith.mindlol.ui.components.revealOnEnter
import com.abhijith.mindlol.ui.theme.MindLolTheme
import com.abhijith.mindlol.ui.theme.Motion

@Composable
fun HomeScreen() {
    val today = FakeData.today
    val mood = pickMood(
        totalMinutes = today.totalMinutes,
        averageMinutes = FakeData.series(StatsPeriod.WEEK).averageMinutes,
        cheatsToday = today.cheatsToday,
        now = FakeData.now,
        bedTime = FakeData.settings.bedTime
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Mascot(mood = mood, size = 120.dp, modifier = Modifier.revealOnEnter(0))
        Spacer(Modifier.height(24.dp))

        CountUpMinutes(today.totalMinutes)
        Text(
            text = "SCREEN TIME TODAY",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(40.dp))
        Row(modifier = Modifier.fillMaxWidth().revealOnEnter(1)) {
            SmallStat(value = formatCount(today.reelsCount), label = "REELS", modifier = Modifier.weight(1f))
            SmallStat(value = formatCount(today.cheatsToday), label = "CHEATS", modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(48.dp))
        Text(
            text = "TOP APPS",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().revealOnEnter(2)
        )
        Spacer(Modifier.height(16.dp))
        val most = today.topApps.maxOf { it.minutes }
        today.topApps.forEachIndexed { i, app ->
            AppRow(app = app, fraction = app.minutes.toFloat() / most, modifier = Modifier.revealOnEnter(3 + i))
            Spacer(Modifier.height(20.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
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

@Composable
private fun SmallStat(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** App name and time, with a thin bar underneath that grows to its share of the top app. */
@Composable
private fun AppRow(app: AppUsage, fraction: Float, modifier: Modifier = Modifier) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(fraction) { grow.animateTo(fraction, Motion.standard(Motion.EXTRA_LONG, delayMillis = Motion.MEDIUM)) }
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val fill = MaterialTheme.colorScheme.onBackground

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(app.appName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(
                formatMinutes(app.minutes),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .drawBehind {
                    val radius = CornerRadius(size.height / 2)
                    drawRoundRect(track, cornerRadius = radius)
                    drawRoundRect(fill, size = Size(size.width * grow.value, size.height), cornerRadius = radius)
                }
        )
    }
}

@Preview(heightDp = 800)
@Composable
private fun HomeScreenPreview() {
    MindLolTheme { HomeScreen() }
}
