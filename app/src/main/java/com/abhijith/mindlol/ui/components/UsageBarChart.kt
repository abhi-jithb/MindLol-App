package com.abhijith.mindlol.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.abhijith.mindlol.domain.StatsPeriod
import com.abhijith.mindlol.domain.UsageSeries
import com.abhijith.mindlol.ui.theme.MindLolTheme
import com.abhijith.mindlol.ui.theme.Motion
import kotlin.math.max
import kotlin.math.min

/**
 * Bar chart drawn by hand on a Canvas. One bar per bucket; the busiest bar is white, the rest gray.
 * A dashed line marks the average. Bars rise left to right whenever the period changes.
 */
@Composable
fun UsageBarChart(series: UsageSeries, modifier: Modifier = Modifier) {
    // remember(key) makes a fresh Animatable for each period, so the bars replay from 0.
    val progress = remember(series.period) { Animatable(0f) }
    LaunchedEffect(series.period) {
        progress.animateTo(1f, tween(Motion.EXTRA_LONG, easing = LinearEasing))
    }

    val barColor = MaterialTheme.colorScheme.onSurfaceVariant
    val peakColor = MaterialTheme.colorScheme.onBackground
    val gridColor = MaterialTheme.colorScheme.outline
    val labelStyle = MaterialTheme.typography.labelMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.sp
    )
    val textMeasurer = rememberTextMeasurer()
    // Show fewer labels when there are many bars, so they don't overlap.
    val labelEvery = when (series.period) {
        StatsPeriod.DAY -> 6
        StatsPeriod.MONTH -> 5
        StatsPeriod.WEEK, StatsPeriod.YEAR -> 1
    }
    val description = "Bar chart. Total ${formatMinutes(series.totalMinutes)}, " +
        "busiest ${series.fullLabels[series.peakIndex]}."

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp)
            .semantics { contentDescription = description }
    ) {
        val n = series.minutes.size
        val labelArea = 24.dp.toPx()
        val chartH = size.height - labelArea
        val slot = size.width / n
        val barW = slot * if (n > 20) 0.6f else 0.45f
        val maxMinutes = max(1, series.minutes.max()).toFloat()

        // Average line, fading in with the bars.
        val avgY = chartH * (1f - series.averageMinutes / maxMinutes)
        drawLine(
            color = gridColor,
            start = Offset(0f, avgY),
            end = Offset(size.width, avgY),
            strokeWidth = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            alpha = progress.value * 0.8f
        )

        series.minutes.forEachIndexed { i, minutes ->
            // Stagger: bar i starts a little after bar i-1. Each bar then eases on its own.
            val start = 0.35f * i / max(1, n - 1)
            val t = Motion.Standard.transform(((progress.value - start) / 0.65f).coerceIn(0f, 1f))
            val h = chartH * (minutes / maxMinutes) * t
            val left = i * slot + (slot - barW) / 2
            drawRoundRect(
                color = if (i == series.peakIndex) peakColor else barColor,
                topLeft = Offset(left, chartH - h),
                size = Size(barW, h),
                cornerRadius = CornerRadius(min(barW / 2, 3.dp.toPx()))
            )

            if (i % labelEvery == 0) {
                val label = textMeasurer.measure(series.labels[i], labelStyle)
                drawText(
                    textLayoutResult = label,
                    topLeft = Offset(
                        x = (i * slot + slot / 2 - label.size.width / 2).coerceIn(0f, size.width - label.size.width),
                        y = chartH + 6.dp.toPx()
                    )
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun UsageBarChartPreview() {
    val sample = UsageSeries(
        period = StatsPeriod.WEEK,
        labels = listOf("Fri", "Sat", "Sun", "Mon", "Tue", "Wed", "Thu"),
        minutes = listOf(196, 241, 227, 158, 172, 149, 134),
        apps = emptyList()
    )
    MindLolTheme { UsageBarChart(sample) }
}
