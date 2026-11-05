package com.abhijith.mindlol.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.abhijith.mindlol.domain.StatsPeriod
import com.abhijith.mindlol.ui.theme.Motion

/** Day / Week / Month / Year tabs. A short white line slides under the selected one. */
@Composable
fun PeriodSelector(
    selected: StatsPeriod,
    onSelect: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val periods = StatsPeriod.entries
    // BoxWithConstraints tells us our own width, so we can place the underline by index.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val itemWidth = maxWidth / periods.size
        val indicatorX by animateDpAsState(itemWidth * selected.ordinal, Motion.standard(), label = "indicator")

        Row(Modifier.fillMaxWidth()) {
            periods.forEach { period ->
                val color by animateColorAsState(
                    if (period == selected) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    Motion.standard(),
                    label = "periodColor"
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null, // no gray ripple; the underline is the feedback
                            role = Role.Tab
                        ) { onSelect(period) }
                        .semantics { this.selected = period == selected }
                        .padding(vertical = 12.dp)
                ) {
                    Text(period.label, style = MaterialTheme.typography.bodyLarge, color = color)
                }
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .offset(x = indicatorX + itemWidth / 2 - 12.dp)
                .width(24.dp)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.onBackground)
        )
    }
}
