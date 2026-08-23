package com.cyberrin.giswrap.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyberrin.giswrap.domain.model.AppFont
import com.cyberrin.giswrap.domain.model.AppLanguage
import com.cyberrin.giswrap.domain.model.Appearance
import com.cyberrin.giswrap.domain.model.City
import com.cyberrin.giswrap.domain.model.CuteColour
import com.cyberrin.giswrap.domain.model.TextTuning
import com.cyberrin.giswrap.ui.theme.CuteTuning
import kotlinx.coroutines.flow.collect

@Stable
class SettingsBinding(
    val appearance: Appearance,
    val savedCities: List<City>,
    val primaryPath: String?,
    val dynamicColorSupported: Boolean,
    val dark: Boolean,
    private val onEvent: (SettingsEvent) -> Unit,
) {
    val dynamicColor: Boolean get() = appearance.dynamicColor
    val cuteTheme: Boolean get() = appearance.cuteTheme
    val accent: Color get() = Color(appearance.accentColor)
    val widgetOpacity: Int get() = appearance.widget.opacity
    val widgetHeroShape: Boolean get() = appearance.widget.heroShape
    val backgroundUri: String? get() = appearance.backgroundUri
    val widgetBorder: Boolean get() = appearance.widget.border
    val font: AppFont get() = appearance.font
    val language: AppLanguage get() = appearance.language
    val textTuning: TextTuning get() = appearance.text

    val tuning: CuteTuning
        get() = CuteTuning(
            wobble = appearance.cute.wobble,
            stars = appearance.cute.stars,
            weights = appearance.cute.amounts,
            frequency = appearance.cute.frequency,
            morph = appearance.cute.morph,
        )

    private fun change(edit: Appearance.() -> Appearance) =
        onEvent(SettingsEvent.AppearanceChanged(appearance.edit()))

    private fun drag(edit: Appearance.() -> Appearance) =
        onEvent(SettingsEvent.AppearanceDragged(appearance.edit()))

    val onToggleDynamicColor: (Boolean) -> Unit = { on -> change { copy(dynamicColor = on) } }
    val onToggleCuteTheme: (Boolean) -> Unit = { on -> change { copy(cuteTheme = on) } }
    val onToggleWidgetHeroShape: (Boolean) -> Unit =
        { on -> change { copy(widget = widget.copy(heroShape = on)) } }
    val onToggleWidgetBorder: (Boolean) -> Unit =
        { on -> change { copy(widget = widget.copy(border = on)) } }

    val onAccentChange: (Color) -> Unit =
        { colour -> drag { copy(accentColor = colour.toArgb()) } }

    val onWidgetOpacityChange: (Int) -> Unit =
        { percent -> drag { copy(widget = widget.copy(opacity = percent)) } }

    val onTuningChange: (CuteTuning) -> Unit = { t ->
        drag {
            copy(cute = cute.copy(
                wobble = t.wobble,
                stars = t.stars,
                frequency = t.frequency,
                morph = t.morph,
            ))
        }
    }

    val onCuteAmountChange: (CuteColour, Float) -> Unit = { colour, share ->
        drag { copy(cute = cute.copy(amounts = cute.amounts + (colour to share))) }
    }

    val onFontChange: (AppFont) -> Unit = { picked -> change { copy(font = picked) } }
    val onLanguageChange: (AppLanguage) -> Unit = { picked -> change { copy(language = picked) } }

    val onTextTuningChange: (TextTuning) -> Unit = { t -> drag { copy(text = t) } }

    val onClearBackground: () -> Unit = { onEvent(SettingsEvent.BackgroundPicked(null)) }

    var onPickBackground: () -> Unit = {}
}

@Composable
fun SettingsScreenRoute(
    onClose: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    val pickBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->

        uri?.let { viewModel.onEvent(SettingsEvent.BackgroundPicked(it.toString())) }
    }

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.Close -> onClose()
                is SettingsEffect.Message -> snackbar.showSnackbar(effect.text)
            }
        }
    }

    BackHandler { viewModel.onEvent(SettingsEvent.Back) }

    val binding = SettingsBinding(
        appearance = state.appearance,
        savedCities = state.savedCities,
        primaryPath = state.primaryPath,
        dynamicColorSupported = state.dynamicColourAvailable,
        dark = state.appearance.themeMode.isDark(isSystemInDarkTheme()),
        onEvent = viewModel::onEvent,
    ).apply {
        onPickBackground = {
            pickBackground.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            SettingsPane(state = state, onEvent = viewModel::onEvent, appearance = binding)
        }
        SnackbarHost(snackbar)
    }
}
