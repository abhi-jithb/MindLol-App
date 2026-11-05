package com.abhijith.mindlol.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.abhijith.mindlol.domain.AppUsage
import com.abhijith.mindlol.ui.theme.Motion

/**
 * App name and time, with a thin bar underneath.
 * [fraction] (0..1) is how full the bar is; it grows there from empty, and glides when it changes.
 * [trailing] overrides the right-hand text (defaults to the formatted time).
 */
@Composable
fun AppUsageRow(
    app: AppUsage,
    fraction: Float,
    modifier: Modifier = Modifier,
    trailing: String = formatMinutes(app.minutes)
) {
    val grow = remember { Animatable(0f) }
    LaunchedEffect(fraction) { grow.animateTo(fraction, Motion.standard(Motion.EXTRA_LONG, delayMillis = Motion.MEDIUM)) }
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val fill = MaterialTheme.colorScheme.onBackground

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(app.appName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
            Text(
                trailing,
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
