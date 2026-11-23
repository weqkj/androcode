package com.androcode.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.androcode.app.data.Prefs
import kotlinx.coroutines.delay

val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

/**
 * Directional motion blur (like AE's "Directional Blur" / camera shutter): a Gaussian blur stretched along
 * one axis, driven by the animation's velocity. Radii are in px and scaled by the user's blur-strength setting.
 */
fun Modifier.blurFx(radiusX: () -> Float, radiusY: () -> Float = { 0f }): Modifier = graphicsLayer {
    val k = if (Prefs.motionBlur) Prefs.blurStrength else 0f
    val rx = radiusX() * k
    val ry = radiusY() * k
    renderEffect = if (rx > 0.6f || ry > 0.6f) {
        BlurEffect(rx.coerceAtLeast(0.01f), ry.coerceAtLeast(0.01f), TileMode.Decal)
    } else null
}

/** Spring-in with vertical motion blur: element rises, fades in and de-blurs as it settles. */
@Composable
fun Modifier.appear(delayMs: Int = 0, dy: Dp = 26.dp, blur: Float = 22f): Modifier {
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        p.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 260f))
    }
    val dyPx = with(LocalDensity.current) { dy.toPx() }
    return this.graphicsLayer {
        val q = p.value.coerceIn(0f, 1f)
        alpha = q
        translationY = (1f - p.value) * dyPx
        val k = if (Prefs.motionBlur) Prefs.blurStrength else 0f
        val r = (1f - q) * blur * k
        renderEffect = if (r > 0.6f) BlurEffect(0.01f, r, TileMode.Decal) else null
    }
}

/**
 * Screen / page switch: slides horizontally with an emphasized easing while the outgoing and incoming
 * content get a horizontal motion blur that fades out as the motion settles.
 */
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun <S> BlurSwitch(
    target: S,
    order: (S) -> Int,
    modifier: Modifier = Modifier,
    content: @Composable (S) -> Unit,
) {
    AnimatedContent(
        targetState = target,
        modifier = modifier,
        label = "blurSwitch",
        transitionSpec = {
            val dir = if (order(targetState) >= order(initialState)) 1 else -1
            (slideInHorizontally(tween(520, easing = EmphasizedDecelerate)) { (it * 0.28f).toInt() * dir } +
                fadeIn(tween(380, delayMillis = 90))) togetherWith
                (slideOutHorizontally(tween(360, easing = EmphasizedAccelerate)) { -(it * 0.28f).toInt() * dir } +
                    fadeOut(tween(240)))
        },
    ) { s ->
        val bx by transition.animateFloat(transitionSpec = { tween(480) }, label = "bx") { st ->
            if (st == EnterExitState.Visible) 0f else 46f
        }
        Box(Modifier.fillMaxSize().blurFx({ bx })) { content(s) }
    }
}
