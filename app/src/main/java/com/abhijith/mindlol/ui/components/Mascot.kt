package com.abhijith.mindlol.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
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
    val sweat: Float,       // sweat drop
    val browDrop: Float = 0f,   // lowers both brows without tilting them (serious, not angry)
    val mouthWidth: Float = 1f  // 1 = normal width; NOD uses a short tight line
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
    // Half-closed eyes under flat lids, straight low brows, a short tight mouth: "makes sense."
    MascotReaction.NOD -> Face(0.55f, 1f, 0f, 0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 0f, browDrop = 0.35f, mouthWidth = 0.6f)
}

/**
 * Where the hands are, as 0..1 progress toward each reaction's pose (0 = hanging at the sides).
 * Animated like the face, so arms move smoothly between reactions.
 */
private data class Arms(
    val visible: Float,  // 0 = no arms (plain moods)
    val crossed: Float,  // unused since the polish: STARE has no arms (they read as a chin strap)
    val palm: Float,     // FACEPALM: right hand over the eyes
    val hips: Float      // TURN_AWAY: hands on hips
)

private fun armsFor(reaction: MascotReaction): Arms = when (reaction) {
    MascotReaction.NONE -> Arms(0f, 0f, 0f, 0f)
    // No arms: crossed arms on a round body read as a strap across the chin. The stern, still face says it.
    MascotReaction.STARE -> Arms(0f, 0f, 0f, 0f)
    MascotReaction.FACEPALM -> Arms(1f, 0f, 1f, 0f)
    MascotReaction.HEAD_SHAKE, MascotReaction.NOD -> Arms(1f, 0f, 0f, 0f)
    MascotReaction.TURN_AWAY -> Arms(1f, 0f, 0f, 1f)
}

/** Per-frame extras: the face slides (shake, nod, turn) and fades (turn away); [still] stops breathing and blinking. Offsets are in body radii. */
private data class Pose(
    val dx: Float,
    val dy: Float,
    val featureAlpha: Float,
    val still: Float,
    val turned: Float,
    /** 0..1 how deep into the nod: closes the eyes, tips the tuft forward, squashes the body a little. */
    val nod: Float = 0f
)

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
        sweat = animated(target.sweat),
        browDrop = animated(target.browDrop),
        mouthWidth = animated(target.mouthWidth)
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

    // The nod has its own 3.2 s timeline that starts at 0 the moment NOD begins (when the card's
    // reply starts), instead of joining a loop halfway. With animations off, it holds the bottom pose.
    val context = LocalContext.current
    val animationsOff = remember { animationsDisabled(context) }
    val nodTime = remember { Animatable(0f) }
    LaunchedEffect(reaction, animationsOff) {
        nodTime.snapTo(0f)
        if (reaction == MascotReaction.NOD && !animationsOff) {
            while (true) {
                nodTime.snapTo(0f)
                nodTime.animateTo(1f, tween(NOD_CYCLE_MS, easing = LinearEasing))
            }
        }
    }
    val nodDepth = when {
        reaction != MascotReaction.NOD -> 0f
        animationsOff -> 1f
        else -> nodDepth(nodTime.value)
    }
    val pose = Pose(
        dx = shake + turned * 0.9f,
        dy = nodDepth * NOD_DROP,
        featureAlpha = 1f - turned,
        still = still,
        turned = turned,
        nod = nodDepth
    )

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

private const val NOD_CYCLE_MS = 3200
private const val NOD_DROP = 0.125f // how far the face dips, in body radii

/**
 * One slow, serious nod per 3.2 s cycle ([t] is 0..1): 0.9 s easing down, 0.3 s holding at the
 * bottom, 0.9 s easing back up, then 1.1 s still. Returns the depth, 0 (up) to 1 (bottom).
 */
private fun nodDepth(t: Float): Float {
    val down = 900f / NOD_CYCLE_MS
    val hold = 300f / NOD_CYCLE_MS
    val up = 900f / NOD_CYCLE_MS
    return when {
        t < down -> FastOutSlowInEasing.transform(t / down)
        t < down + hold -> 1f
        t < down + hold + up -> 1f - FastOutSlowInEasing.transform((t - down - hold) / up)
        else -> 0f
    }
}

/** True when the phone's "Animator duration scale" is off. Compose animations would then jump to their end. */
private fun animationsDisabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private fun DrawScope.drawMascot(face: Face, breath: Float, blink: Float, drift: Float, arms: Arms, pose: Pose) {
    val s = size.minDimension
    val r = s * 0.34f
    val stroke = s * 0.022f
    val cx = size.width / 2
    val cy = size.height / 2 + s * 0.04f + breath * s * 0.008f
    val line = Stroke(width = stroke, cap = StrokeCap.Round)

    // Body: black fill, white outline, a little squash and stretch while breathing.
    // The nod adds a 2% squash at its bottom, as if it has weight.
    val bodyW = r * (1f - breath * 0.012f + pose.nod * 0.02f)
    val bodyH = r * (1f + breath * 0.012f - pose.nod * 0.02f)
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
    // At the bottom of a nod the tuft tips forward about 15 degrees, pivoting where it meets the head.
    rotate(degrees = pose.nod * 15f, pivot = Offset(cx - r * 0.05f * flip, top)) {
        drawPath(tuft, White, style = line)
    }

    // The face (eyes, brows, mouth) moves together: shaking, nodding, or sliding away when turning.
    // Anything drawn past the body's edge while turning is hidden by fading it out.
    val fx = cx + pose.dx * r
    val fy = pose.dy * r
    val fa = pose.featureAlpha.coerceIn(0f, 1f)

    // Eyes.
    val eyeY = cy - r * 0.12f + fy
    val eyeR = r * 0.13f
    // A nod closes the eyes on the way down: a slow, deliberate blink.
    val eyeH = max(eyeR * 2 * face.eyeOpen * blink * (1f - pose.nod), stroke)
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
            val baseY = eyeY - eyeR * 2.3f - lift + face.browDrop * eyeR
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
    val mouthW = r * 0.3f * face.mouthWidth
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

        // The right hand becomes one clean palm silhouette over both eyes (see drawPalm);
        // every other hand stays a small round hand.
        val cover = if (side > 0) arms.palm else 0f
        if (cover > 0.3f) {
            drawPalm(hand, r, stroke, scale = 0.4f + 0.6f * cover, alpha = arms.visible)
        } else {
            val topLeft = Offset(hand.x - handR, hand.y - handR)
            drawOval(Black, topLeft, Size(handR * 2, handR * 2), alpha = arms.visible)
            drawOval(White, topLeft, Size(handR * 2, handR * 2), alpha = arms.visible, style = line)
        }
    }
}

/**
 * FACEPALM's hand: a rounded palm wide enough to cover both eyes, four short fingers on top and a
 * thumb at the lower left, merged into ONE outline (Path union), so there are no lines inside it.
 * Black fill hides the eyes; the white outline matches the body. [center] is the palm's middle.
 */
private fun DrawScope.drawPalm(center: Offset, r: Float, stroke: Float, scale: Float, alpha: Float) {
    val palmW = r * 1.2f * scale // wide enough to hide both eyes and both brow tips
    val palmH = r * 0.42f * scale
    val corner = CornerRadius(r * 0.16f * scale)
    val palmTop = center.y - palmH / 2
    var shape = Path().apply {
        addRoundRect(RoundRect(Rect(center.x - palmW / 2, palmTop, center.x + palmW / 2, center.y + palmH / 2), corner))
    }
    // Four fingers, a little apart, the middle two slightly longer.
    // Long, slim fingers with clear gaps, so it reads as an open hand, not a fist.
    val fingerW = palmW * 0.165f
    val gap = (palmW * 0.9f - fingerW * 4) / 3
    val startX = center.x - palmW * 0.45f
    val lengths = listOf(0.36f, 0.45f, 0.43f, 0.33f)
    lengths.forEachIndexed { i, len ->
        val left = startX + i * (fingerW + gap)
        val finger = Path().apply {
            addRoundRect(RoundRect(Rect(left, palmTop - r * len * scale, left + fingerW, palmTop + palmH * 0.4f), CornerRadius(fingerW / 2)))
        }
        shape = Path.combine(PathOperation.Union, shape, finger)
    }
    // Thumb, sticking out toward the middle of the face at the bottom left.
    val thumb = Path().apply {
        val top = center.y + palmH * 0.05f
        addRoundRect(RoundRect(Rect(center.x - palmW / 2 - r * 0.24f * scale, top, center.x - palmW * 0.3f, top + palmH * 0.5f), CornerRadius(palmH * 0.25f)))
    }
    shape = Path.combine(PathOperation.Union, shape, thumb)
    drawPath(shape, Black, alpha = alpha)
    drawPath(shape, White, alpha = alpha, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
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
