package com.androcode.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

/**
 * Builds full Material 3 colour schemes from a single seed colour.
 * Tonal palettes are generated in OKLCH (perceptually uniform), then mapped to M3 roles
 * using the standard tone assignments (primary 40/80, containers 90/30 and so on).
 */
object ColorEngine {
    private fun linear(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    /** returns hue (deg) and chroma of the colour in OKLCH */
    fun hueChroma(argb: Int): Pair<Double, Double> {
        val c = Color(argb)
        val r = linear(c.red); val g = linear(c.green); val b = linear(c.blue)
        val l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
        val m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
        val s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)
        val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
        val bb = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        var h = Math.toDegrees(atan2(bb, a))
        if (h < 0) h += 360.0
        return h to hypot(a, bb)
    }

    private fun rgbLinear(L: Double, a: Double, b: Double): DoubleArray {
        val l_ = L + 0.3963377774 * a + 0.2158037573 * b
        val m_ = L - 0.1055613458 * a - 0.0638541728 * b
        val s_ = L - 0.0894841775 * a - 1.2914855480 * b
        val l = l_ * l_ * l_; val m = m_ * m_ * m_; val s = s_ * s_ * s_
        return doubleArrayOf(
            4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
            -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
            -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
        )
    }

    private fun fits(v: DoubleArray) = v.all { it >= -0.0005 && it <= 1.0005 }

    private fun encode(x: Double): Float {
        val y = x.coerceIn(0.0, 1.0)
        return (if (y <= 0.0031308) 12.92 * y else 1.055 * y.pow(1.0 / 2.4) - 0.055).toFloat()
    }

    private fun toneToL(t: Double): Double {
        val y = if (t > 8.0) ((t + 16.0) / 116.0).pow(3) else t / 903.3
        return Math.cbrt(y)
    }

    /** colour of given hue/chroma at a lightness "tone" 0..100, chroma is reduced until it fits sRGB */
    fun tone(hue: Double, chroma: Double, tone: Double): Color {
        val L = toneToL(tone)
        if (tone <= 0.0) return Color.Black
        if (tone >= 100.0) return Color.White
        val h = Math.toRadians(hue)
        var c = chroma
        if (!fits(rgbLinear(L, c * cos(h), c * sin(h)))) {
            var lo = 0.0; var hi = c
            repeat(22) {
                val mid = (lo + hi) / 2
                if (fits(rgbLinear(L, mid * cos(h), mid * sin(h)))) lo = mid else hi = mid
            }
            c = lo
        }
        val v = rgbLinear(L, c * cos(h), c * sin(h))
        return Color(encode(v[0]), encode(v[1]), encode(v[2]))
    }

    fun fromHue(hue: Double): Int = tone(hue, 0.17, 62.0).toArgb()

    fun scheme(seedArgb: Int, dark: Boolean): ColorScheme {
        val (h, seedC) = hueChroma(seedArgb)
        val cp = seedC.coerceIn(0.09, 0.17)
        fun p(t: Double) = tone(h, cp, t)
        fun s(t: Double) = tone(h, cp * 0.38, t)
        fun t(t: Double) = tone(h + 60.0, cp * 0.72, t)
        fun n(t: Double) = tone(h, 0.008, t)
        fun nv(t: Double) = tone(h, 0.016, t)
        val errHue = 28.0
        fun e(t: Double) = tone(errHue, 0.19, t)

        return if (!dark) lightColorScheme(
            primary = p(40.0), onPrimary = p(100.0), primaryContainer = p(90.0), onPrimaryContainer = p(10.0),
            inversePrimary = p(80.0),
            secondary = s(40.0), onSecondary = s(100.0), secondaryContainer = s(90.0), onSecondaryContainer = s(10.0),
            tertiary = t(40.0), onTertiary = t(100.0), tertiaryContainer = t(90.0), onTertiaryContainer = t(10.0),
            background = n(98.0), onBackground = n(10.0),
            surface = n(98.0), onSurface = n(10.0), surfaceVariant = nv(90.0), onSurfaceVariant = nv(30.0),
            surfaceTint = p(40.0),
            inverseSurface = n(20.0), inverseOnSurface = n(95.0),
            error = e(40.0), onError = e(100.0), errorContainer = e(90.0), onErrorContainer = e(10.0),
            outline = nv(50.0), outlineVariant = nv(80.0), scrim = Color.Black,
            surfaceBright = n(98.0), surfaceDim = n(87.0),
            surfaceContainerLowest = n(100.0), surfaceContainerLow = n(96.0), surfaceContainer = n(94.0),
            surfaceContainerHigh = n(92.0), surfaceContainerHighest = n(90.0),
        ) else darkColorScheme(
            primary = p(80.0), onPrimary = p(20.0), primaryContainer = p(30.0), onPrimaryContainer = p(90.0),
            inversePrimary = p(40.0),
            secondary = s(80.0), onSecondary = s(20.0), secondaryContainer = s(30.0), onSecondaryContainer = s(90.0),
            tertiary = t(80.0), onTertiary = t(20.0), tertiaryContainer = t(30.0), onTertiaryContainer = t(90.0),
            background = n(6.0), onBackground = n(90.0),
            surface = n(6.0), onSurface = n(90.0), surfaceVariant = nv(30.0), onSurfaceVariant = nv(80.0),
            surfaceTint = p(80.0),
            inverseSurface = n(90.0), inverseOnSurface = n(20.0),
            error = e(80.0), onError = e(20.0), errorContainer = e(30.0), onErrorContainer = e(90.0),
            outline = nv(60.0), outlineVariant = nv(30.0), scrim = Color.Black,
            surfaceBright = n(24.0), surfaceDim = n(6.0),
            surfaceContainerLowest = n(4.0), surfaceContainerLow = n(10.0), surfaceContainer = n(12.0),
            surfaceContainerHigh = n(17.0), surfaceContainerHighest = n(22.0),
        )
    }

    fun amoled(c: ColorScheme): ColorScheme = c.copy(
        background = Color.Black, surface = Color.Black, surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0B0B0D),
        surfaceContainer = Color(0xFF111114),
        surfaceContainerHigh = Color(0xFF18181C),
        surfaceContainerHighest = Color(0xFF202025),
    )
}
