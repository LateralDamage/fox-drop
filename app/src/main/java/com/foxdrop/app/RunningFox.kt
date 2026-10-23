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
import androidx.compose.ui.graphics.drawscope.Stroke
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
private const val SAIL_MS = 2000L
private const val TOTAL_MS = DROP_MS + LAND_MS + RUN_MS
private const val PIRATE_MS = DROP_MS + LAND_MS + SAIL_MS
private const val FADE_MS = 250L

private val Orange = Color(0xFFF26B1D)
private val DarkOrange = Color(0xFFC94F0E)
private val Cream = Color(0xFFFFF4E6)
private val Sock = Color(0xFF3A1A0C)
private val Straw = Color(0xFFE8C25A)
private val StrawDark = Color(0xFFB8902F)
private val HatBand = Color(0xFFD62828)
private val Hull = Color(0xFF6B3D1F)
private val HullDark = Color(0xFF4A2712)
private val Gold = Color(0xFFE8C02A)
private val Porthole = Color(0xFF24140A)
private val Flag = Color(0xFF3A3848)
private val FarSea = Color(0xFF10285A)
private val SeaTop = Color(0xFF2A5C9A)
private val Sea = Color(0xFF163A6E)

/**
 * Plays when the app opens or Kollin taps a notification: a fox parachutes in, lands and sprints
 * off the screen, then [onDone] fires and the app shows the tab the alert was about. Tapping skips it.
 * With [pirate] (pirate season, see Prefs.pirateNow) it wears a straw hat, lands on a ship and sails away.
 */
@Composable
fun RunningFoxOverlay(message: String, pirate: Boolean = false, onDone: () -> Unit) {
    // Timed off the frame clock by hand rather than with Animatable: Compose animations obey the
    // system animation scale, and with "Remove animations" on they finish instantly and the fox never shows.
    var elapsed by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (elapsed < (if (pirate) PIRATE_MS else TOTAL_MS) + FADE_MS) {
            elapsed = withFrameMillis { it } - start
        }
        onDone()
    }
    val total = if (pirate) PIRATE_MS else TOTAL_MS
    val ms = elapsed.coerceAtMost(total).toFloat()
    val fade = 1f - ((elapsed - total).toFloat() / FADE_MS).coerceIn(0f, 1f)
    Box(
        Modifier.fillMaxSize().alpha(fade).background(Night),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val scale = size.width / 420f
            val ground = size.height * 0.55f
            val landX = size.width * 0.38f
            val twoPi = 2f * PI.toFloat()

            if (pirate) {
                drawPirateOpening(ms, elapsed.toFloat(), scale, ground, landX)
                return@Canvas
            }
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
private fun DrawScope.drawFox(s: Float, phase: Float, swingAmp: Float = 38f, hat: Boolean = false) {
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
    if (hat) drawStrawHat(s)
}

/** A straw hat with a red band, tipped back on the fox's head (head centre 50,-20). */
private fun DrawScope.drawStrawHat(s: Float) {
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    rotate(-10f, pivot = p(50f, -38f)) {
        drawArc(Straw, startAngle = 180f, sweepAngle = 180f, useCenter = true, topLeft = p(37f, -53f), size = Size(30f * s, 30f * s))
        drawRect(HatBand, topLeft = p(37f, -43f), size = Size(30f * s, 5f * s))
        drawOval(Straw, topLeft = p(21f, -42.5f), size = Size(62f * s, 11f * s))
        drawOval(StrawDark, topLeft = p(22f, -41.8f), size = Size(60f * s, 9.6f * s), style = Stroke(1.2f * s))
        for ((top, bottom) in listOf(p(45f, -51f) to p(44f, -43f), p(52f, -53f) to p(52f, -43f), p(59f, -51f) to p(60f, -43f))) {
            drawLine(StrawDark, top, bottom, 1.2f * s)
        }
    }
}

/**
 * Pirate season: the fox (in its straw hat) parachutes onto a ship rocking on the swell, the chute
 * lets go, and the ship sails off the right edge with the fox on deck. [t] keeps running through the
 * fade so the sea never freezes. The ship is drawn in the fox's coordinates, so the deck is at y = 44.
 */
private fun DrawScope.drawPirateOpening(ms: Float, t: Float, s: Float, ground: Float, landX: Float) {
    val pi = PI.toFloat()
    val twoPi = 2f * pi
    val shipY = ground + sin(t / 700f * pi) * 4f * s
    val rock = sin(t / 900f * pi) * 2f
    val sea = ground + 90f * s
    val since = ms - DROP_MS - LAND_MS
    val r = if (since > 0f) since / SAIL_MS else 0f
    fun along(r: Float) = r * r * (0.25f + 0.75f * r)
    val travel = size.width - landX + 260f * s
    val shipX = landX + along(r) * travel

    drawCircle(Cream.copy(alpha = 0.9f), radius = 22f * s, center = Offset(size.width * 0.82f, size.height * 0.16f))
    drawCircle(Night, radius = 18f * s, center = Offset(size.width * 0.82f + 9f * s, size.height * 0.16f - 5f * s))
    drawWave(s, sea - 10f * s, t / 600f, 4f, FarSea)

    fun onShip(block: DrawScope.() -> Unit) = translate(shipX, shipY) { rotate(rock, pivot = Offset(0f, 60f * s)) { block() } }
    onShip { drawRigging(s, t) }
    if (ms >= DROP_MS) onShip {
        val landed = ms - DROP_MS
        val l = (landed / LAND_MS).coerceAtMost(1f)
        var dip = 0f
        if (landed < LAND_MS) {
            for (side in listOf(-1f, 1f)) {
                drawCircle(Color.White.copy(alpha = 0.35f * (1f - l)), radius = (6f + l * 16f) * s, center = Offset(side * (30f + l * 20f) * s, 40f * s))
            }
            dip = sin(l * pi) * 6f * s
        }
        translate(0f, dip) { drawFox(s, if (since > 0f) t / 260f else 0f, swingAmp = if (since > 0f) 3f else 0f, hat = true) }
    }
    onShip { drawHull(s) }
    if (r > 0f) for (i in 1..5) {
        val back = r - i * 0.035f
        if (back <= 0f) continue
        drawCircle(Color.White.copy(alpha = (0.3f - i * 0.05f).coerceAtLeast(0f)), radius = (6f + i * 4f) * s, center = Offset(landX + along(back) * travel - 190f * s, sea + 4f * s))
    }
    drawWave(s, sea, t / 400f, 6f, SeaTop)
    drawWave(s, sea + 14f * s, t / 300f + 2f, 5f, Sea)

    // The drop goes over everything, so the chute reads in front of the sail.
    if (ms < DROP_MS) {
        val d = ms / DROP_MS
        val ease = 1f - (1f - d) * (1f - d)
        val sway = sin(d * twoPi * 1.5f) * 9f * (1f - d * 0.7f)
        translate(landX - (1f - ease) * 30f * s, -80f * s + ease * (shipY + 80f * s)) {
            rotate(sway, pivot = Offset(0f, -170f * s)) {
                drawChute(s, 1f)
                drawFox(s, d * twoPi * 1.6f, swingAmp = 12f, hat = true)
            }
        }
    } else {
        val c = ((ms - DROP_MS) / (LAND_MS + 300f)).coerceIn(0f, 1f)
        if (c < 1f) translate(landX - c * 50f * s, shipY + c * 10f * s) {
            rotate(-35f * c, pivot = Offset(0f, -22f * s)) { drawChute(s, 1f - c) }
        }
    }
}

/** A band of sea from [y] down to the bottom of the screen, its top edge a rolling sine. */
private fun DrawScope.drawWave(s: Float, y: Float, phase: Float, amp: Float, color: Color) {
    val path = Path().apply {
        moveTo(0f, size.height)
        var x = 0f
        while (x <= size.width + 8f) {
            lineTo(x, y + sin(x / (38f * s) + phase) * amp * s)
            x += 8f
        }
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, color)
}

/** A closed shape through x,y pairs in fox units, shifted down by [dy]. */
private fun shape(s: Float, dy: Float, vararg xy: Float) = Path().apply {
    moveTo(xy[0] * s, (xy[1] + dy) * s)
    for (i in 2 until xy.size step 2) lineTo(xy[i] * s, (xy[i + 1] + dy) * s)
    close()
}

/** Mast, yard, a wind-filled sail with a fox-head mark, and a flag with crossbones behind a fox skull. */
private fun DrawScope.drawRigging(s: Float, t: Float) {
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    drawLine(HullDark, p(-40f, 44f), p(-40f, -250f), 7f * s)
    drawLine(HullDark, p(-115f, -222f), p(35f, -222f), 5f * s)
    val billow = 14f + sin(t / 260f) * 4f
    val sail = Path().apply {
        moveTo(-112f * s, -220f * s)
        lineTo(32f * s, -220f * s)
        quadraticTo((32f + billow * 2f) * s, -158f * s, 38f * s, -96f * s)
        lineTo(-118f * s, -96f * s)
        quadraticTo((-118f + billow * 2f) * s, -158f * s, -112f * s, -220f * s)
        close()
    }
    drawPath(sail, Cream)
    drawPath(shape(s, 0f, -58f, -170f, -54f, -188f, -44f, -176f, -28f, -176f, -18f, -188f, -14f, -170f, -36f, -140f), Orange)

    val w = sin(t / 180f) * 5f
    val flag = Path().apply {
        moveTo(-37f * s, -250f * s)
        quadraticTo(-10f * s, (-256f + w) * s, 16f * s, (-248f + w) * s)
        lineTo(16f * s, (-222f + w) * s)
        quadraticTo(-10f * s, (-230f + w) * s, -37f * s, -226f * s)
        close()
    }
    drawPath(flag, Flag)
    val o = w / 2f
    for ((a, b) in listOf(Offset(-20f, -247f) to Offset(6f, -229f), Offset(6f, -247f) to Offset(-20f, -229f))) {
        drawLine(Color.White, p(a.x, a.y + o), p(b.x, b.y + o), 2.6f * s, StrokeCap.Round)
        val len = (b - a).getDistance()
        val dx = (b.x - a.x) / len
        val dy = (b.y - a.y) / len
        // Two knobs at each end of a bone.
        for ((end, k) in listOf(a to -1f, b to 1f)) for (side in listOf(-1f, 1f)) {
            drawCircle(Color.White, radius = 1.9f * s, center = p(end.x + k * dx * 0.8f - side * dy * 1.7f, end.y + o + k * dy * 0.8f + side * dx * 1.7f))
        }
    }
    drawPath(shape(s, o, -17f, -243f, -15f, -250f, -10f, -245f, -4f, -245f, 1f, -250f, 3f, -243f, -7f, -231f), Cream)
}

/** The hull, bow to the right, with gold trim and portholes. */
private fun DrawScope.drawHull(s: Float) {
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    val hull = Path().apply {
        moveTo(-185f * s, 24f * s)
        lineTo(-135f * s, 24f * s)
        lineTo(-135f * s, 44f * s)
        lineTo(150f * s, 44f * s)
        lineTo(196f * s, 26f * s)
        quadraticTo(180f * s, 80f * s, 140f * s, 104f * s)
        lineTo(-150f * s, 104f * s)
        quadraticTo(-178f * s, 70f * s, -185f * s, 24f * s)
        close()
    }
    drawPath(hull, Hull)
    for (y in listOf(62f, 82f)) drawLine(HullDark, p(-178f, y), p(178f, y), 2f * s)
    drawLine(Gold, p(-185f, 24f), p(-135f, 24f), 3f * s)
    drawLine(Gold, p(-135f, 44f), p(150f, 44f), 3f * s)
    drawLine(Gold, p(150f, 44f), p(196f, 26f), 3f * s)
    for (x in listOf(-90f, -30f, 30f, 90f)) {
        drawCircle(Gold, radius = 7f * s, center = p(x, 72f))
        drawCircle(Porthole, radius = 4.6f * s, center = p(x, 72f))
    }
}
