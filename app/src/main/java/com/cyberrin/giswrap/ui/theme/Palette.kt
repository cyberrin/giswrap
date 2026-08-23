package com.cyberrin.giswrap.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.cyberrin.giswrap.domain.model.CuteColour
import com.cyberrin.giswrap.domain.model.DEFAULT_ACCENT
import kotlin.math.max

val LocalDarkWeatherArt = staticCompositionLocalOf { true }

val DefaultSeed = Color(DEFAULT_ACCENT)

private const val PRIMARY_CHROMA_FLOOR = 16.0
private const val SECONDARY_CHROMA = 16.0
private const val TERTIARY_CHROMA = 32.0
private const val TERTIARY_HUE_SHIFT = 60.0
private const val NEUTRAL_CHROMA = 8.0

private class SeedPalettes(seed: Color) {
    private val hct = hctOf(seed)

    val primary = TonalPalette(hct.hue, max(hct.chroma, PRIMARY_CHROMA_FLOOR))
    val secondary = TonalPalette(hct.hue, SECONDARY_CHROMA)
    val tertiary = TonalPalette(hct.hue + TERTIARY_HUE_SHIFT, TERTIARY_CHROMA)

    val neutral = TonalPalette(hct.hue, NEUTRAL_CHROMA)
}

private val schemeCache = object : LinkedHashMap<Long, ColorScheme>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: Map.Entry<Long, ColorScheme>) = size > 8
}

fun seededColorScheme(seed: Color, dark: Boolean): ColorScheme {
    val key = (seed.toArgb().toLong() shl 1) or if (dark) 1L else 0L
    synchronized(schemeCache) { schemeCache[key] }?.let { return it }
    val built = buildSeededColorScheme(seed, dark)
    synchronized(schemeCache) { schemeCache[key] = built }
    return built
}

private fun buildSeededColorScheme(seed: Color, dark: Boolean): ColorScheme {
    val p = SeedPalettes(seed)

    fun TonalPalette.pick(light: Int, night: Int) = tone(if (dark) night else light)

    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = p.primary.pick(40, 80),
        onPrimary = p.primary.pick(100, 20),
        primaryContainer = p.primary.pick(90, 30),
        onPrimaryContainer = p.primary.pick(10, 90),
        inversePrimary = p.primary.pick(80, 40),
        secondary = p.secondary.pick(40, 80),
        onSecondary = p.secondary.pick(100, 20),
        secondaryContainer = p.secondary.pick(90, 30),
        onSecondaryContainer = p.secondary.pick(10, 90),
        tertiary = p.tertiary.pick(40, 80),
        onTertiary = p.tertiary.pick(100, 20),
        tertiaryContainer = p.tertiary.pick(90, 30),
        onTertiaryContainer = p.tertiary.pick(10, 90),
        background = p.neutral.pick(98, 6),
        onBackground = p.neutral.pick(10, 90),
        surface = p.neutral.pick(98, 6),
        onSurface = p.neutral.pick(10, 90),
        surfaceVariant = p.neutral.pick(90, 30),
        onSurfaceVariant = p.neutral.pick(30, 80),
        inverseSurface = p.neutral.pick(20, 90),
        inverseOnSurface = p.neutral.pick(95, 20),
        outline = p.neutral.pick(50, 60),
        outlineVariant = p.neutral.pick(80, 30),
        scrim = p.neutral.pick(0, 0),
        surfaceBright = p.neutral.pick(98, 24),
        surfaceDim = p.neutral.pick(87, 6),
        surfaceContainerLowest = p.neutral.pick(100, 4),
        surfaceContainerLow = p.neutral.pick(96, 10),
        surfaceContainer = p.neutral.pick(94, 12),
        surfaceContainerHigh = p.neutral.pick(92, 17),
        surfaceContainerHighest = p.neutral.pick(90, 22),
        surfaceTint = p.primary.pick(40, 80),
        primaryFixed = p.primary.pick(90, 90),
        primaryFixedDim = p.primary.pick(80, 80),
        onPrimaryFixed = p.primary.pick(10, 10),
        onPrimaryFixedVariant = p.primary.pick(30, 30),
        secondaryFixed = p.secondary.pick(90, 90),
        secondaryFixedDim = p.secondary.pick(80, 80),
        onSecondaryFixed = p.secondary.pick(10, 10),
        onSecondaryFixedVariant = p.secondary.pick(30, 30),
        tertiaryFixed = p.tertiary.pick(90, 90),
        tertiaryFixedDim = p.tertiary.pick(80, 80),
        onTertiaryFixed = p.tertiary.pick(10, 10),
        onTertiaryFixedVariant = p.tertiary.pick(30, 30),
    )
}

fun seededWidgetColors(seed: Color, dark: Boolean): Triple<Color, Color, Color> {
    val neutral = TonalPalette(hctOf(seed).hue, NEUTRAL_CHROMA)
    return if (dark) {
        Triple(neutral.tone(12), neutral.tone(90), neutral.tone(80))
    } else {
        Triple(neutral.tone(94), neutral.tone(10), neutral.tone(30))
    }
}

private class CutePalette(
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
)

private val CuteDay = CutePalette(
    primary = Color(0xFFFCDCE1),
    secondary = Color(0xFFF0D9EF),
    tertiary = Color(0xFFE9ECCE),
    accent = Color(0xFFF4849D),
    surface = Color(0xFFFFF6F8),
    onSurface = Color(0xFF4A2C36),
    onSurfaceVariant = Color(0xFF7C5A64),
    outline = Color(0xFFE0AFBB),
)

private val CuteNight = CutePalette(
    primary = Color(0xFF4A3A46),
    secondary = Color(0xFF463C52),
    tertiary = Color(0xFFF6C9D4),
    accent = Color(0xFFF4A6B8),
    surface = Color(0xFF2B2430),
    onSurface = Color(0xFFF6E6EC),
    onSurfaceVariant = Color(0xFFD3BCC6),
    outline = Color(0xFF8A6E7C),
)

fun cuteColorScheme(dark: Boolean): ColorScheme {
    val p = if (dark) CuteNight else CuteDay
    val primary = p.primary
    val secondary = p.secondary
    val tertiary = p.tertiary
    val accent = p.accent

    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = accent,
        onPrimary = p.surface,
        primaryContainer = primary,
        onPrimaryContainer = p.onSurface,
        inversePrimary = accent,
        secondary = accent,
        onSecondary = p.surface,
        secondaryContainer = secondary,
        onSecondaryContainer = p.onSurface,
        tertiary = tertiary,
        onTertiary = p.onSurface,
        tertiaryContainer = tertiary,
        onTertiaryContainer = p.onSurface,
        background = p.surface,
        onBackground = p.onSurface,
        surface = p.surface,
        onSurface = p.onSurface,
        surfaceVariant = primary,
        onSurfaceVariant = p.onSurfaceVariant,
        surfaceTint = accent,
        outline = p.outline,
        outlineVariant = p.outline,
        surfaceContainerLowest = p.surface,
        surfaceContainerLow = p.surface,
        surfaceContainer = primary,
        surfaceContainerHigh = secondary,
        surfaceContainerHighest = secondary,
        surfaceBright = p.surface,
        surfaceDim = p.surface,
    )
}

fun cuteColourFor(identity: Any?, weights: Map<CuteColour, Float>): CuteColour {
    val shares = CuteColour.entries.map { (weights[it] ?: 1f).coerceAtLeast(0f) }
    val total = shares.sum()
    if (total <= 0f) return CuteColour.PRIMARY

    var roll = ((cuteSeed(identity).toLong() and 0xFFFFFFL) / 16777216f) * total
    CuteColour.entries.forEachIndexed { index, colour ->
        roll -= shares[index]
        if (roll < 0f) return colour
    }
    return CuteColour.ACCENT
}

fun ColorScheme.cute(colour: CuteColour): Color = when (colour) {
    CuteColour.PRIMARY -> primaryContainer
    CuteColour.SECONDARY -> secondaryContainer
    CuteColour.TERTIARY -> tertiaryContainer
    CuteColour.ACCENT -> primary
}

fun ColorScheme.onCute(colour: CuteColour): Color = when (colour) {
    CuteColour.PRIMARY -> onPrimaryContainer
    CuteColour.SECONDARY -> onSecondaryContainer
    CuteColour.TERTIARY -> onTertiaryContainer
    CuteColour.ACCENT -> onPrimary
}
