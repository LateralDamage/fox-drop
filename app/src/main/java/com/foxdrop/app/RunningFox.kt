package com.foxdrop.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

private const val DROP_MS = 1300L
private const val LAND_MS = 220L
private const val RUN_MS = 1100L
private const val TOTAL_MS = DROP_MS + LAND_MS + RUN_MS
private const val FADE_MS = 250L

private val Orange = Color(0xFFF26B1D)
private val DarkOrange = Color(0xFFC94F0E)
private val Cream = Color(0xFFFFF4E6)
private val Sock = Color(0xFF3A1A0C)

/**
 * Plays when the app opens or Kollin taps a notification: a fox parachutes in, lands and sprints
 * off the screen, then [onDone] fires and the app shows the tab the alert was about. Tapping skips it.
 */
@Composable
fun RunningFoxOverlay(message: String, onDone: () -> Unit) {
    // Timed off the frame clock by hand rather than with Animatable: Compose animations obey the
    // system animation scale, and with "Remove animations" on they finish instantly and the fox never shows.
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (elapsed < TOTAL_MS + FADE_MS) {
            elapsed = withFrameMillis { it } - start
        }
        onDone()
    }
    val ms = elapsed.coerceAtMost(TOTAL_MS).toFloat()
    val fade = 1f - ((elapsed - TOTAL_MS).toFloat() / FADE_MS).coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxSize().alpha(fade).background(Night),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val scale = size.width / 420f
            val ground = size.height * 0.55f
            val landX = size.width * 0.38f
            val twoPi = 2f * PI.toFloat()

            drawLine(Color.White.copy(alpha = 0.12f), Offset(0f, ground + 44f * scale), Offset(size.width, ground + 44f * scale), 3f * scale)

            if (ms < DROP_MS) {
                // Drop: eases out as the chute catches air, swinging like a pendulum that settles.
                val d = ms / DROP_MS
                val ease = 1f - (1f - d) * (1f - d)
                val x = landX - (1f - ease) * 30f * scale
                val y = -80f * scale + ease * (ground + 80f * scale)
                val sway = sin(d * twoPi * 1.5f) * 9f * (1f - d * 0.7f)
                translate(x, y) {
                    rotate(sway, pivot = Offset(0f, -170f * scale)) {
                        drawChute(scale, 1f)
                        drawFox(scale, d * twoPi * 1.6f, swingAmp = 12f)
                    }
                }
            } else {
                val since = ms - DROP_MS
                // The chute lets go, tips back and fades while the fox gets moving.
                val c = (since / (LAND_MS + 300f)).coerceIn(0f, 1f)
                if (c < 1f) translate(landX - c * 50f * scale, ground + c * 10f * scale) {
                    rotate(-35f * c, pivot = Offset(0f, -22f * scale)) { drawChute(scale, 1f - c) }
                }
                if (since < LAND_MS) {
                    // Touchdown: a quick crouch and a puff of dust either side.
                    val l = since / LAND_MS
                    for (side in listOf(-1f, 1f)) {
                        drawCircle(Color.White.copy(alpha = 0.35f * (1f - l)), radius = (6f + l * 16f) * scale, center = Offset(landX + side * (30f + l * 20f) * scale, ground + 40f * scale))
                    }
                    translate(landX, ground + sin(l * PI.toFloat()) * 6f * scale) { drawFox(scale, 0f, swingAmp = 0f) }
                } else {
                    // Run: starts at a jog and speeds up; four strides off the right edge.
                    val r = (since - LAND_MS) / RUN_MS
                    fun along(r: Float) = r * (0.35f + 0.65f * r)
                    val span = size.width - landX + 160f * scale
                    val phase = along(r) * twoPi * 4f
                    for (i in 1..4) {
                        val back = r - i * 0.04f
                        if (back <= 0f) continue
                        val a = (0.35f - i * 0.07f).coerceAtLeast(0f)
                        drawCircle(Color.White.copy(alpha = a), radius = (5f + i * 3f) * scale, center = Offset(landX + along(back) * span - 60f * scale, ground + 40f * scale - i * 4f * scale))
                    }
                    val bob = -abs(sin(phase)) * 8f * scale
                    translate(landX + along(r) * span, ground + bob) { drawFox(scale, phase) }
                }
            }
        }
        Text(
            message,
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.align(Alignment.Center).padding(top = 260.dp),
        )
    }
}

/** The parachute, in the fox's own coordinates: canopy overhead, lines meeting at its back. */
private fun DrawScope.drawChute(s: Float, alpha: Float) {
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    val hem = listOf(-80f, -48f, -16f, 16f, 48f, 80f)
    for (x in hem) drawLine(Color.White.copy(alpha = 0.6f * alpha), p(x, -130f), p(0f, -22f), 1.5f * s)
    val canopy = Path().apply {
        moveTo(-80f * s, -130f * s)
        cubicTo(-80f * s, -215f * s, 80f * s, -215f * s, 80f * s, -130f * s)
        for (i in hem.size - 2 downTo 0) quadraticTo((hem[i] + 16f) * s, -144f * s, hem[i] * s, -130f * s)
        close()
    }
    drawPath(canopy, Orange.copy(alpha = alpha))
    // Two cream panels, each running from the top of the dome down to its own scallop.
    for (x in listOf(-48f, 16f)) {
        val panel = Path().apply {
            moveTo(0f, -194f * s)
            quadraticTo(x * 1.1f * s, -190f * s, x * s, -130f * s)
            quadraticTo((x + 16f) * s, -144f * s, (x + 32f) * s, -130f * s)
            quadraticTo((x + 32f) * 1.1f * s, -190f * s, 0f, -194f * s)
            close()
        }
        drawPath(panel, Cream.copy(alpha = alpha))
    }
}

/** A side-on fox facing right, origin at the middle of its body. [swingAmp] is how far the legs swing, in degrees. */
private fun DrawScope.drawFox(s: Float, phase: Float, swingAmp: Float = 38f) {
    fun p(x: Float, y: Float) = Offset(x * s, y * s)

    // Legs: the far pair first so the body covers their tops. Diagonal pairs move together (a trot).
    fun leg(hipX: Float, hipY: Float, swing: Float, far: Boolean) {
        val color = if (far) DarkOrange else Orange
        rotate(swing, pivot = p(hipX, hipY)) {
            drawLine(color, p(hipX, hipY), p(hipX, hipY + 26f), 9f * s, StrokeCap.Round)
            drawLine(Sock, p(hipX, hipY + 22f), p(hipX, hipY + 38f), 8f * s, StrokeCap.Round)
        }
    }
    val swing = sin(phase) * swingAmp
    leg(-26f, 6f, swing, far = true)
    leg(28f, 6f, -swing, far = true)

    // Tail, streaming out behind and flicking with the stride.
    val flick = sin(phase + 1f) * 6f
    val tail = Path().apply {
        moveTo(-34f * s, -6f * s)
        cubicTo(-70f * s, (-30f + flick) * s, -110f * s, (-20f + flick) * s, -120f * s, (-4f + flick) * s)
        cubicTo(-100f * s, (8f + flick) * s, -66f * s, 14f * s, -34f * s, 8f * s)
        close()
    }
    drawPath(tail, Orange)
    drawCircle(Cream, radius = 11f * s, center = p(-114f, -4f + flick))

    // Body and cream belly.
    drawOval(Orange, topLeft = p(-44f, -20f), size = Size(90f * s, 38f * s))
    drawOval(Cream, topLeft = p(-18f, 2f), size = Size(52f * s, 16f * s))

    leg(-22f, 8f, -swing, far = false)
    leg(32f, 8f, swing, far = false)

    // Head: skull, pointed snout, cream cheek, ears, eye, nose.
    drawCircle(Orange, radius = 19f * s, center = p(50f, -20f))
    val snout = Path().apply {
        moveTo(56f * s, -32f * s); lineTo(88f * s, -14f * s); lineTo(56f * s, -6f * s); close()
    }
    drawPath(snout, Orange)
    val cheek = Path().apply {
        moveTo(40f * s, -12f * s); lineTo(86f * s, -13f * s); lineTo(56f * s, -2f * s); close()
    }
    drawPath(cheek, Cream)
    for ((x, c) in listOf(38f to DarkOrange, 50f to Orange)) {
        val ear = Path().apply {
            moveTo(x * s, -32f * s); lineTo((x + 4f) * s, -56f * s); lineTo((x + 14f) * s, -34f * s); close()
        }
        drawPath(ear, c)
    }
    drawCircle(Sock, radius = 3.2f * s, center = p(60f, -24f))
    drawCircle(Color.White, radius = 1.1f * s, center = p(61f, -25f))
    drawCircle(Sock, radius = 3.6f * s, center = p(88f, -14f))
}
