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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

private const val DROP_MS = 1300L
private const val LAND_MS = 220L
private const val RUN_MS = 1100L
private const val TOTAL_MS = DROP_MS + LAND_MS + RUN_MS
// Pirate season (manga): eye close-up, drop and dive, impact, sail away.
private const val EYE_MS = 850L
private const val MDROP_MS = 1100L
private const val CUT = 0.45f
private const val IMPACT_MS = 420L
private const val MSAIL_MS = 2300L
private const val PIRATE_MS = EYE_MS + MDROP_MS + IMPACT_MS + MSAIL_MS
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
private val Paper = Color(0xFFF7F0DF)
private val Ink = Color(0xFF15111A)
private val Burst = Color(0xFFFFDDB0)
private val MangaFarSea = Color(0xFF3B5B8C)
private val MangaSea = Color(0xFF1E3358)
private val MangaDeepSea = Color(0xFF13223D)

/**
 * Plays when the app opens or Kollin taps a notification: a fox parachutes in, lands and sprints
 * off the screen, then [onDone] fires and the app shows the tab the alert was about. Tapping skips it.
 * With [pirate] (pirate season, see Prefs.pirateNow) it plays the manga version: straw hat, a dive onto a ship, ドン!!
 */
@Composable
fun RunningFoxOverlay(message: String, pirate: Boolean = false, onDone: () -> Unit) {
    // Timed off the frame clock by hand rather than with Animatable: Compose animations obey the
    // system animation scale, and with "Remove animations" on they finish instantly and the fox never shows.
    var elapsed by remember { mutableLongStateOf(0L) }
    val tm = rememberTextMeasurer()
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
                drawMangaOpening(ms, elapsed.toFloat(), scale, ground, landX, tm)
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
        if (!pirate) Text(
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
 * Pirate season, manga style and over the top. Four beats:
 *  1. EYE: an extreme close-up of the fox's eye, radial speed lines, ゴゴゴ, a glint, then a slash cut.
 *  2. DROP: the fox floats down under its chute, cuts loose (シュッ!) and dives at the ship.
 *  3. IMPACT: one inverted frame, screen shake, a starburst, spray and a huge ドン!!
 *  4. SAIL: a turning sunburst, FOX DROP! slams in, and the ship sails off under ドドドド.
 * [t] keeps running through the fade so nothing freezes. The ship is in fox coordinates (deck at y = 44).
 */
private fun DrawScope.drawMangaOpening(ms: Float, t: Float, s: Float, ground: Float, landX: Float, tm: TextMeasurer) {
    val w = size.width
    val h = size.height
    val pi = PI.toFloat()
    val twoPi = 2f * pi
    val flicker = (t / 70f).toInt() // speed lines redraw at ~14 fps, like inked frames

    if (ms < EYE_MS) {
        val e = ms / EYE_MS
        val eye = Offset(w * 0.5f, h * 0.42f)
        drawRect(Paper)
        radialLines(eye, 130f * s, 96, flicker, Ink)
        // Push in on the eye: fox units scaled up so its eye (60,-24) sits at the centre.
        val big = s * (5.2f + e * 0.9f)
        translate(eye.x - 60f * big, eye.y + 24f * big) {
            drawFox(big, 0f, swingAmp = 0f, hat = true)
            drawMangaEye(big)
        }
        if (e in 0.42f..0.8f) {
            val g = (e - 0.42f) / 0.38f
            sparkle(eye + Offset(2.2f * big, -2.6f * big), sin(g * pi) * 30f * s, g * 90f)
        }
        for (k in 0..2) if (e > 0.08f + k * 0.14f) {
            val j = jitter(flicker + k, 3f * s)
            sfx(tm, "ゴ", Offset(w * 0.86f, h * (0.18f + k * 0.11f)) + j, 50f * s, 10f, 1f, Ink, Paper)
            sfx(tm, "ゴ", Offset(w * 0.13f, h * (0.62f + k * 0.1f)) - j, 42f * s, -8f, 1f, Ink, Paper)
        }
        if (e > 0.82f) {
            val k = (e - 0.82f) / 0.18f
            val a = Offset(-0.1f * w, 0.12f * h)
            val b = Offset(1.1f * w, 0.8f * h)
            drawLine(Color.White, a, a + (b - a) * k, 26f * s, StrokeCap.Round)
            drawLine(Ink, a, a + (b - a) * k, 12f * s, StrokeCap.Round)
        }
        return
    }

    val m = ms - EYE_MS
    val impact = m - MDROP_MS
    val k = (impact / IMPACT_MS).coerceIn(0f, 1f)
    val inImpact = impact in 0f..IMPACT_MS.toFloat()
    val sail = m - MDROP_MS - IMPACT_MS
    val frame = (t / 30f).toInt()
    val shake = if (inImpact) jitter(frame, (1f - k) * (1f - k) * 18f * s) else if (sail in 0f..180f) jitter(frame, (1f - sail / 180f) * 6f * s) else Offset.Zero
    val inverted = impact in 0f..90f

    val sea = ground + 90f * s
    val dip = if (inImpact) sin(k * pi) * (1f - k) * 30f * s else 0f
    val shipY = ground + sin(t / 700f * pi) * 4f * s + dip
    val rock = sin(t / 900f * pi) * 2f + if (inImpact) sin(k * pi * 3f) * (1f - k) * 6f else 0f
    val moving = sail - 700f
    fun along(r: Float) = r * r * (0.25f + 0.75f * r)
    val r = if (moving > 0f) moving / (MSAIL_MS - 700f) else 0f
    val travel = w - landX + 260f * s
    val shipX = landX + along(r) * travel
    val hit = Offset(landX, ground - 30f * s)

    translate(shake.x, shake.y) {
        val bleed = 40f * s
        drawRect(if (inverted) Ink else Paper, topLeft = Offset(-bleed, -bleed), size = Size(w + 2 * bleed, h + 2 * bleed))
        when {
            inverted -> radialLines(hit, 110f * s, 80, flicker, Color.White)
            m < MDROP_MS -> {
                val dive = ((m / MDROP_MS - CUT) / (1f - CUT)).coerceIn(0f, 1f)
                streaks(vertical = true, t = t, speed = 1.2f + dive * 3f, count = 26 + (dive * 20).toInt(), color = Ink.copy(alpha = 0.35f + dive * 0.5f), s = s)
            }
            inImpact -> radialLines(hit, 120f * s, 80, flicker, Ink.copy(alpha = 1f - k))
        }
        if (sail >= 0f) {
            sunburst(hit, t / 45f, Burst.copy(alpha = (sail / 220f).coerceAtMost(1f)))
            if (moving > 0f) streaks(vertical = false, t = t, speed = 1f + r * 2.5f, count = 22, color = Ink.copy(alpha = 0.5f), s = s)
        }
        if (inImpact) {
            spikyStar(hit + Offset(0f, 40f * s), (90f + k * 120f) * s, (50f + k * 60f) * s, 16, Gold.copy(alpha = 1f - k), frame)
            spikyStar(hit + Offset(0f, 40f * s), (55f + k * 70f) * s, (30f + k * 40f) * s, 12, Color.White.copy(alpha = 1f - k), frame + 9)
        }

        drawWave(s, sea - 10f * s, t / 600f, 4f, MangaFarSea)
        fun onShip(block: DrawScope.() -> Unit) = translate(shipX, shipY) { rotate(rock, pivot = Offset(0f, 60f * s)) { block() } }
        onShip { drawRigging(s, t, outline = true) }
        if (m >= MDROP_MS) onShip {
            // Victory hops before the ship gets under way, then a steady sea-legs sway.
            val hop = if (sail in 0f..700f) -abs(sin(sail / 700f * twoPi)) * 14f * s else 0f
            translate(0f, hop) { drawFox(s, if (moving > 0f) t / 260f else 0f, swingAmp = if (moving > 0f) 3f else 0f, hat = true) }
        }
        onShip { drawHull(s) }
        if (inImpact) for (i in 0 until 14) {
            // Spray thrown up both sides of the hull.
            val side = if (i % 2 == 0) -1f else 1f
            val a = (20f + rnd(i * 5) * 50f) / 180f * pi
            val dist = (60f + rnd(i * 7 + 1) * 140f) * s * k
            val c = Offset(landX + side * (150f * s + cos(a) * dist), sea - sin(a) * dist * 1.4f + k * k * 120f * s)
            drawCircle(Color.White.copy(alpha = 1f - k), (5f + rnd(i) * 7f) * s, c)
            drawCircle(Ink.copy(alpha = 1f - k), (5f + rnd(i) * 7f) * s, c, style = Stroke(1.6f * s))
        }
        if (r > 0f) for (i in 1..5) {
            val back = r - i * 0.035f
            if (back <= 0f) continue
            drawCircle(Color.White.copy(alpha = (0.5f - i * 0.08f).coerceAtLeast(0f)), radius = (6f + i * 4f) * s, center = Offset(landX + along(back) * travel - 190f * s, sea + 4f * s))
        }
        drawWave(s, sea, t / 400f, 6f, MangaSea)
        drawFoam(s, sea, t / 400f, 6f)
        drawWave(s, sea + 16f * s, t / 300f + 2f, 5f, MangaDeepSea)
        drawFoam(s, sea + 16f * s, t / 300f + 2f, 5f)

        if (m < MDROP_MS) {
            val d = m / MDROP_MS
            val top = h * 0.22f
            if (d < CUT) {
                // Floating down, swinging under the chute.
                val f = d / CUT
                val sway = sin(f * twoPi * 1.2f) * 10f
                translate(landX - (1f - f) * 30f * s, -140f * s + f * (top + 140f * s)) {
                    rotate(sway, pivot = Offset(0f, -170f * s)) {
                        drawChute(s, 1f)
                        drawFox(s, f * twoPi * 1.6f, swingAmp = 12f, hat = true)
                    }
                }
            } else {
                // Cut loose: the chute tumbles away and the fox dives, accelerating, with a ghost trail.
                val v = (d - CUT) / (1f - CUT)
                translate(landX - v * 140f * s, top - v * 300f * s) {
                    rotate(-70f * v, pivot = Offset(0f, -22f * s)) { drawChute(s, 1f - v) }
                }
                for (g in 3 downTo 1) {
                    val gv = (v - g * 0.07f).coerceAtLeast(0f)
                    translate(landX, top + gv * gv * (shipY - top)) { faded(0.22f / g) { drawFox(s, 0f, swingAmp = 0f, hat = true) } }
                }
                translate(landX, top + v * v * (shipY - top)) { drawFox(s, 0f, swingAmp = 0f, hat = true) }
                if (v < 0.55f) sfx(tm, "シュッ!", Offset(landX + 110f * s, top - 30f * s), 34f * s, -12f, 1f, Ink, Paper, alpha = 1f - v / 0.55f)
            }
        }
    }

    // Sound effects and the title sit on top of the shake so they stay readable.
    if (impact >= 0f && sail < 320f) {
        val fade = if (sail > 0f) 1f - sail / 320f else 1f
        sfx(tm, "ドン!!", Offset(w * 0.5f, h * 0.27f), 104f * s, -10f, pop((impact / 170f).coerceAtMost(1f)), Gold, Ink, alpha = fade, halo = true)
    }
    if (sail >= 0f) {
        sfx(tm, "FOX DROP!", Offset(w * 0.5f, h * 0.17f), 60f * s, -6f, pop((sail / 230f).coerceAtMost(1f)), Orange, Ink, halo = true)
        for (i in 0..3) if (sail > 250f + i * 110f) {
            sfx(tm, "ド", Offset(w * (0.2f + i * 0.2f), h * 0.88f) + jitter(flicker + i * 3, 3f * s), 40f * s, -8f, 1f, Ink, Paper)
        }
    }
}

/** A manga eye over the fox's own (centre 60,-24): almond white, big dark iris with highlights, a hard brow. */
private fun DrawScope.drawMangaEye(s: Float) {
    fun p(x: Float, y: Float) = Offset(x * s, y * s)
    val white = Path().apply {
        moveTo(51f * s, -23f * s)
        quadraticTo(58f * s, -31.5f * s, 69f * s, -27f * s)
        quadraticTo(61f * s, -18.5f * s, 51f * s, -23f * s)
        close()
    }
    drawPath(white, Color.White)
    drawCircle(Sock, radius = 3.9f * s, center = p(61f, -25f))
    drawCircle(Color(0xFF7A2A08), radius = 2.2f * s, center = p(61f, -24.6f))
    drawCircle(Ink, radius = 1.3f * s, center = p(61f, -24.8f))
    drawCircle(Color.White, radius = 1.15f * s, center = p(62.2f, -26.4f))
    drawCircle(Color.White, radius = 0.5f * s, center = p(59.8f, -23.6f))
    drawPath(white, Ink, style = Stroke(0.9f * s, join = StrokeJoin.Round))
    drawLine(Ink, p(49f, -34.5f), p(68f, -30.5f), 2.6f * s, StrokeCap.Round)
}

private fun rnd(i: Int): Float {
    val x = sin(i * 12.9898f + 78.233f) * 43758.547f
    return x - floor(x)
}

private fun jitter(seed: Int, amp: Float) = Offset((rnd(seed * 2) - 0.5f) * 2f * amp, (rnd(seed * 2 + 1) - 0.5f) * 2f * amp)

/** Overshoot for a slammed-in word: starts big, punches past 1, settles. [k] runs 0..1. */
private fun pop(k: Float) = if (k >= 1f) 1f else 1f + 1.9f * (1f - k) * (1f - k) * (1f - k) - 0.14f * sin(k * PI.toFloat())

/** Thin wedges converging on [c], starting a jittered [inner] radius out; [seed] reshuffles them. */
private fun DrawScope.radialLines(c: Offset, inner: Float, count: Int, seed: Int, color: Color) {
    val far = size.width + size.height
    val twoPi = 2f * PI.toFloat()
    for (i in 0 until count) {
        val a = (i + rnd(seed * 131 + i) * 0.8f) / count * twoPi
        val r0 = inner * (0.7f + rnd(seed * 71 + i * 3) * 0.7f)
        val half = 0.003f + rnd(seed * 17 + i * 7) * 0.011f
        val path = Path().apply {
            moveTo(c.x + cos(a) * r0, c.y + sin(a) * r0)
            lineTo(c.x + cos(a - half) * far, c.y + sin(a - half) * far)
            lineTo(c.x + cos(a + half) * far, c.y + sin(a + half) * far)
            close()
        }
        drawPath(path, color)
    }
}

/** Motion streaks: rushing up the screen ([vertical], a fall) or off to the left (sailing right). */
private fun DrawScope.streaks(vertical: Boolean, t: Float, speed: Float, count: Int, color: Color, s: Float) {
    val across = if (vertical) size.width else size.height
    val run = if (vertical) size.height else size.width
    for (i in 0 until count) {
        val x = rnd(i * 3 + 1) * across
        val len = (70f + rnd(i * 5 + 2) * 240f) * s
        val v = speed * (0.6f + rnd(i * 11) * 0.8f) * s
        val span = run + len
        val pos = (rnd(i * 13) * span + t * v) % span - len
        val width = (1f + rnd(i * 17) * 2.5f) * s
        if (vertical) drawLine(color, Offset(x, run - pos), Offset(x, run - pos + len), width)
        else drawLine(color, Offset(run - pos, x), Offset(run - pos + len, x), width)
    }
}

/** Alternating rays from [c] out past the corners, turned by [deg]. */
private fun DrawScope.sunburst(c: Offset, deg: Float, color: Color) {
    val far = size.width + size.height
    val n = 16
    val step = 2f * PI.toFloat() / n
    val off = deg / 180f * PI.toFloat()
    for (i in 0 until n) {
        val a = off + i * step
        val path = Path().apply {
            moveTo(c.x, c.y)
            lineTo(c.x + cos(a) * far, c.y + sin(a) * far)
            lineTo(c.x + cos(a + step / 2f) * far, c.y + sin(a + step / 2f) * far)
            close()
        }
        drawPath(path, color)
    }
}

/** A jagged impact star with [points] spikes; [seed] wobbles the spike lengths frame to frame. */
private fun DrawScope.spikyStar(c: Offset, outer: Float, inner: Float, points: Int, color: Color, seed: Int) {
    val step = PI.toFloat() / points
    val path = Path()
    for (i in 0 until points * 2) {
        val rad = if (i % 2 == 0) outer * (0.8f + rnd(seed * 31 + i) * 0.4f) else inner
        val a = i * step
        val x = c.x + cos(a) * rad
        val y = c.y + sin(a) * rad
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
}

/** A four-point glint with an ink edge. */
private fun DrawScope.sparkle(c: Offset, r: Float, deg: Float) {
    if (r <= 0f) return
    val path = Path().apply {
        moveTo(0f, -r); lineTo(r * 0.18f, -r * 0.18f); lineTo(r, 0f); lineTo(r * 0.18f, r * 0.18f)
        lineTo(0f, r); lineTo(-r * 0.18f, r * 0.18f); lineTo(-r, 0f); lineTo(-r * 0.18f, -r * 0.18f); close()
    }
    translate(c.x, c.y) {
        rotate(deg, pivot = Offset.Zero) {
            drawPath(path, Ink, style = Stroke(r * 0.12f, join = StrokeJoin.Round))
            drawPath(path, Color.White)
        }
    }
}

/** White foam line along the top of a [drawWave] band. */
private fun DrawScope.drawFoam(s: Float, y: Float, phase: Float, amp: Float) {
    val path = Path().apply {
        var x = 0f
        moveTo(0f, y + sin(phase) * amp * s)
        while (x <= size.width + 8f) {
            lineTo(x, y + sin(x / (38f * s) + phase) * amp * s)
            x += 8f
        }
    }
    drawPath(path, Color.White, style = Stroke(2.2f * s, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/**
 * Manga lettering: [text] centred on [c] at [px] pixels, turned [deg] and scaled by [scale], filled with
 * [fill] inside a thick [ink] edge. [halo] adds an outer white edge so it reads on anything.
 */
private fun DrawScope.sfx(
    tm: TextMeasurer, text: String, c: Offset, px: Float, deg: Float, scale: Float,
    fill: Color, ink: Color, alpha: Float = 1f, halo: Boolean = false,
) {
    if (alpha <= 0f) return
    val layout = tm.measure(text, TextStyle(fontSize = px.toSp(), fontWeight = FontWeight.Black, fontStyle = FontStyle.Italic, letterSpacing = 1.sp))
    val topLeft = c - Offset(layout.size.width / 2f, layout.size.height / 2f)
    withTransform({ rotate(deg, c); scale(scale, scale, c) }) {
        if (halo) drawText(layout, Color.White, topLeft, alpha = alpha, drawStyle = Stroke(px * 0.34f, join = StrokeJoin.Round))
        drawText(layout, ink, topLeft, alpha = alpha, drawStyle = Stroke(px * 0.17f, join = StrokeJoin.Round))
        drawText(layout, fill, topLeft, alpha = alpha)
    }
}

/** Draws [block] at [alpha] as one layer, so overlapping parts don't show through each other. */
private fun DrawScope.faded(alpha: Float, block: DrawScope.() -> Unit) {
    val c = drawContext.canvas
    c.saveLayer(Rect(-size.width * 2f, -size.height * 2f, size.width * 3f, size.height * 3f), Paint().apply { this.alpha = alpha })
    block()
    c.restore()
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
private fun DrawScope.drawRigging(s: Float, t: Float, outline: Boolean = false) {
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
    if (outline) drawPath(sail, Ink, style = Stroke(2.2f * s, join = StrokeJoin.Round))
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
