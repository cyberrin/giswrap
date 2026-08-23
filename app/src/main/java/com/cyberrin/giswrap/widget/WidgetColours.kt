package com.cyberrin.giswrap.widget

import androidx.compose.ui.graphics.Color
import com.cyberrin.giswrap.domain.model.Appearance
import com.cyberrin.giswrap.domain.model.shares
import com.cyberrin.giswrap.ui.theme.CuteTuning
import com.cyberrin.giswrap.ui.theme.cute
import com.cyberrin.giswrap.ui.theme.cuteColorScheme
import com.cyberrin.giswrap.ui.theme.cuteColourFor
import com.cyberrin.giswrap.ui.theme.seededColorScheme

data class WidgetColours(
    val panel: Color,
    val text: Color,
    val muted: Color,
    val cute: Boolean = false,
    val hero: Boolean = false,
    val border: Boolean = false,
    val tuning: CuteTuning = CuteTuning(),
    val textScale: Float = 1f,
)

fun widgetColours(appearance: Appearance, dark: Boolean): WidgetColours {
    val shape = CuteTuning(
        wobble = appearance.cute.wobble,
        stars = appearance.cute.stars,
        weights = appearance.cute.shares(dark),
        frequency = appearance.cute.frequency,
        morph = appearance.cute.morph,
    )

    if (appearance.cuteTheme) {
        val scheme = cuteColorScheme(dark)
        return WidgetColours(
            panel = scheme.cute(cuteColourFor("widget", appearance.cute.shares(dark)))
                .copy(alpha = appearance.widget.opacity / 100f),
            text = scheme.onSurface,
            muted = scheme.onSurfaceVariant,
            cute = true,
            hero = appearance.widget.heroShape,
            border = appearance.widget.border,
            tuning = shape,
            textScale = appearance.text.scale,
        )
    }

    val scheme = seededColorScheme(Color(appearance.accentColor), dark)
    return WidgetColours(
        panel = scheme.surfaceContainer.copy(alpha = appearance.widget.opacity / 100f),
        text = scheme.onSurface,
        muted = scheme.onSurfaceVariant,
        hero = appearance.widget.heroShape,
        border = appearance.widget.border,
        tuning = shape,
        textScale = appearance.text.scale,
    )
}
