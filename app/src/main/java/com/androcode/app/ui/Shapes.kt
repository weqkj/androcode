package com.androcode.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import com.androcode.app.data.Prefs
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/** A polar "blob": r(θ) = 1 + depth·cos(lobes·θ). lobes=0 → circle. Two blobs can be morphed into each other. */
data class Blob(val lobes: Int, val depth: Float)

object Blobs {
    val Circle = Blob(0, 0f)
    val Cookie7 = Blob(7, 0.07f)
    val Cookie9 = Blob(9, 0.06f)
    val Cookie8 = Blob(8, 0.055f)
    val Flower5 = Blob(5, 0.20f)
    val Clover4 = Blob(4, 0.22f)
    val Burst12 = Blob(12, 0.08f)
    val Triangle = Blob(3, 0.28f)
    val Star6 = Blob(6, 0.30f)
    val Gem8 = Blob(8, 0.14f)
    val Squircle = Blob(4, 0.10f)
}

private fun radius(b: Blob, th: Float): Float =
    if (b.lobes == 0) 1f else (1f + b.depth * cos(b.lobes * th)) / (1f + b.depth)

/** Writes the (optionally morphed) blob outline into this path. */
fun Path.blob(cx: Float, cy: Float, r: Float, rot: Float, a: Blob, b: Blob = a, t: Float = 0f) {
    rewind()
    val n = 56
    for (i in 0..n) {
        val th = (2.0 * PI * i / n).toFloat()
        val ra = radius(a, th)
        val rb = radius(b, th)
        val rr = r * (ra + (rb - ra) * t)
        val ang = th + rot
        val x = cx + cos(ang) * rr
        val y = cy + sin(ang) * rr
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private class Spec(
    val a: Blob, val b: Blob, val role: Int, val size: Float,
    val cx: Float, val cy: Float, val ox: Float, val oy: Float,
    val orbit: Float, val spin: Float, val phase: Float, val alpha: Float,
)

private val specs: List<Spec> = run {
    val r = Random(42)
    val pool = listOf(
        Blobs.Circle, Blobs.Cookie7, Blobs.Cookie9, Blobs.Flower5, Blobs.Clover4,
        Blobs.Burst12, Blobs.Triangle, Blobs.Star6, Blobs.Gem8, Blobs.Squircle,
    )
    List(10) { i ->
        Spec(
            a = pool[r.nextInt(pool.size)], b = pool[r.nextInt(pool.size)], role = i % 6,
            size = 0.10f + r.nextFloat() * 0.20f,
            cx = r.nextFloat(), cy = r.nextFloat(),
            ox = 0.03f + r.nextFloat() * 0.10f, oy = 0.03f + r.nextFloat() * 0.10f,
            orbit = (0.18f + r.nextFloat() * 0.35f) * (if (r.nextBoolean()) 1f else -1f),
            spin = (r.nextFloat() - 0.5f) * 1.3f,
            phase = r.nextFloat() * 6.28f,
            alpha = 0.10f + r.nextFloat() * 0.16f,
        )
    }
}

/**
 * Ambient background of drifting, morphing Material-style shapes.
 * Motion blur is done the way After Effects does it: each shape is drawn at several sub-frame time samples
 * along its path, with a fading trail. [energy] (0..1) speeds the field up — it kicks on page changes and
 * sends, so the blur is strongest exactly when things move fast.
 */
@Composable
fun ShapeField(modifier: Modifier = Modifier, energy: State<Float>? = null) {
    val cs = MaterialTheme.colorScheme
    val tau = remember { mutableFloatStateOf(0f) }
    val dTau = remember { mutableFloatStateOf(0f) }
    val path = remember { Path() }

    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { n ->
                if (last != 0L) {
                    val dt = ((n - last) / 1_000_000_000f).coerceAtMost(0.05f)
                    val d = dt * (1f + (energy?.value ?: 0f) * 14f)
                    dTau.floatValue = d
                    tau.floatValue += d
                }
                last = n
            }
        }
    }

    val colors = listOf(cs.primary, cs.tertiary, cs.secondary, cs.primaryContainer, cs.tertiaryContainer, cs.secondaryContainer)

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val m = min(w, h)
        val t = tau.floatValue
        val d = dTau.floatValue
        val strength = if (Prefs.motionBlur) Prefs.blurStrength else 0f
        val shutter = d * 5f * strength
        // trail only while things move fast: idle frames draw a single sample
        val samples = if (strength <= 0f || shutter < 0.02f) 1 else (3 + 2 * strength).toInt() // how many frames of motion each exposure covers
        for (s in specs) {
            val col = colors[s.role]
            for (k in samples - 1 downTo 0) {
                val tt = t - shutter * k / samples
                val ang = s.phase + s.orbit * tt + 0.35f * sin(0.23f * tt + s.phase)
                val x = (s.cx + s.ox * cos(ang)) * w
                val y = (s.cy + s.oy * sin(ang * 0.9f)) * h
                val morph = sin(tt * 0.35f + s.phase) * 0.5f + 0.5f
                path.blob(x, y, s.size * m, s.spin * tt + s.phase, s.a, s.b, morph)
                val a = if (k == 0) s.alpha else s.alpha * 0.55f * (1f - k.toFloat() / samples)
                drawPath(path, col.copy(alpha = a.coerceIn(0f, 1f)))
            }
        }
    }
}

/** The app mark: a cookie that keeps morphing between shapes, with the terminal prompt on it. Tap it. */
@Composable
fun MorphHero(size: Dp, modifier: Modifier = Modifier, onTap: () -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    val inf = rememberInfiniteTransition(label = "hero")
    val t by inf.animateFloat(0f, 4f, infiniteRepeatableLinear(18000), label = "t")
    val spin by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatableLinear(24000), label = "spin")
    val bounce = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    val path = remember { Path() }
    val shapes = remember { listOf(Blobs.Cookie9, Blobs.Flower5, Blobs.Burst12, Blobs.Clover4) }

    Canvas(
        modifier
            .size(size)
            .graphicsLayer { scaleX = bounce.value; scaleY = bounce.value }
            .pointerInput(Unit) {
                detectTapGestures {
                    onTap()
                    scope.launch {
                        bounce.snapTo(0.84f)
                        bounce.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 280f))
                    }
                }
            }
    ) {
        val c = center
        val r = this.size.minDimension * 0.44f
        val i = floor(t).toInt().coerceIn(0, 3)
        val raw = t - floor(t)
        val f = ((raw - 0.55f) / 0.45f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        val a = shapes[i]
        val b = shapes[(i + 1) % 4]

        val trail = if (Prefs.motionBlur) (3 * Prefs.blurStrength).toInt() else 0
        for (k in trail downTo 1) {
            path.blob(c.x, c.y, r, spin - k * 0.04f, a, b, f)
            drawPath(path, cs.primary.copy(alpha = 0.12f * (1f - k / (trail + 1f))))
        }
        path.blob(c.x, c.y, r, spin, a, b, f)
        drawPath(
            path,
            Brush.linearGradient(
                listOf(cs.primary, cs.tertiary),
                start = Offset(c.x - r, c.y - r),
                end = Offset(c.x + r, c.y + r),
            )
        )
        // terminal prompt ">_"
        val sw = r * 0.23f
        val glyph = cs.onPrimary
        drawLine(glyph, Offset(c.x - r * 0.36f, c.y - r * 0.40f), Offset(c.x + r * 0.02f, c.y), sw, StrokeCap.Round)
        drawLine(glyph, Offset(c.x + r * 0.02f, c.y), Offset(c.x - r * 0.36f, c.y + r * 0.40f), sw, StrokeCap.Round)
        drawLine(glyph, Offset(c.x + r * 0.16f, c.y + r * 0.36f), Offset(c.x + r * 0.54f, c.y + r * 0.36f), sw, StrokeCap.Round)
    }
}

private fun infiniteRepeatableLinear(ms: Int) =
    infiniteRepeatable<Float>(tween(ms, easing = LinearEasing))

/** Small decorative blob (avatars, bullets, loaders). */
@Composable
fun BlobMark(size: Dp, color: Color, modifier: Modifier = Modifier, blob: Blob = Blobs.Cookie8, spinning: Boolean = false) {
    val inf = rememberInfiniteTransition(label = "mark")
    val spin by inf.animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatableLinear(if (spinning) 3500 else 40000), label = "spin")
    val path = remember { Path() }
    Canvas(modifier.size(size)) {
        path.blob(center.x, center.y, this.size.minDimension * 0.48f, spin, blob)
        drawPath(path, color)
    }
}
