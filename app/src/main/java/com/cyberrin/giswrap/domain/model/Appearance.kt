package com.cyberrin.giswrap.domain.model

data class Appearance(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val accentColor: Int = DEFAULT_ACCENT,
    val cuteTheme: Boolean = false,
    val cute: CuteDials = CuteDials(),
    val font: AppFont = AppFont.SYSTEM,
    val language: AppLanguage = AppLanguage.SYSTEM,
    val text: TextTuning = TextTuning(),
    val widget: WidgetLook = WidgetLook(),
    val backgroundUri: String? = null,
)

data class CuteDials(
    val wobble: Float = 1f,
    val frequency: Float = 1f,
    val morph: Float = 1f,
    val stars: Float = 1f,
    val amounts: Map<CuteColour, Float> = CuteColour.entries.associateWith {
        if (it == CuteColour.ACCENT) ACCENT_SHARE else 1f
    },
)

data class WidgetLook(
    val opacity: Int = WIDGET_OPACITY_MAX,
    val heroShape: Boolean = false,
    val border: Boolean = false,
)

data class TextTuning(
    val scale: Float = 1f,
    val weight: Int = 400,
    val lineHeight: Float = 1f,
    val letterSpacing: Float = 0f,
)

enum class AppFont { SYSTEM, SERIF, DOODLE }

// SYSTEM means "let Android choose": a Russian device resolves to values-ru/,
// everything else falls back to the English values/. The other two override it.
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    RUSSIAN("ru"),
    ENGLISH("en"),
}

fun CuteDials.shares(dark: Boolean): Map<CuteColour, Float> =
    amounts.mapValues { (colour, share) ->
        when {
            colour == CuteColour.ACCENT -> ACCENT_SHARE
            dark && colour == CuteColour.TERTIARY -> 0f
            else -> share
        }
    }

const val ACCENT_SHARE = 0f

enum class CuteColour { PRIMARY, SECONDARY, TERTIARY, ACCENT }

enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM;

    fun next(): ThemeMode = entries[(ordinal + 1) % entries.size]

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        LIGHT -> false
        DARK -> true
        SYSTEM -> systemDark
    }
}

const val DEFAULT_ACCENT: Int = 0xFF00AAFF.toInt()

const val WIDGET_OPACITY_MAX = 100

const val WIDGET_OPACITY_STEPS = 11

fun opacityStep(percent: Int): Int = (percent.coerceIn(0, WIDGET_OPACITY_MAX) + 5) / 10
