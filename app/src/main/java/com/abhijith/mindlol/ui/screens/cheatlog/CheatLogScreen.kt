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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.abhijith.mindlol.data.repository.FakeData
import com.abhijith.mindlol.domain.CheatEntry
import com.abhijith.mindlol.ui.components.Mascot
import com.abhijith.mindlol.ui.components.MascotMood
import com.abhijith.mindlol.ui.components.revealOnEnter
import com.abhijith.mindlol.ui.theme.MindLolTheme

@Composable
fun CheatLogScreen() {
    val entries = FakeData.cheatLog
    // groupBy keeps the original order: Today, Yesterday, Tue, ...
    val byDay = entries.groupBy { it.day }
    val mood = if (entries.size >= 5) MascotMood.DRAMATIC else MascotMood.SMUG

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
                Mascot(mood = mood, size = 88.dp, modifier = Modifier.revealOnEnter(0))
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "${entries.size}",
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
        byDay.forEach { (day, dayEntries) ->
            item(key = "day-$day") {
                Text(
                    text = day.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp, bottom = 8.dp).revealOnEnter(row)
                )
            }
            row++
            items(dayEntries, key = { "${it.day}-${it.time}" }) { entry ->
                CheatRow(entry, Modifier.revealOnEnter(row.coerceAtMost(8)))
            }
            row += dayEntries.size
        }
    }
}

/** Time on the left; the roast and the app on the right. */
@Composable
private fun CheatRow(entry: CheatEntry, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                text = entry.time,
                style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(76.dp).padding(top = 4.dp)
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = entry.roast,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.appName.uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
    }
}

@Preview(heightDp = 900)
@Composable
private fun CheatLogScreenPreview() {
    MindLolTheme { CheatLogScreen() }
}
