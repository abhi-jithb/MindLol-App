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
import com.abhijith.mindlol.domain.CheatType
import com.abhijith.mindlol.domain.RoastContext
import com.abhijith.mindlol.ui.theme.Black
import com.abhijith.mindlol.ui.theme.MindLolTheme
import com.abhijith.mindlol.ui.theme.Motion
import com.abhijith.mindlol.ui.theme.White
import java.time.LocalTime
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class MascotMood(val description: String) {
    NEUTRAL("calm"),
    SLEEPY("sleepy"),
    SMUG("smug"),
    DRAMATIC("shocked")
}

/**
 * One-off reactions for cheat moments. A reaction overrides the mood's face and brings out
 * the arms. NONE shows the plain mood.
 */
enum class MascotReaction(val description: String) {
    NONE(""),
    STARE("staring at you, arms crossed"),
    FACEPALM("facepalming"),
    HEAD_SHAKE("shaking its head"),
    TURN_AWAY("turning its back on you"),
    NOD("nodding slowly")
}

/**
 * The reaction for one cheat, from the same context its roast line came from. The worse the
 * pattern, the more dramatic: a first offence gets the stare, a habit gets its back turned.
 */
fun reactionFor(context: RoastContext): MascotReaction = when {
    context.sameAppThisWeek >= 3 || context.cheatsThisWeek >= 5 -> MascotReaction.TURN_AWAY
    context.sameAppThisWeek >= 2 -> MascotReaction.HEAD_SHAKE
    context.type == CheatType.LIMIT -> MascotReaction.FACEPALM
    else -> MascotReaction.STARE
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

/** The face each reaction makes. Negative browWorry lowers the inner brow ends: a stern look. */
private fun faceFor(reaction: MascotReaction): Face? = when (reaction) {
    MascotReaction.NONE -> null
    MascotReaction.STARE -> Face(1.2f, 0f, 1f, 0f, 0f, 0f, 1f, 1f, 0f, -0.7f, 0f, 0f)
    MascotReaction.FACEPALM -> Face(0.08f, 0f, 0f, -0.4f, 0f, 0f, 1f, 1f, 0f, 0.8f, 0f, 0f)
    MascotReaction.HEAD_SHAKE -> Face(0.6f, 1f, 0f, -0.5f, 0f, 0f, 1f, 1f, 0f, 0.6f, 0f, 0f)
    MascotReaction.TURN_AWAY -> Face(0.6f, 1f, 0f, -0.2f, 0f, 0f, 1f, 1f, 0f, -0.4f, 0f, 0f)
    MascotReaction.NOD -> Face(0.55f, 1f, 0f, 0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 0f)
}

/**
 * Where the hands are, as 0..1 progress toward each reaction's pose (0 = hanging at the sides).
 * Animated like the face, so arms move smoothly between reactions.
 */
private data class Arms(
    val visible: Float,  // 0 = no arms (plain moods)
    val crossed: Float,  // STARE: both hands over the belly
    val palm: Float,     // FACEPALM: right hand over the eyes
    val hips: Float      // TURN_AWAY: hands on hips
)

private fun armsFor(reaction: MascotReaction): Arms = when (reaction) {
    MascotReaction.NONE -> Arms(0f, 0f, 0f, 0f)
    MascotReaction.STARE -> Arms(1f, 1f, 0f, 0f)
    MascotReaction.FACEPALM -> Arms(1f, 0f, 1f, 0f)
    MascotReaction.HEAD_SHAKE, MascotReaction.NOD -> Arms(1f, 0f, 0f, 0f)
    MascotReaction.TURN_AWAY -> Arms(1f, 0f, 0f, 1f)
}

/** Per-frame extras: the face slides (shake, nod, turn) and fades (turn away); [still] stops breathing and blinking. Offsets are in body radii. */
private data class Pose(val dx: Float, val dy: Float, val featureAlpha: Float, val still: Float, val turned: Float)

@Composable
private fun animated(target: Float): Float =
    animateFloatAsState(target, Motion.standard(Motion.LONG), label = "face").value

/**
 * The MindLol mascot: a round black character with a white outline, drawn on a Canvas.
 * It breathes and blinks on its own. Changing [mood] morphs the face; a [reaction] other than
 * NONE takes over the face and brings out arms for cheat moments.
 */
@Composable
fun Mascot(
    mood: MascotMood,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    reaction: MascotReaction = MascotReaction.NONE
) {
    val target = faceFor(reaction) ?: faceFor(mood)
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

    val armTarget = armsFor(reaction)
    val arms = Arms(
        visible = animated(armTarget.visible),
        crossed = animated(armTarget.crossed),
        palm = animated(armTarget.palm),
        hips = animated(armTarget.hips)
    )
    val still = animated(if (reaction == MascotReaction.STARE) 1f else 0f)
    val turned = animated(if (reaction == MascotReaction.TURN_AWAY) 1f else 0f)

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

    // One slow 2.6 s cycle drives the head shake and the nod.
    val cycle by idle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "reactionCycle"
    )
    val shake = if (reaction == MascotReaction.HEAD_SHAKE) shakeOffset(cycle) else 0f
    val nod = if (reaction == MascotReaction.NOD) nodOffset(cycle) else 0f
    val pose = Pose(dx = shake + turned * 0.9f, dy = nod, featureAlpha = 1f - turned, still = still, turned = turned)

    val label = if (reaction != MascotReaction.NONE) reaction.description else "looking ${mood.description}"
    Canvas(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = "Mascot $label" }
    ) {
        drawMascot(face, breath * (1f - still), 1f + (blink - 1f) * (1f - still), drift, arms, pose)
    }
}

/** Three slow side-to-side shakes in the first 70% of the cycle, then a pause. In body radii. */
private fun shakeOffset(t: Float): Float =
    if (t < 0.7f) 0.13f * sin(t / 0.7f * 3f * 2f * PI.toFloat()) else 0f

/** Two slow dips in the first 70% of the cycle, then a pause. Always downward. In body radii. */
private fun nodOffset(t: Float): Float =
    if (t < 0.7f) 0.12f * sin(t / 0.7f * 2f * PI.toFloat()).let { it * it } else 0f

private fun DrawScope.drawMascot(face: Face, breath: Float, blink: Float, drift: Float, arms: Arms, pose: Pose) {
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

    // Hair tuft on top. It flips direction as the mascot turns away.
    val top = cy - bodyH
    val flip = 1f - 2f * pose.turned
    val tuft = Path().apply {
        moveTo(cx - r * 0.05f * flip, top)
        quadraticTo(cx + r * 0.3f * flip, top - r * 0.3f, cx + r * 0.05f * flip, top - r * 0.42f)
    }
    drawPath(tuft, White, style = line)

    // The face (eyes, brows, mouth) moves together: shaking, nodding, or sliding away when turning.
    // Anything drawn past the body's edge while turning is hidden by fading it out.
    val fx = cx + pose.dx * r
    val fy = pose.dy * r
    val fa = pose.featureAlpha.coerceIn(0f, 1f)

    // Eyes.
    val eyeY = cy - r * 0.12f + fy
    val eyeR = r * 0.13f
    val eyeH = max(eyeR * 2 * face.eyeOpen * blink, stroke)
    for (side in listOf(-1f, 1f)) {
        val x = fx + side * r * 0.38f
        drawOval(White, Offset(x - eyeR, eyeY - eyeH / 2), Size(eyeR * 2, eyeH), alpha = fa)
        if (face.pupils > 0f) {
            drawCircle(Black, eyeR * 0.45f * min(1f, blink * 1.5f), Offset(x, eyeY), alpha = face.pupils * fa)
        }
        if (face.lid > 0f) {
            val lidY = eyeY - eyeH / 2
            drawLine(White, Offset(x - eyeR * 1.3f, lidY), Offset(x + eyeR * 1.3f, lidY), stroke, StrokeCap.Round, alpha = face.lid * fa)
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
                stroke, StrokeCap.Round, alpha = (browAlpha * fa).coerceIn(0f, 1f)
            )
        }
    }

    // Mouth: a curve that fades into an open "O" as mouthOpen grows.
    val mouthY = cy + r * 0.32f + fy
    val mouthW = r * 0.3f
    val openness = min(1f, face.mouthOpen * 4f)
    if (openness < 1f) {
        val mouth = Path().apply {
            moveTo(fx - mouthW, mouthY)
            quadraticTo(fx, mouthY + face.mouthCurve * r * 0.3f, fx + mouthW, mouthY - face.mouthTilt * r * 0.18f)
        }
        drawPath(mouth, White, alpha = (1f - openness) * fa, style = line)
    }
    if (openness > 0f) {
        val w = r * 0.32f * face.mouthOpen
        val h = r * 0.4f * face.mouthOpen
        drawOval(White, Offset(fx - w / 2, mouthY + r * 0.05f - h / 2), Size(w, h), alpha = openness * fa, style = line)
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

    if (arms.visible > 0f) drawArms(cx, cy, r, eyeY, fx, stroke, arms)

    // "Hmph": three short puff lines beside the head once it has turned away.
    if (pose.turned > 0.5f) {
        val a = (pose.turned - 0.5f) * 2f
        val px = cx - r * 1.15f
        val py = cy - r * 0.35f
        for (i in -1..1) {
            val y = py + i * r * 0.16f
            drawLine(White, Offset(px, y), Offset(px - r * 0.22f, y + i * r * 0.06f), stroke * 0.8f, StrokeCap.Round, alpha = a)
        }
    }
}

/**
 * Two arms: a curved line from each shoulder to a round hand. Each hand's position blends from
 * hanging at the side toward the pose the reaction asks for (crossed, palm on face, on hips).
 */
private fun DrawScope.drawArms(cx: Float, cy: Float, r: Float, eyeY: Float, fx: Float, stroke: Float, arms: Arms) {
    val line = Stroke(width = stroke, cap = StrokeCap.Round)
    val handR = r * 0.11f
    for (side in listOf(-1f, 1f)) {
        val shoulder = Offset(cx + side * r * 0.97f, cy + r * 0.2f)
        val rest = Offset(cx + side * r * 1.18f, cy + r * 0.72f)
        val crossed = Offset(cx - side * r * 0.55f, cy + r * (0.66f + side * 0.06f)) // across the belly, below the mouth
        val hip = Offset(cx + side * r * 0.98f, cy + r * 0.55f)
        // Only the right arm (side = 1) goes to the face.
        val palm = if (side > 0) Offset(fx + r * 0.05f, eyeY) else rest

        var hand = rest
        hand = lerp(hand, crossed, arms.crossed)
        hand = lerp(hand, hip, arms.hips)
        hand = lerp(hand, palm, arms.palm)

        // Each pose has its own elbow; blend them the same way as the hands.
        var elbow = Offset(cx + side * r * 1.25f, (shoulder.y + hand.y) / 2 + r * 0.1f)
        elbow = lerp(elbow, Offset(cx + side * r * 1.15f, cy + r * 0.95f), arms.crossed)
        elbow = lerp(elbow, Offset(cx + side * r * 1.55f, cy + r * 0.05f), arms.hips) // akimbo: elbows out
        if (side > 0) elbow = lerp(elbow, Offset(cx + r * 1.2f, cy + r * 0.85f), arms.palm) // up from below
        val arm = Path().apply {
            moveTo(shoulder.x, shoulder.y)
            quadraticTo(elbow.x, elbow.y, hand.x, hand.y)
        }
        drawPath(arm, White, alpha = arms.visible, style = line)

        // The right hand grows into a flat palm wide enough to cover both eyes, fingers up.
        val cover = if (side > 0) arms.palm else 0f
        val w = handR * (2f + cover * 8f)
        val h = handR * (2f + cover * 1.6f)
        val topLeft = Offset(hand.x - w / 2, hand.y - h / 2)
        drawOval(Black, topLeft, Size(w, h), alpha = arms.visible)
        drawOval(White, topLeft, Size(w, h), alpha = arms.visible, style = line)
        if (cover > 0.5f) {
            val a = (cover - 0.5f) * 2f * arms.visible
            for (i in -1..1) {
                val x = hand.x + i * w * 0.22f
                drawLine(White, Offset(x, hand.y - h * 0.15f), Offset(x, hand.y - h * 0.5f - handR * 0.9f), stroke, StrokeCap.Round, alpha = a)
            }
        }
    }
}

private fun lerp(a: Offset, b: Offset, t: Float): Offset = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

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

@Preview(showBackground = true, backgroundColor = 0xFF000000, widthDp = 640)
@Composable
private fun MascotReactionsPreview() {
    MindLolTheme {
        Row(
            modifier = Modifier.background(Black).padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            MascotReaction.entries.drop(1).forEach { reaction ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Mascot(MascotMood.NEUTRAL, size = 96.dp, reaction = reaction)
                    Text(reaction.name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
