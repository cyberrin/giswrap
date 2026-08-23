package com.cyberrin.giswrap.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.cyberrin.giswrap.domain.model.ACCENT_SHARE
import com.cyberrin.giswrap.domain.model.AppFont
import com.cyberrin.giswrap.domain.model.AppLanguage
import com.cyberrin.giswrap.domain.model.Appearance
import com.cyberrin.giswrap.domain.model.CuteColour
import com.cyberrin.giswrap.domain.model.CuteDials
import com.cyberrin.giswrap.domain.model.DEFAULT_ACCENT
import com.cyberrin.giswrap.domain.model.TextTuning
import com.cyberrin.giswrap.domain.model.ThemeMode
import com.cyberrin.giswrap.domain.model.WIDGET_OPACITY_MAX
import com.cyberrin.giswrap.domain.model.WidgetLook
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

@Singleton
class SettingsDataStore @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    val appearance: Flow<Appearance> = store.data.map { it.toAppearance() }

    val locationAsked: Flow<Boolean> = store.data.map { it[Keys.LOCATION_ASKED] ?: false }

    suspend fun current(): Appearance = store.data.first().toAppearance()

    suspend fun markLocationAsked() {
        store.edit { it[Keys.LOCATION_ASKED] = true }
    }

    suspend fun update(edit: (Appearance) -> Appearance) {
        store.edit { prefs ->
            val next = edit(prefs.toAppearance())
            prefs.write(next)
        }
    }

    private fun Preferences.toAppearance() = Appearance(
        themeMode = this[Keys.THEME]?.let { stored ->
            ThemeMode.entries.firstOrNull { it.name == stored }
        } ?: ThemeMode.SYSTEM,
        dynamicColor = this[Keys.DYNAMIC] ?: false,
        accentColor = this[Keys.ACCENT] ?: DEFAULT_ACCENT,
        cuteTheme = this[Keys.CUTE] ?: false,
        cute = CuteDials(
            wobble = this[Keys.CUTE_WOBBLE] ?: 1f,
            frequency = this[Keys.CUTE_FREQUENCY] ?: 1f,
            morph = this[Keys.CUTE_MORPH] ?: 1f,
            stars = this[Keys.CUTE_STARS] ?: 1f,
            amounts = CuteColour.entries.associateWith { colour ->

                if (colour == CuteColour.ACCENT) ACCENT_SHARE
                else this[Keys.cuteAmount(colour)] ?: 1f
            },
        ),
        font = this[Keys.FONT]?.let { stored ->
            AppFont.entries.firstOrNull { it.name == stored }
        } ?: AppFont.SYSTEM,
        language = this[Keys.LANGUAGE]?.let { stored ->
            AppLanguage.entries.firstOrNull { it.name == stored }
        } ?: AppLanguage.SYSTEM,
        text = TextTuning(
            scale = this[Keys.TEXT_SCALE] ?: 1f,
            weight = this[Keys.TEXT_WEIGHT] ?: 400,
            lineHeight = this[Keys.TEXT_LINE] ?: 1f,
            letterSpacing = this[Keys.TEXT_SPACING] ?: 0f,
        ),
        widget = WidgetLook(
            opacity = this[Keys.WIDGET_OPACITY] ?: WIDGET_OPACITY_MAX,
            heroShape = this[Keys.WIDGET_HERO] ?: false,
            border = this[Keys.WIDGET_BORDER] ?: false,
        ),
        backgroundUri = this[Keys.BACKGROUND],
    )

    private fun androidx.datastore.preferences.core.MutablePreferences.write(a: Appearance) {
        this[Keys.THEME] = a.themeMode.name
        this[Keys.DYNAMIC] = a.dynamicColor
        this[Keys.ACCENT] = a.accentColor
        this[Keys.CUTE] = a.cuteTheme
        this[Keys.CUTE_WOBBLE] = a.cute.wobble
        this[Keys.CUTE_FREQUENCY] = a.cute.frequency
        this[Keys.CUTE_MORPH] = a.cute.morph
        this[Keys.CUTE_STARS] = a.cute.stars
        CuteColour.entries.forEach { colour ->
            this[Keys.cuteAmount(colour)] = a.cute.amounts[colour] ?: 1f
        }
        this[Keys.FONT] = a.font.name
        this[Keys.LANGUAGE] = a.language.name
        this[Keys.TEXT_SCALE] = a.text.scale
        this[Keys.TEXT_WEIGHT] = a.text.weight
        this[Keys.TEXT_LINE] = a.text.lineHeight
        this[Keys.TEXT_SPACING] = a.text.letterSpacing
        this[Keys.WIDGET_OPACITY] = a.widget.opacity
        this[Keys.WIDGET_HERO] = a.widget.heroShape
        this[Keys.WIDGET_BORDER] = a.widget.border
        a.backgroundUri?.let { this[Keys.BACKGROUND] = it } ?: remove(Keys.BACKGROUND)
    }

    private object Keys {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_colour")
        val ACCENT = intPreferencesKey("accent_colour")
        val CUTE = booleanPreferencesKey("cute_theme")
        val CUTE_WOBBLE = floatPreferencesKey("cute_wobble")
        val CUTE_FREQUENCY = floatPreferencesKey("cute_frequency")
        val CUTE_MORPH = floatPreferencesKey("cute_morph")
        val CUTE_STARS = floatPreferencesKey("cute_stars")
        val FONT = stringPreferencesKey("font")
        val LANGUAGE = stringPreferencesKey("language")
        val WIDGET_OPACITY = intPreferencesKey("widget_opacity")
        val WIDGET_HERO = booleanPreferencesKey("widget_hero_shape")
        val WIDGET_BORDER = booleanPreferencesKey("widget_border")
        val BACKGROUND = stringPreferencesKey("background_uri")
        val LOCATION_ASKED = booleanPreferencesKey("location_asked")

        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val TEXT_WEIGHT = intPreferencesKey("text_weight")
        val TEXT_LINE = floatPreferencesKey("text_line")
        val TEXT_SPACING = floatPreferencesKey("text_spacing")

        fun cuteAmount(c: CuteColour) = floatPreferencesKey("cute_amount_$c")
    }
}
