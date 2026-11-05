package com.abhijith.mindlol.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.abhijith.mindlol.ui.theme.Black
import com.abhijith.mindlol.ui.theme.MindLolTheme
import com.abhijith.mindlol.ui.theme.Motion
import com.abhijith.mindlol.ui.theme.White
import java.time.LocalTime
import kotlin.math.max
import kotlin.math.min

enum class MascotMood(val description: String) {
    NEUTRAL("calm"),
    SLEEPY("sleepy"),
    SMUG("smug"),
    DRAMATIC("shocked")
}

/**
 * Picks the mascot's mood from the day's numbers.
 * Order matters: the first rule that matches wins.
 */
fun pickMood(
    totalMinutes: Int,
    averageMinutes: Int,
    cheatsToday: Int,
    now: LocalTime,
    bedTime: LocalTime
): MascotMood = when {
    // From 30 minutes before bedtime until 5 AM, it is tired of you.
    now >= bedTime.minusMinutes(30) || now < LocalTime.of(5, 0) -> MascotMood.SLEEPY
    cheatsToday >= 3 || totalMinutes > averageMinutes * 6 / 5 -> MascotMood.DRAMATIC
    cheatsToday > 0 -> MascotMood.SMUG
    else -> MascotMood.NEUTRAL
}

/**
 * Every part of the face as a number. Moods are just different sets of numbers,
 * which lets one mood morph smoothly into another.
 */
private data class Face(
    val eyeOpen: Float,     // 0 = closed line, 1 = round, 1.5 = wide
    val lid: Float,         // flat upper eyelid, 0..1 visibility
    val pupils: Float,      // black pupils inside the eyes, 0..1 visibility
    val mouthCurve: Float,  // -1 frown .. 1 smile
    val mouthTilt: Float,   // lifts the right corner: a smirk
    val mouthOpen: Float,   // 0 = line, 1 = big open "O"
    val brows: Float,       // eyebrow visibility
    val leftBrow: Float,    // extra visibility for the left brow only (smug hides it)
    val rightBrowLift: Float,
    val browWorry: Float,   // raises the inner ends of both brows
    val zzz: Float,         // floating Z's
    val sweat: Float        // sweat drop
)

private fun faceFor(mood: MascotMood): Face = when (mood) {
    MascotMood.NEUTRAL -> Face(1f, 0f, 0f, 0.3f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f)
    MascotMood.SLEEPY -> Face(0.08f, 0f, 0f, 0f, 0f, 0.4f, 0f, 1f, 0f, 0f, 1f, 0f)
    MascotMood.SMUG -> Face(0.6f, 1f, 0f, 0.45f, 0.8f, 0f, 1f, 0f, 1f, 0f, 0f, 0f)
    MascotMood.DRAMATIC -> Face(1.5f, 0f, 1f, -0.3f, 0f, 1f, 1f, 1f, 0f, 1f, 0f, 1f)
}

@Composable
private fun animated(target: Float): Float =
    animateFloatAsState(target, Motion.standard(Motion.LONG), label = "face").value

/**
 * The MindLol mascot: a round black character with a white outline, drawn on a Canvas.
 * It breathes and blinks on its own. Changing [mood] morphs the face.
 */
@Composable
fun Mascot(
    mood: MascotMood,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp
) {
    val target = faceFor(mood)
    // Each number animates toward the new mood's value.
    val face = Face(
        eyeOpen = animated(target.eyeOpen),
        lid = animated(target.lid),
        pupils = animated(target.pupils),
        mouthCurve = animated(target.mouthCurve),
        mouthTilt = animated(target.mouthTilt),
        mouthOpen = animated(target.mouthOpen),
        brows = animated(target.brows),
        leftBrow = animated(target.leftBrow),
        rightBrowLift = animated(target.rightBrowLift),
        browWorry = animated(target.browWorry),
        zzz = animated(target.zzz),
        sweat = animated(target.sweat)
    )

    // Idle loops that run forever while the mascot is on screen.
    val idle = rememberInfiniteTransition(label = "idle")
    val breath by idle.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath"
    )
    val blink by idle.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 4200
                1f at 3900
                0.1f at 4000
                1f at 4100
            }
        ),
        label = "blink"
    )
    val drift by idle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "drift"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Mascot looking ${mood.description}" }
    ) {
        drawMascot(face, breath, blink, drift)
    }
}

private fun DrawScope.drawMascot(face: Face, breath: Float, blink: Float, drift: Float) {
    val s = size.minDimension
    val r = s * 0.34f
    val stroke = s * 0.022f
    val cx = size.width / 2
    val cy = size.height / 2 + s * 0.04f + breath * s * 0.008f
    val line = Stroke(width = stroke, cap = StrokeCap.Round)

    // Body: black fill, white outline, a little squash and stretch while breathing.
    val bodyW = r * (1f - breath * 0.012f)
    val bodyH = r * (1f + breath * 0.012f)
    val bodyTopLeft = Offset(cx - bodyW, cy - bodyH)
    val bodySize = Size(bodyW * 2, bodyH * 2)
    drawOval(Black, bodyTopLeft, bodySize)
    drawOval(White, bodyTopLeft, bodySize, style = line)

    // Hair tuft on top.
    val top = cy - bodyH
    val tuft = Path().apply {
        moveTo(cx - r * 0.05f, top)
        quadraticTo(cx + r * 0.3f, top - r * 0.3f, cx + r * 0.05f, top - r * 0.42f)
    }
    drawPath(tuft, White, style = line)

    // Eyes.
    val eyeY = cy - r * 0.12f
    val eyeR = r * 0.13f
    val eyeH = max(eyeR * 2 * face.eyeOpen * blink, stroke)
    for (side in listOf(-1f, 1f)) {
        val x = cx + side * r * 0.38f
        drawOval(White, Offset(x - eyeR, eyeY - eyeH / 2), Size(eyeR * 2, eyeH))
        if (face.pupils > 0f) {
            drawCircle(Black, eyeR * 0.45f * min(1f, blink * 1.5f), Offset(x, eyeY), alpha = face.pupils)
        }
        if (face.lid > 0f) {
            val lidY = eyeY - eyeH / 2
            drawLine(White, Offset(x - eyeR * 1.3f, lidY), Offset(x + eyeR * 1.3f, lidY), stroke, StrokeCap.Round, alpha = face.lid)
        }
        val browAlpha = if (side < 0) face.brows * face.leftBrow else face.brows
        if (browAlpha > 0f) {
            val lift = if (side > 0) face.rightBrowLift * eyeR * 0.9f else 0f
            val baseY = eyeY - eyeR * 2.3f - lift
            val innerX = x - side * eyeR * 1.1f
            val outerX = x + side * eyeR * 1.1f
            drawLine(
                White,
                Offset(innerX, baseY - face.browWorry * eyeR * 0.7f),
                Offset(outerX, baseY + face.browWorry * eyeR * 0.4f),
                stroke, StrokeCap.Round, alpha = browAlpha
            )
        }
    }

    // Mouth: a curve that fades into an open "O" as mouthOpen grows.
    val mouthY = cy + r * 0.32f
    val mouthW = r * 0.3f
    val openness = min(1f, face.mouthOpen * 4f)
    if (openness < 1f) {
        val mouth = Path().apply {
            moveTo(cx - mouthW, mouthY)
            quadraticTo(cx, mouthY + face.mouthCurve * r * 0.3f, cx + mouthW, mouthY - face.mouthTilt * r * 0.18f)
        }
        drawPath(mouth, White, alpha = 1f - openness, style = line)
    }
    if (openness > 0f) {
        val w = r * 0.32f * face.mouthOpen
        val h = r * 0.4f * face.mouthOpen
        drawOval(White, Offset(cx - w / 2, mouthY + r * 0.05f - h / 2), Size(w, h), alpha = openness, style = line)
    }

    // Floating Z's for SLEEPY.
    if (face.zzz > 0f) {
        val fade = face.zzz * (1f - drift * 0.6f)
        drawZ(cx + r * 0.85f, cy - r * 0.9f - drift * r * 0.2f, r * 0.14f, stroke, fade)
        drawZ(cx + r * 1.1f, cy - r * 1.25f - drift * r * 0.2f, r * 0.2f, stroke, fade * 0.7f)
    }

    // Sweat drop for DRAMATIC.
    if (face.sweat > 0f) {
        // Just outside the outline, upper right.
        val x = cx + r * 1.0f
        val y = cy - r * 0.78f
        val d = r * 0.14f
        val drop = Path().apply {
            moveTo(x, y - d)
            quadraticTo(x + d * 0.9f, y + d * 0.4f, x, y + d * 0.8f)
            quadraticTo(x - d * 0.9f, y + d * 0.4f, x, y - d)
        }
        drawPath(drop, White, alpha = face.sweat, style = line)
    }
}

private fun DrawScope.drawZ(x: Float, y: Float, w: Float, stroke: Float, alpha: Float) {
    val z = Path().apply {
        moveTo(x, y)
        lineTo(x + w, y)
        lineTo(x, y + w)
        lineTo(x + w, y + w)
    }
    drawPath(z, White, alpha = alpha.coerceIn(0f, 1f), style = Stroke(stroke * 0.8f, cap = StrokeCap.Round))
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun MascotMoodsPreview() {
    MindLolTheme {
        Row(
            modifier = Modifier.background(Black).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MascotMood.entries.forEach { mood ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Mascot(mood, size = 96.dp)
                    Text(mood.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
