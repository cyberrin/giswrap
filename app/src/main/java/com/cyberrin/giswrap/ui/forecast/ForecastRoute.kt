@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.cyberrin.giswrap.ui.forecast

import com.cyberrin.giswrap.ui.common.text
import android.graphics.ImageDecoder
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.toSize
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cyberrin.giswrap.R
import com.cyberrin.giswrap.domain.model.Appearance
import com.cyberrin.giswrap.ui.art.buildSky
import com.cyberrin.giswrap.ui.art.drawSky
import com.cyberrin.giswrap.ui.common.DismissScrim
import com.cyberrin.giswrap.ui.common.Loading
import com.cyberrin.giswrap.ui.common.Notice
import com.cyberrin.giswrap.ui.theme.LocalCuteTheme
import com.cyberrin.giswrap.ui.theme.LocalCuteTuning
import kotlin.math.ceil
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect

@Composable
fun ForecastScreenRoute(
    appearance: Appearance,
    onOpenSearch: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ForecastViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is ForecastEffect.OpenSearch -> onOpenSearch(effect.query)
                ForecastEffect.OpenSettings -> onOpenSettings()
                ForecastEffect.AskLocationPermission -> Unit
            }
        }
    }

    ForecastScreen(state = state, onEvent = viewModel::onEvent, appearance = appearance)
}

@Composable
fun ForecastScreen(
    state: ForecastUiState,
    onEvent: (ForecastEvent) -> Unit,
    appearance: Appearance = Appearance(),
) {
    val focusManager = LocalFocusManager.current

    var searchFocused by remember { mutableStateOf(false) }
    val activity = LocalActivity.current

    var exitArmed by remember { mutableStateOf(false) }
    LaunchedEffect(exitArmed) {
        if (exitArmed) {
            delay(EXIT_WINDOW_MS)
            exitArmed = false
        }
    }

    BackHandler {
        when {
            searchFocused -> focusManager.clearFocus()
            state.suggestionsOpen -> onEvent(ForecastEvent.SuggestionsDismissed)
            exitArmed -> activity?.finish()
            else -> exitArmed = true
        }
    }

    val cute = LocalCuteTheme.current
    val surface = MaterialTheme.colorScheme.surface

    val starBright = MaterialTheme.colorScheme.primary
    val starWarm = MaterialTheme.colorScheme.tertiary
    val starDim = MaterialTheme.colorScheme.outlineVariant
    val stars = LocalCuteTuning.current.stars
    val picture = rememberBackground(appearance.backgroundUri)

    val hasBackdrop = cute || picture != null

    Surface(Modifier.fillMaxSize(), color = surface) {
        val density = LocalDensity.current

        var canvas by remember { mutableStateOf(Size.Zero) }

        val sky = remember(canvas, stars, density) {
            if (canvas.minDimension < 1f) emptyList() else with(density) { buildSky(canvas, stars) }
        }

        if (hasBackdrop) {
            Box(
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvas = it.toSize() }

                    .drawBehind {
                        drawRect(surface)
                        if (picture != null) {
                            val cover = maxOf(
                                size.width / picture.width.toFloat(),
                                size.height / picture.height.toFloat(),
                            )
                            scale(cover, Offset.Zero) {
                                drawImage(
                                    picture,
                                    topLeft = Offset(
                                        (size.width / cover - picture.width) / 2f,
                                        (size.height / cover - picture.height) / 2f,
                                    ),
                                )
                            }
                        } else {
                            drawSky(sky, starBright, starWarm, starDim)
                        }
                    }
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
        ) {
            TopBar(
                state = state,
                onEvent = onEvent,
                themeMode = appearance.themeMode,
                onCycleTheme = { onEvent(ForecastEvent.ThemeCycled) },
                onSearchFocusChanged = { searchFocused = it },
            )

            Box(Modifier.weight(1f)) {
                val searchError = state.searchError
                val page = when {
                    state.locating -> Page.LOADING
                    state.city != null -> Page.CITY
                    searchError != null -> Page.ERROR
                    else -> Page.PROMPT
                }

                AnimatedContent(
                    targetState = page,
                    transitionSpec = { PageEnter togetherWith PageExit },
                    label = "page",
                ) { shown ->
                    when (shown) {
                        Page.LOADING -> Loading()

                        Page.CITY -> state.city?.let { CityPane(state, onEvent) }
                        Page.ERROR -> Notice(searchError?.text().orEmpty(), error = true)
                        Page.PROMPT -> Notice(stringResource(R.string.search_prompt))
                    }
                }

                if (exitArmed && !searchFocused) {
                    ExitHint(Modifier.align(Alignment.BottomCenter))
                }

                if (searchFocused || state.suggestionsOpen) {
                    DismissScrim {
                        if (searchFocused) focusManager.clearFocus()
                        else onEvent(ForecastEvent.SuggestionsDismissed)
                    }
                }

                if (state.suggestionsOpen) {
                    Suggestions(
                        state = state,
                        onPick = { picked ->
                            focusManager.clearFocus()
                            onEvent(ForecastEvent.CityPicked(picked))
                        },
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                }
            }
        }
    }
}

@Composable
private fun rememberBackground(uri: String?): ImageBitmap? {
    val context = LocalContext.current
    val screen = context.resources.displayMetrics
    return remember(uri, screen.widthPixels, screen.heightPixels) {
        uri ?: return@remember null
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri.toUri())
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->

                val cover = maxOf(
                    screen.widthPixels.toFloat() / info.size.width,
                    screen.heightPixels.toFloat() / info.size.height,
                )
                if (cover < 1f) {
                    decoder.setTargetSize(
                        ceil(info.size.width * cover).toInt().coerceAtLeast(1),
                        ceil(info.size.height * cover).toInt().coerceAtLeast(1),
                    )
                }

                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }.asImageBitmap()
        }.getOrNull()
    }
}

private enum class Page { LOADING, CITY, ERROR, PROMPT }

private val PageEnter = fadeIn(tween(220, delayMillis = 40)) +
    slideInVertically(tween(260, delayMillis = 40)) { it / 12 }
private val PageExit = fadeOut(tween(140))

private const val EXIT_WINDOW_MS = 2_500L
