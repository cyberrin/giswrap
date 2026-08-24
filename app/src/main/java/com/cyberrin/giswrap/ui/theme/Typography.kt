package com.cyberrin.giswrap.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.cyberrin.giswrap.R
import com.cyberrin.giswrap.domain.model.AppFont
import com.cyberrin.giswrap.domain.model.TextTuning

private val RubikDoodleShadow = FontFamily(Font(R.font.rubik_doodle_shadow))

// Default, not SansSerif: SansSerif is the sans-serif *alias* and resolves to
// Roboto, so it ignores a face the user or OEM substituted for the system font.
fun fontFamilyFor(font: AppFont): FontFamily = when (font) {
    AppFont.SYSTEM -> FontFamily.Default
    AppFont.SERIF -> FontFamily.Serif
    AppFont.DOODLE -> RubikDoodleShadow
}

private val ReadableFloor = 18.sp

fun appTypography(
    base: Typography,
    family: FontFamily,
    tuning: TextTuning,
    smallTextFamily: FontFamily? = null,
    smallTextBelow: TextUnit = ReadableFloor,
): Typography {
    fun TextStyle.tuned(): TextStyle {
        val t = tuning
        val size = fontSize * t.scale
        return copy(
            fontFamily = if (smallTextFamily != null && size < smallTextBelow) {
                smallTextFamily
            } else {
                family
            },
            fontSize = size,
            lineHeight = size * lineHeightRatio() * t.lineHeight,
            fontWeight = FontWeight(t.weight.coerceIn(100, 900)),
            letterSpacing = t.letterSpacing.em,
        )
    }

    return with(base) {
        Typography(
        displayLarge = displayLarge.tuned(),
        displayLargeEmphasized = displayLargeEmphasized.tuned(),
        displayMedium = displayMedium.tuned(),
        displayMediumEmphasized = displayMediumEmphasized.tuned(),
        displaySmall = displaySmall.tuned(),
        displaySmallEmphasized = displaySmallEmphasized.tuned(),
        headlineLarge = headlineLarge.tuned(),
        headlineLargeEmphasized = headlineLargeEmphasized.tuned(),
        headlineMedium = headlineMedium.tuned(),
        headlineMediumEmphasized = headlineMediumEmphasized.tuned(),
        headlineSmall = headlineSmall.tuned(),
        headlineSmallEmphasized = headlineSmallEmphasized.tuned(),
        titleLarge = titleLarge.tuned(),
        titleLargeEmphasized = titleLargeEmphasized.tuned(),
        titleMedium = titleMedium.tuned(),
        titleMediumEmphasized = titleMediumEmphasized.tuned(),
        titleSmall = titleSmall.tuned(),
        titleSmallEmphasized = titleSmallEmphasized.tuned(),
        bodyLarge = bodyLarge.tuned(),
        bodyLargeEmphasized = bodyLargeEmphasized.tuned(),
        bodyMedium = bodyMedium.tuned(),
        bodyMediumEmphasized = bodyMediumEmphasized.tuned(),
        bodySmall = bodySmall.tuned(),
        bodySmallEmphasized = bodySmallEmphasized.tuned(),
        labelLarge = labelLarge.tuned(),
        labelLargeEmphasized = labelLargeEmphasized.tuned(),
        labelMedium = labelMedium.tuned(),
        labelMediumEmphasized = labelMediumEmphasized.tuned(),
        labelSmall = labelSmall.tuned(),
        labelSmallEmphasized = labelSmallEmphasized.tuned(),
        )
    }
}

private fun TextStyle.lineHeightRatio(): Float {
    val size = fontSize.value
    val height = lineHeight.value
    return if (size > 0f && height > 0f && !height.isNaN()) height / size else 1.2f
}
