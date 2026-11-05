package com.abhijith.mindlol.ui.screens.cheatlog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.abhijith.mindlol.domain.Cheat
import com.abhijith.mindlol.domain.CheatEntry
import com.abhijith.mindlol.domain.CheatType
import com.abhijith.mindlol.domain.RoastContext
import com.abhijith.mindlol.domain.RoastIntensity
import com.abhijith.mindlol.ui.components.Mascot
import com.abhijith.mindlol.ui.components.MascotMood
import com.abhijith.mindlol.ui.components.reactionFor
import com.abhijith.mindlol.ui.components.revealOnEnter
import com.abhijith.mindlol.ui.theme.MindLolTheme
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

@Composable
fun CheatLogScreen(viewModel: CheatLogViewModel = viewModel(factory = CheatLogViewModel.Factory)) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    CheatLogContent(state)
}

/** Stateless: draws the given state. Cheats are grouped by the day they happened, newest first. */
@Composable
fun CheatLogContent(state: CheatLogUiState) {
    val zone = ZoneId.systemDefault()
    if (state.loading) {
        Spacer(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
        return
    }
    if (state.entries.isEmpty()) {
        NoCheats()
        return
    }
    // groupBy keeps the order (newest first), so days come out Today, Yesterday, Tue, ...
    val byDay = state.entries.groupBy { Instant.ofEpochMilli(it.cheat.startMillis).atZone(zone).toLocalDate() }
    // The header mascot reacts to the latest cheat.
    val latest = reactionFor(state.entries.first().context)

    // LazyColumn only composes rows that are on screen, like ListView.builder in Flutter.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
    ) {
        item(key = "header") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Mascot(mood = MascotMood.DRAMATIC, reaction = latest, size = 112.dp, modifier = Modifier.revealOnEnter(0))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "${state.cheatsThisWeek}",
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "CHEATS THIS WEEK",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        var row = 1 // running index, so rows enter one after another
        byDay.forEach { (date, dayEntries) ->
            item(key = "day-$date") {
                Text(
                    text = dayLabel(date, LocalDate.now(zone)).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp).revealOnEnter(row.coerceAtMost(8))
                )
            }
            row++
            items(dayEntries, key = { it.cheat.key.toString() }) { entry ->
                CheatRow(entry, zone, Modifier.revealOnEnter(row.coerceAtMost(8)))
            }
            row += dayEntries.size
        }
    }
}

/** Mascot reacting on the left; time, roast and details on the right. */
@Composable
private fun CheatRow(entry: CheatEntry, zone: ZoneId, modifier: Modifier = Modifier) {
    val start = Instant.ofEpochMilli(entry.cheat.startMillis).atZone(zone).toLocalTime()
    val kind = if (entry.cheat.type == CheatType.BEDTIME) "${entry.cheat.minutes} MIN IN BED" else "${entry.cheat.minutes} MIN OVER LIMIT"
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top
        ) {
            Mascot(mood = MascotMood.NEUTRAL, reaction = reactionFor(entry.context), size = 56.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = start.format(timeFormat),
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.roast,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${entry.appName.uppercase()} · $kind",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
    }
}

/** No cheats yet: the mascot is smug about it, and a little suspicious. */
@Composable
private fun NoCheats() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Mascot(mood = MascotMood.SMUG, size = 140.dp, modifier = Modifier.revealOnEnter(0))
        Spacer(Modifier.height(24.dp))
        Text(
            text = "No cheats. Yet.",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.revealOnEnter(1)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Clean record. I'm still watching.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.revealOnEnter(2)
        )
    }
}

/** "Today", "Yesterday", the weekday within a week, otherwise "5 Nov". */
private fun dayLabel(date: LocalDate, today: LocalDate): String = when {
    date == today -> "Today"
    date == today.minusDays(1) -> "Yesterday"
    date.isAfter(today.minusDays(7)) -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    else -> "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
}

@Preview(heightDp = 900)
@Composable
private fun CheatLogPreview() {
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val cheat = Cheat("com.instagram.android", CheatType.BEDTIME, LocalDate.now(zone), now, now + 600_000, 10)
    val context = RoastContext("Instagram", CheatType.BEDTIME, LocalTime.of(0, 47), 10, 1, 1,
        LocalTime.of(23, 0), LocalTime.of(8, 0), null, RoastIntensity.SAVAGE, "preview")
    MindLolTheme {
        CheatLogContent(CheatLogUiState(false, listOf(CheatEntry(cheat, "Instagram", "Bedtime was 11. You chose Instagram.", context)), 1))
    }
}

@Preview(heightDp = 700)
@Composable
private fun NoCheatsPreview() {
    MindLolTheme { CheatLogContent(CheatLogUiState(loading = false)) }
}
