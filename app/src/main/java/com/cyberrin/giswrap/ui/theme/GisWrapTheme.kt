package com.cyberrin.giswrap.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat
import com.cyberrin.giswrap.domain.model.AppFont
import com.cyberrin.giswrap.domain.model.Appearance
import com.cyberrin.giswrap.domain.model.ThemeMode
import com.cyberrin.giswrap.domain.model.shares

val dynamicColorSupported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

private val ThemeSwitchSpec = tween<Float>(durationMillis = 350, easing = FastOutSlowInEasing)

private fun blend(from: ColorScheme, to: ColorScheme, t: Float): ColorScheme = from.copy(
    background = lerp(from.background, to.background, t),
    error = lerp(from.error, to.error, t),
    errorContainer = lerp(from.errorContainer, to.errorContainer, t),
    inverseOnSurface = lerp(from.inverseOnSurface, to.inverseOnSurface, t),
    inversePrimary = lerp(from.inversePrimary, to.inversePrimary, t),
    inverseSurface = lerp(from.inverseSurface, to.inverseSurface, t),
    onBackground = lerp(from.onBackground, to.onBackground, t),
    onError = lerp(from.onError, to.onError, t),
    onErrorContainer = lerp(from.onErrorContainer, to.onErrorContainer, t),
    onPrimary = lerp(from.onPrimary, to.onPrimary, t),
    onPrimaryContainer = lerp(from.onPrimaryContainer, to.onPrimaryContainer, t),
    onPrimaryFixed = lerp(from.onPrimaryFixed, to.onPrimaryFixed, t),
    onPrimaryFixedVariant = lerp(from.onPrimaryFixedVariant, to.onPrimaryFixedVariant, t),
    onSecondary = lerp(from.onSecondary, to.onSecondary, t),
    onSecondaryContainer = lerp(from.onSecondaryContainer, to.onSecondaryContainer, t),
    onSecondaryFixed = lerp(from.onSecondaryFixed, to.onSecondaryFixed, t),
    onSecondaryFixedVariant = lerp(from.onSecondaryFixedVariant, to.onSecondaryFixedVariant, t),
    onSurface = lerp(from.onSurface, to.onSurface, t),
    onSurfaceVariant = lerp(from.onSurfaceVariant, to.onSurfaceVariant, t),
    onTertiary = lerp(from.onTertiary, to.onTertiary, t),
    onTertiaryContainer = lerp(from.onTertiaryContainer, to.onTertiaryContainer, t),
    onTertiaryFixed = lerp(from.onTertiaryFixed, to.onTertiaryFixed, t),
    onTertiaryFixedVariant = lerp(from.onTertiaryFixedVariant, to.onTertiaryFixedVariant, t),
    outline = lerp(from.outline, to.outline, t),
    outlineVariant = lerp(from.outlineVariant, to.outlineVariant, t),
    primary = lerp(from.primary, to.primary, t),
    primaryContainer = lerp(from.primaryContainer, to.primaryContainer, t),
    primaryFixed = lerp(from.primaryFixed, to.primaryFixed, t),
    primaryFixedDim = lerp(from.primaryFixedDim, to.primaryFixedDim, t),
    scrim = lerp(from.scrim, to.scrim, t),
    secondary = lerp(from.secondary, to.secondary, t),
    secondaryContainer = lerp(from.secondaryContainer, to.secondaryContainer, t),
    secondaryFixed = lerp(from.secondaryFixed, to.secondaryFixed, t),
    secondaryFixedDim = lerp(from.secondaryFixedDim, to.secondaryFixedDim, t),
    surface = lerp(from.surface, to.surface, t),
    surfaceBright = lerp(from.surfaceBright, to.surfaceBright, t),
    surfaceContainer = lerp(from.surfaceContainer, to.surfaceContainer, t),
    surfaceContainerHigh = lerp(from.surfaceContainerHigh, to.surfaceContainerHigh, t),
    surfaceContainerHighest = lerp(from.surfaceContainerHighest, to.surfaceContainerHighest, t),
    surfaceContainerLow = lerp(from.surfaceContainerLow, to.surfaceContainerLow, t),
    surfaceContainerLowest = lerp(from.surfaceContainerLowest, to.surfaceContainerLowest, t),
    surfaceDim = lerp(from.surfaceDim, to.surfaceDim, t),
    surfaceTint = lerp(from.surfaceTint, to.surfaceTint, t),
    surfaceVariant = lerp(from.surfaceVariant, to.surfaceVariant, t),
    tertiary = lerp(from.tertiary, to.tertiary, t),
    tertiaryContainer = lerp(from.tertiaryContainer, to.tertiaryContainer, t),
    tertiaryFixed = lerp(from.tertiaryFixed, to.tertiaryFixed, t),
    tertiaryFixedDim = lerp(from.tertiaryFixedDim, to.tertiaryFixedDim, t),
)

@Composable
private fun animatedColorScheme(target: ColorScheme, key: Any): ColorScheme {
    val progress = remember { Animatable(1f) }

    val ends = remember { mutableStateOf(target to target) }
    var started by remember { mutableStateOf(false) }

    LaunchedEffect(key) {
        if (!started) {
            started = true
            ends.value = target to target
            return@LaunchedEffect
        }

        val (from, to) = ends.value
        ends.value = blend(from, to, progress.value) to target
        progress.snapTo(0f)
        progress.animateTo(1f, ThemeSwitchSpec)
    }

    val (from, to) = ends.value
    return if (progress.value >= 1f) to else blend(from, to, progress.value)
}

@Composable
fun GisWrapTheme(
    appearance: Appearance,
    content: @Composable () -> Unit,
) {
    val mode = appearance.themeMode
    val dynamic = appearance.dynamicColor
    val accent = Color(appearance.accentColor)
    val cute = appearance.cuteTheme
    val dark = mode.isDark(isSystemInDarkTheme())
    val tuning = CuteTuning(
        wobble = appearance.cute.wobble,
        stars = appearance.cute.stars,
        weights = appearance.cute.shares(dark),
        frequency = appearance.cute.frequency,
        morph = appearance.cute.morph,
    )
    val font = appearance.font
    val textTuning = appearance.text

    val context = LocalContext.current

    val useDynamic = dynamic && dynamicColorSupported && !cute
    val colorScheme = if (cute) {
        remember(dark) { cuteColorScheme(dark) }
    } else if (useDynamic) {
        remember(context, dark) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
    } else {
        remember(accent, dark) { seededColorScheme(accent, dark) }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        LaunchedEffect(dark, view) {
            val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkWeatherArt provides dark,
        LocalCuteTheme provides cute,
        LocalCuteTuning provides tuning,
    ) {
        val baseType = MaterialTheme.typography
        val family = remember(font) { fontFamilyFor(font) }
        MaterialExpressiveTheme(
            colorScheme = animatedColorScheme(colorScheme, listOf(useDynamic, accent, dark, cute)),
            typography = remember(baseType, family, textTuning, font) {
                appTypography(
                    base = baseType,
                    family = family,
                    tuning = textTuning,
                    smallTextFamily = if (font == AppFont.DOODLE) FontFamily.SansSerif else null,
                )
            },
            motionScheme = MotionScheme.standard(),
            content = content,
        )
    }
}
