@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.cyberrin.giswrap.ui.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.cyberrin.giswrap.R
import com.cyberrin.giswrap.domain.model.AppFont
import com.cyberrin.giswrap.domain.model.AppLanguage
import com.cyberrin.giswrap.domain.model.CuteColour
import com.cyberrin.giswrap.domain.model.WIDGET_OPACITY_MAX
import com.cyberrin.giswrap.domain.model.WIDGET_OPACITY_STEPS
import com.cyberrin.giswrap.ui.art.*
import com.cyberrin.giswrap.ui.common.*
import com.cyberrin.giswrap.ui.theme.*
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

private enum class SettingsSection(val labelRes: Int) {
    APPEARANCE(R.string.settings_appearance),
    TEXT(R.string.settings_text),
    WIDGET(R.string.settings_widget),
    CITIES(R.string.settings_saved_cities),
}

@Composable
private fun WidgetOpacitySetting(opacity: Int, onChange: (Int) -> Unit) {
    var dragged by remember(opacity) { mutableFloatStateOf(opacity.toFloat()) }
    SoftCard(identity = "widget-opacity") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SettingLabel(
                    text = stringResource(R.string.settings_widget_opacity),
                    changed = opacity != WIDGET_OPACITY_MAX,
                    onReset = { onChange(WIDGET_OPACITY_MAX) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.settings_widget_opacity_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = stringResource(
                    R.string.settings_widget_opacity_value,
                    dragged.roundToInt(),
                ),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = dragged,
            onValueChange = { dragged = it },
            onValueChangeFinished = { onChange(dragged.roundToInt()) },
            valueRange = 0f..WIDGET_OPACITY_MAX.toFloat(),
            steps = WIDGET_OPACITY_STEPS - 2,
        )
    }
}

@Composable
private fun SwitchSetting(
    identity: String,
    title: String,
    hint: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    default: Boolean = false,
) {
    SoftCard(identity = identity) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SettingLabel(
                    text = title,
                    changed = checked != default,
                    onReset = { onCheckedChange(default) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        }
    }
}

@Composable
private fun ResetMark(changed: Boolean, onReset: () -> Unit) {
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        if (changed) {
            Image(
                painter = painterResource(R.drawable.ic_widget_refresh),
                contentDescription = stringResource(R.string.settings_reset),
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onReset)
                    .padding(6.dp),
            )
        }
    }
}

@Composable
private fun SettingLabel(
    text: String,
    changed: Boolean,
    onReset: () -> Unit,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.labelLarge,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = text, style = style, color = color)
        ResetMark(changed, onReset)
    }
}

@Composable
private fun TuningSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    default: Float,
    steps: Int = 0,
    onChange: (Float) -> Unit,
) {
    var dragged by remember(value) { mutableFloatStateOf(value) }
    SettingLabel(
        text = label,
        changed = abs(value - default) > 0.001f,
        onReset = { onChange(default) },
    )
    Slider(
        value = dragged,
        onValueChange = { dragged = it },
        onValueChangeFinished = { onChange(dragged) },
        valueRange = range,
        steps = steps,
    )
}

private fun LazyListScope.appearanceSection(appearance: SettingsBinding) {
    if (!appearance.cuteTheme) {
        settingItem("dynamic") {
            SwitchSetting(
                identity = "dynamic-switch",
                title = stringResource(R.string.settings_dynamic_colour),
                hint = if (dynamicColorSupported) {
                    stringResource(R.string.settings_dynamic_colour_on)
                } else {
                    stringResource(R.string.settings_dynamic_colour_unsupported)
                },
                checked = appearance.dynamicColor && dynamicColorSupported,
                enabled = dynamicColorSupported,
                onCheckedChange = appearance.onToggleDynamicColor,
            )
        }
        settingItem("accent") { AccentSetting(appearance) }
    }
    settingItem("cute") {
        SwitchSetting(
            identity = "cute-switch",
            title = stringResource(R.string.settings_cute),
            hint = stringResource(R.string.settings_cute_hint),
            checked = appearance.cuteTheme,
            onCheckedChange = appearance.onToggleCuteTheme,
        )
    }
    if (appearance.cuteTheme) {
        settingItem("cute-dials") { CuteSettings(appearance) }
    }
}

private const val DisabledAlpha = 0.38f

private fun LazyListScope.settingItem(key: String, content: @Composable () -> Unit) {
    item(key = key) {
        // Column, not Box: Box stacks its children and hid the dials behind the backdrop card.
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.animateItem(
                fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
            ),
        ) {
            content()
        }
    }
}

private fun LazyListScope.textSection(appearance: SettingsBinding) {
    settingItem("language") { LanguageSetting(appearance) }
    settingItem("font") { FontSetting(appearance) }
    settingItem("text") {
        SoftCard(identity = "text") {
            Text(
                text = stringResource(R.string.settings_text_all),
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = stringResource(R.string.settings_text_all_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val t = appearance.textTuning
            TuningSlider(
                label = stringResource(R.string.settings_text_size),
                value = t.scale,
                range = 0.6f..2f,
                default = 1f,
                onChange = { appearance.onTextTuningChange(t.copy(scale = it)) },
            )
            TuningSlider(
                label = stringResource(R.string.settings_text_weight),
                value = t.weight.toFloat(),
                range = 100f..900f,
                default = 400f,
                steps = 7,
                onChange = { appearance.onTextTuningChange(t.copy(weight = it.roundToInt())) },
            )
            TuningSlider(
                label = stringResource(R.string.settings_text_line),
                value = t.lineHeight,
                range = 0.8f..2f,
                default = 1f,
                onChange = { appearance.onTextTuningChange(t.copy(lineHeight = it)) },
            )
            TuningSlider(
                label = stringResource(R.string.settings_text_spacing),
                value = t.letterSpacing,
                range = -0.05f..0.3f,
                default = 0f,
                onChange = { appearance.onTextTuningChange(t.copy(letterSpacing = it)) },
            )
        }
    }
}

@Composable
private fun LanguageSetting(appearance: SettingsBinding) {
    SoftCard(identity = "language") {
        SettingLabel(
            text = stringResource(R.string.settings_language),
            changed = appearance.language != AppLanguage.SYSTEM,
            onReset = { appearance.onLanguageChange(AppLanguage.SYSTEM) },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.settings_language_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        AppLanguage.entries.forEach { language ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .selectable(
                        selected = appearance.language == language,
                        onClick = { appearance.onLanguageChange(language) },
                    )
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = appearance.language == language, onClick = null)
                Text(
                    text = stringResource(language.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun FontSetting(appearance: SettingsBinding) {
    SoftCard(identity = "font") {
        SettingLabel(
            text = stringResource(R.string.settings_font),
            changed = appearance.font != AppFont.SYSTEM,
            onReset = { appearance.onFontChange(AppFont.SYSTEM) },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.settings_font_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        AppFont.entries.forEach { font ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .selectable(
                        selected = appearance.font == font,
                        onClick = { appearance.onFontChange(font) },
                    )
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = appearance.font == font, onClick = null)
                Text(
                    text = stringResource(font.labelRes),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AccentSetting(appearance: SettingsBinding) {
    val pickerEnabled = !(appearance.dynamicColor && dynamicColorSupported)

    val presence by animateFloatAsState(
        targetValue = if (pickerEnabled) 1f else DisabledAlpha,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "accentEnabled",
    )
    SoftCard(identity = "accent-picker") {
        Text(
            text = stringResource(R.string.settings_accent),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.alpha(presence),
        )

        val hint = if (pickerEnabled) {
            stringResource(R.string.settings_accent_hint)
        } else {
            stringResource(R.string.settings_accent_hint_dynamic)
        }

        val hintIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val hintOut = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            targetState = hint,
            transitionSpec = { fadeIn(hintIn) togetherWith fadeOut(hintOut) },
            label = "accentHint",
        ) { line ->
        Text(
            text = line,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        }

        Box(Modifier.alpha(presence)) {
            AccentPicker(
                accent = appearance.accent,
                onAccentChange = appearance.onAccentChange,
                enabled = pickerEnabled,
            )
        }
    }
}

@Composable
private fun CuteSettings(appearance: SettingsBinding) {
    SoftCard(identity = "cute-tuning") {
        TuningSlider(
            label = stringResource(R.string.settings_cute_wobble),
            value = appearance.tuning.wobble,
            range = 0f..2f,
            default = 1f,
            onChange = { appearance.onTuningChange(appearance.tuning.copy(wobble = it)) },
        )
        TuningSlider(
            label = stringResource(R.string.settings_cute_frequency),
            value = appearance.tuning.frequency,
            range = 0.4f..3f,
            default = 1f,
            onChange = { appearance.onTuningChange(appearance.tuning.copy(frequency = it)) },
        )
        TuningSlider(
            label = stringResource(R.string.settings_cute_morph),
            value = appearance.tuning.morph,
            range = 0f..1f,
            default = 1f,
            onChange = { appearance.onTuningChange(appearance.tuning.copy(morph = it)) },
        )
        TuningSlider(
            label = stringResource(R.string.settings_cute_stars),
            value = appearance.tuning.stars,
            range = 0.3f..2.5f,
            default = 1f,
            onChange = { appearance.onTuningChange(appearance.tuning.copy(stars = it)) },
        )

        CuteColour.entries.filter { it != CuteColour.ACCENT }.forEach { colour ->

            AnimatedVisibility(
                visible = !(appearance.dark && colour == CuteColour.TERTIARY),
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                    expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                    shrinkVertically(MaterialTheme.motionScheme.defaultSpatialSpec()),
            ) {
                Column {
                    TuningSlider(
                        label = stringResource(colour.labelRes),
                        value = appearance.tuning.weights[colour] ?: 1f,
                        range = 0f..1f,
                        default = 1f,
                        onChange = { appearance.onCuteAmountChange(colour, it) },
                    )
                }
            }
        }
    }

    SoftCard(identity = "cute-background") {
        SettingLabel(
            text = stringResource(R.string.settings_background),
            changed = appearance.backgroundUri != null,
            onReset = appearance.onClearBackground,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = stringResource(R.string.settings_background_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = appearance.onPickBackground) {
                Text(stringResource(R.string.settings_background_pick))
            }
            if (appearance.backgroundUri != null) {
                TextButton(onClick = appearance.onClearBackground) {
                    Text(stringResource(R.string.settings_background_clear))
                }
            }
        }
    }
}

@Composable
internal fun SettingsPane(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit, appearance: SettingsBinding) {
    val pagerState = rememberPagerState { SettingsSection.entries.size }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            flingBehavior = PagerDefaults.flingBehavior(pagerState, snapPositionalThreshold = 0.1f),
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, bottom = TabBarClearance,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (SettingsSection.entries[page]) {
                    SettingsSection.APPEARANCE -> appearanceSection(appearance)
                    SettingsSection.TEXT -> textSection(appearance)
                    SettingsSection.WIDGET -> {
                        settingItem("widget-hero") {
                            SwitchSetting(
                                identity = "widget-hero",
                                title = stringResource(R.string.settings_widget_hero),
                                hint = stringResource(R.string.settings_widget_hero_hint),
                                checked = appearance.widgetHeroShape,
                                onCheckedChange = appearance.onToggleWidgetHeroShape,
                            )
                        }
                        settingItem("widget-border") {
                            SwitchSetting(
                                identity = "widget-border",
                                title = stringResource(R.string.settings_widget_border),
                                hint = stringResource(R.string.settings_widget_border_hint),
                                checked = appearance.widgetBorder,
                                onCheckedChange = appearance.onToggleWidgetBorder,
                            )
                        }
                        settingItem("widget-opacity") {
                            WidgetOpacitySetting(
                                appearance.widgetOpacity,
                                appearance.onWidgetOpacityChange,
                            )
                        }
                    }
                    SettingsSection.CITIES -> citiesSection(state, onEvent)
                }
            }
        }

        SlotBar(
            labels = SettingsSection.entries.map { stringResource(it.labelRes) },
            position = { pagerState.currentPage + pagerState.currentPageOffsetFraction },
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .fillMaxWidth(),
        )
    }
}

private fun LazyListScope.citiesSection(state: SettingsUiState, onEvent: (SettingsEvent) -> Unit) {
    if (state.savedCities.isEmpty()) {
        item {
            Text(
                text = stringResource(R.string.settings_no_cities),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(4.dp),
            )
        }
    }

    items(state.savedCities, key = { it.urlPath }, contentType = { "city" }) { saved ->
        val isPrimary = saved.urlPath == state.primaryPath
        SoftCard(onClick = { onEvent(SettingsEvent.PrimaryPicked(saved)) }, identity = saved.urlPath) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(saved.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (isPrimary) {
                            stringResource(R.string.settings_opens_at_launch)
                        } else {
                            saved.where.ifBlank { "—" }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isPrimary) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                if (!isPrimary) {
                    TextAction(stringResource(R.string.settings_make_primary)) { onEvent(SettingsEvent.PrimaryPicked(saved)) }
                }
                TextAction(stringResource(R.string.settings_remove)) { onEvent(SettingsEvent.CityRemoved(saved)) }
            }
        }
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

private val AppLanguage.labelRes: Int
    get() = when (this) {
        AppLanguage.SYSTEM -> R.string.settings_language_system
        AppLanguage.RUSSIAN -> R.string.settings_language_russian
        AppLanguage.ENGLISH -> R.string.settings_language_english
    }

private val AppFont.labelRes: Int
    get() = when (this) {
        AppFont.SYSTEM -> R.string.settings_font_system
        AppFont.SERIF -> R.string.settings_font_serif
        AppFont.DOODLE -> R.string.settings_font_doodle
    }

private val CuteColour.labelRes: Int
    get() = when (this) {
        CuteColour.PRIMARY -> R.string.settings_cute_primary
        CuteColour.SECONDARY -> R.string.settings_cute_secondary
        CuteColour.TERTIARY -> R.string.settings_cute_tertiary
        CuteColour.ACCENT -> R.string.settings_cute_accent
    }
