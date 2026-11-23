package com.androcode.app.ui

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.androcode.app.data.Prefs
import com.androcode.app.data.ThemeMode

private val AndroShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(42.dp),
)

@Composable
private fun anim(c: Color): Color = animateColorAsState(c, tween(600), label = "scheme").value

/** Every colour role cross-fades, so switching theme/seed/dynamic is a smooth morph rather than a flash. */
@Composable
private fun ColorScheme.animated(): ColorScheme = copy(
    primary = anim(primary), onPrimary = anim(onPrimary),
    primaryContainer = anim(primaryContainer), onPrimaryContainer = anim(onPrimaryContainer),
    inversePrimary = anim(inversePrimary),
    secondary = anim(secondary), onSecondary = anim(onSecondary),
    secondaryContainer = anim(secondaryContainer), onSecondaryContainer = anim(onSecondaryContainer),
    tertiary = anim(tertiary), onTertiary = anim(onTertiary),
    tertiaryContainer = anim(tertiaryContainer), onTertiaryContainer = anim(onTertiaryContainer),
    background = anim(background), onBackground = anim(onBackground),
    surface = anim(surface), onSurface = anim(onSurface),
    surfaceVariant = anim(surfaceVariant), onSurfaceVariant = anim(onSurfaceVariant),
    surfaceTint = anim(surfaceTint),
    inverseSurface = anim(inverseSurface), inverseOnSurface = anim(inverseOnSurface),
    error = anim(error), onError = anim(onError),
    errorContainer = anim(errorContainer), onErrorContainer = anim(onErrorContainer),
    outline = anim(outline), outlineVariant = anim(outlineVariant),
    surfaceBright = anim(surfaceBright), surfaceDim = anim(surfaceDim),
    surfaceContainer = anim(surfaceContainer), surfaceContainerHigh = anim(surfaceContainerHigh),
    surfaceContainerHighest = anim(surfaceContainerHighest), surfaceContainerLow = anim(surfaceContainerLow),
    surfaceContainerLowest = anim(surfaceContainerLowest),
)

@Composable
fun AndroTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = when (Prefs.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val base = if (Prefs.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else {
        ColorEngine.scheme(Prefs.seed, dark)
    }
    val scheme = if (dark && Prefs.amoled) ColorEngine.amoled(base) else base
    MaterialTheme(colorScheme = scheme.animated(), shapes = AndroShapes, content = content)
}
