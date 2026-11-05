package com.abhijith.mindlol.ui.screens.stats

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
import com.abhijith.mindlol.data.repository.FakeData
import com.abhijith.mindlol.domain.StatsPeriod
import com.abhijith.mindlol.ui.components.AppUsageRow
import com.abhijith.mindlol.ui.components.PeriodSelector
import com.abhijith.mindlol.ui.components.UsageBarChart
import com.abhijith.mindlol.ui.components.SmallStat
import com.abhijith.mindlol.ui.components.formatMinutes
import com.abhijith.mindlol.ui.components.revealOnEnter
import com.abhijith.mindlol.ui.theme.MindLolTheme
import com.abhijith.mindlol.ui.theme.Motion

@Composable
fun StatsScreen() {
    // rememberSaveable: the chosen period survives tab switches and rotation.
    var period by rememberSaveable { mutableStateOf(StatsPeriod.WEEK) }
    val series = FakeData.series(period)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        PeriodSelector(selected = period, onSelect = { period = it })

        Spacer(Modifier.height(32.dp))
        AnimatedTotal(series.totalMinutes)
        Text(
            text = "TOTAL · " + when (period) {
                StatsPeriod.DAY -> "TODAY"
                StatsPeriod.WEEK -> "LAST 7 DAYS"
                StatsPeriod.MONTH -> "LAST 30 DAYS"
                StatsPeriod.YEAR -> "LAST 12 MONTHS"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))
        UsageBarChart(series)

        Spacer(Modifier.height(32.dp))
        Row(Modifier.fillMaxWidth().revealOnEnter(1)) {
            SmallStat(
                value = formatMinutes(series.averageMinutes),
                label = "AVG / ${period.bucketName.uppercase()}",
                modifier = Modifier.weight(1f)
            )
            SmallStat(
                value = series.fullLabels[series.peakIndex],
                label = "BUSIEST ${period.bucketName.uppercase()}",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(40.dp))
        Text(
            text = "BY APP",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().revealOnEnter(2)
        )
        Spacer(Modifier.height(16.dp))
        val total = series.totalMinutes.coerceAtLeast(1)
        val most = series.apps.maxOf { it.minutes }.coerceAtLeast(1)
        series.apps.forEachIndexed { i, app ->
            AppUsageRow(
                app = app,
                fraction = app.minutes.toFloat() / most,
                trailing = "${formatMinutes(app.minutes)} · ${app.minutes * 100 / total}%",
                modifier = Modifier.revealOnEnter(3 + i)
            )
            Spacer(Modifier.height(20.dp))
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** The big number. Rolls from the old total to the new one when the period changes. */
@Composable
private fun AnimatedTotal(totalMinutes: Int) {
    val value = remember { Animatable(totalMinutes.toFloat()) }
    LaunchedEffect(totalMinutes) { value.animateTo(totalMinutes.toFloat(), Motion.standard(Motion.EXTRA_LONG)) }
    Text(
        text = formatMinutes(value.value.toInt()),
        style = MaterialTheme.typography.displayLarge.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1
    )
}

@Preview(heightDp = 900)
@Composable
private fun StatsScreenPreview() {
    MindLolTheme { StatsScreen() }
}
