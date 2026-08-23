@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.cyberrin.giswrap.ui.forecast
import androidx.compose.ui.res.booleanResource
import com.cyberrin.giswrap.ui.common.text
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cyberrin.giswrap.R
import com.cyberrin.giswrap.data.remote.parser.Sources
import com.cyberrin.giswrap.domain.model.Appearance
import com.cyberrin.giswrap.domain.model.City
import com.cyberrin.giswrap.domain.model.CurrentWeather
import com.cyberrin.giswrap.domain.model.DailyForecast
import com.cyberrin.giswrap.domain.model.Forecast
import com.cyberrin.giswrap.domain.model.ForecastOrigin
import com.cyberrin.giswrap.domain.model.HourlyForecast
import com.cyberrin.giswrap.domain.model.Period
import com.cyberrin.giswrap.domain.model.ThemeMode
import com.cyberrin.giswrap.ui.art.*
import com.cyberrin.giswrap.ui.common.*
import com.cyberrin.giswrap.ui.theme.*
import java.util.Locale
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
internal fun Suggestions(
    state: ForecastUiState,
    onPick: (City) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomStart = BarCorner,
            bottomEnd = BarCorner,
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .padding(start = SearchFieldInset, end = SearchFieldInset)
            .offset(y = -BarGap)
            .fillMaxWidth(),
    ) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val searchError = state.searchError
        when {
            state.searching && state.suggestions.isEmpty() -> SuggestionNote(stringResource(R.string.search_searching))
            searchError != null -> SuggestionNote(searchError.text(), error = true)
            state.suggestions.isEmpty() && state.searched ->
                SuggestionNote(stringResource(R.string.search_nothing_found, state.query))
            state.suggestions.isEmpty() -> SuggestionNote(stringResource(R.string.search_keep_typing))
            else -> Column {
                state.suggestions.take(SUGGESTION_LIMIT).forEach { city ->
                    SuggestionRow(city) { onPick(city) }
                }
                val extra = state.suggestions.size - SUGGESTION_LIMIT
                if (extra > 0) {
                    SuggestionNote(stringResource(R.string.search_more, extra))
                }
            }
        }
    }
}

@Composable
internal fun ExitHint(modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface,
        shadowElevation = 6.dp,
        modifier = modifier.padding(bottom = TabBarClearance).padding(horizontal = 24.dp),
    ) {
        Text(
            text = stringResource(R.string.exit_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
internal fun TopBar(
    state: ForecastUiState,
    onEvent: (ForecastEvent) -> Unit,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted.values.any { it }) onEvent(ForecastEvent.LocateRequested) else onEvent(ForecastEvent.LocationDenied)
    }
    val locate = {
        val already = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ).any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        if (already) {
            onEvent(ForecastEvent.LocateRequested)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                )
            )
        }
    }

    LaunchedEffect(state.askLocation) {
        if (state.askLocation) {
            onEvent(ForecastEvent.LocationPromptShown)
            locate()
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BarEdgePadding, vertical = BarGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BarGap),
    ) {
        RoundIconButton(onClick = { onEvent(ForecastEvent.SettingsOpened) }) {
            Image(
                painter = painterResource(
                    if (LocalDarkWeatherArt.current) R.drawable.ic_settings_dark
                    else R.drawable.ic_settings_light
                ),
                contentDescription = null,
                modifier = Modifier.size(34.dp),
            )
        }
        SearchField(
            value = state.query,
            onValueChange = { onEvent(ForecastEvent.QueryChanged(it)) },
            onSubmit = {
                focusManager.clearFocus()
                onEvent(ForecastEvent.SearchSubmitted)
            },
            expanded = state.suggestionsOpen,
            onFocusChanged = onSearchFocusChanged,
            modifier = Modifier.weight(1f),
        )
        RoundIconButton(onClick = onCycleTheme) {
            val tint = MaterialTheme.colorScheme.onSurface

            AnimatedContent(
                targetState = themeMode,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.7f))
                        .togetherWith(fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.7f))
                },
                label = "themeGlyph",
            ) { mode ->
                when (mode) {
                    ThemeMode.LIGHT -> LightModeGlyph(tint, Modifier.size(21.dp))
                    ThemeMode.DARK -> DarkModeGlyph(tint, Modifier.size(20.dp))
                    ThemeMode.SYSTEM -> SystemModeGlyph(tint, Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
internal fun CityPane(state: ForecastUiState, onEvent: (ForecastEvent) -> Unit) {
    val pagerState = rememberPagerState(initialPage = state.tab.ordinal) { Tab.entries.size }

    LaunchedEffect(pagerState.settledPage) {
        val settledOn = Tab.entries[pagerState.settledPage]
        if (settledOn != state.tab) onEvent(ForecastEvent.TabPicked(settledOn))
    }
    LaunchedEffect(state.tab) {
        val target = state.tab.ordinal

        if (pagerState.currentPage != target || abs(pagerState.currentPageOffsetFraction) > 0.01f) {
            if (abs(pagerState.currentPage - target) <= 1) {
                pagerState.animateScrollToPage(target)
            } else {
                pagerState.scrollToPage(target)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            flingBehavior = PagerDefaults.flingBehavior(
                state = pagerState,
                snapPositionalThreshold = 0.1f,
            ),
            // One page either side stays composed, so a swipe shows content instead of a spinner.
            beyondViewportPageCount = 1,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val pullState = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { onEvent(ForecastEvent.Refreshed) },
                state = pullState,
                indicator = {
                    RefreshArrow(
                        state = pullState,
                        isRefreshing = state.refreshing,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                },
                modifier = Modifier.fillMaxSize(),
            ) {
                val cityPath = state.city?.urlPath
                when (val tabState = state.tabs[Tab.entries[page]]) {
                    null, TabState.Loading -> Loading()
                    is TabState.Failed ->
                        Notice(stringResource(R.string.forecast_load_failed, tabState.error.text()), error = true)
                    is TabState.Now ->
                        NowPane(
                            weather = tabState.weather,
                            hours = tabState.hours,
                            fetchedAt = tabState.fetchedAt,
                            sourceUrl = cityPath?.let(Sources::cityUrl),
                        )
                    is TabState.Days ->
                        DaysPane(
                            forecast = tabState.forecast,
                            fetchedAt = tabState.fetchedAt,
                            sourceUrl = cityPath?.let { Sources.pageUrl(it, tabState.forecast.period) },
                        )
                }
            }
        }

        val slotPosition = { pagerState.currentPage + pagerState.currentPageOffsetFraction }

        val highlighted = remember(pagerState) {
            derivedStateOf {
                slotPosition().roundToInt().coerceIn(0, Tab.entries.lastIndex)
            }
        }

        TabBar(
            slots = Tab.entries.size,
            label = { stringResource(Tab.entries[it].shortRes) },
            highlighted = { highlighted.value },
            position = slotPosition,
            onSelect = { onEvent(ForecastEvent.TabPicked(Tab.entries[it])) },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp)
                .padding(bottom = 16.dp)
                .fillMaxWidth(),
        )
    }
}

@Composable
private fun Hero(
    caption: String?,
    icon: String?,
    temperature: String,
) {
    val cookie = MaterialShapes.Cookie12Sided.toShape()
    Box(
        Modifier
            .fillMaxWidth(0.72f)
            .aspectRatio(1f)

            .background(MaterialTheme.colorScheme.primaryContainer, cookie)

            .then(
                if (!LocalCuteTheme.current) {
                    Modifier
                } else {
                    Modifier.border(CuteStroke, MaterialTheme.colorScheme.primary, cookie)
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        WeatherIcon(
            code = icon,
            description = caption,
            modifier = Modifier.size(HeroIconSize),
            centerInk = true,
        )
        Text(
            text = caption.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = HeroTextInset, start = HeroTextMargin, end = HeroTextMargin),
        )
        Text(
            text = temperature,
            style = MaterialTheme.typography.displayMediumEmphasized,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = HeroTextInset),
        )
    }
}

@Composable
private fun NowPane(
    weather: CurrentWeather,
    fetchedAt: LocalDateTime,
    sourceUrl: String?,
    hours: List<HourlyForecast> = emptyList(),
) {
    val today = remember { LocalDate.now() }

    val fromUpstream = booleanResource(R.bool.condition_text_from_upstream)
    val locale = appLocale()
    val caption = remember(weather.icon, weather.description, fromUpstream) {
        conditionText(fromUpstream, weather.icon, weather.description)?.replaceFirst(", ", "\n")
    }
    val degrees = remember(weather.temperature) { signed(weather.temperature) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 8.dp, bottom = TabBarClearance,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { DateHeading(today) }
        item {
            Hero(caption = caption, icon = weather.icon, temperature = degrees)
        }
        item {
            SoftCard(identity = "current-detail") {
                FieldGrid(
                    listOf(
                        stringResource(R.string.field_feels_like) to signed(weather.feelsLike),
                        stringResource(R.string.field_humidity) to plain(weather.humidity, "%"),
                        stringResource(R.string.field_pressure) to
                            plain(weather.pressure, stringResource(R.string.unit_pressure)),
                        stringResource(R.string.field_wind) to
                            plain(weather.windSpeed, stringResource(R.string.unit_speed)),
                    )
                )
            }
        }
        if (hours.isNotEmpty()) {
            item { HourlyStrip(hours) }
        }
        item { SourceCredit(sourceUrl, fetchedAt) }
    }
}

@Composable
private fun DaysPane(
    forecast: Forecast,
    fetchedAt: LocalDateTime,
    sourceUrl: String?,
) {
    if (forecast.days.isEmpty()) {
        Notice(stringResource(R.string.forecast_empty))
        return
    }

    val first = forecast.days.first().date
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp, top = 12.dp, bottom = TabBarClearance,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(
            forecast.days,
            key = { it.date.toString() },
            contentType = { "day" },
        ) { day ->
            DayCard(day, first)
        }
        item { SourceCredit(sourceUrl, fetchedAt) }
    }
}

@Composable
private fun DayCard(day: DailyForecast, firstDate: LocalDate) {
    val speedUnit = stringResource(R.string.unit_speed)
    val todayLabel = stringResource(R.string.today)
    val fromUpstream = booleanResource(R.bool.condition_text_from_upstream)
    val locale = appLocale()
    val windLabel = stringResource(R.string.detail_wind, plain(day.windSpeedMax, speedUnit))
    val humidityLabel = stringResource(R.string.detail_humidity, "${day.humidity}%")

    val label = remember(day.date, firstDate, todayLabel, locale) {
        dayLabel(day.date, firstDate, todayLabel, locale)
    }
    val range = remember(day.tempMin, day.tempMax) {
        "${signed(day.tempMin)} … ${signed(day.tempMax)}"
    }

    val detail = remember(day, fromUpstream, locale) {
        listOfNotNull(
            conditionText(fromUpstream, day.icon, day.description, locale),
            day.windSpeedMax?.let { windLabel },
            day.humidity?.let { humidityLabel },
        ).joinToString(", ")
    }

    SoftCard(identity = day.date) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = range,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            WeatherIcon(
                code = day.icon,
                description = day.description,
                modifier = Modifier.size(DayIconSize),
            )
        }
    }
}

@Composable
private fun SourceCredit(
    url: String?,
    fetchedAt: LocalDateTime,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val openLabel = stringResource(R.string.source_credit_open)
    val cute = LocalCuteTheme.current
    val creditTuning = LocalCuteTuning.current
    val fill = MaterialTheme.colorScheme.cute(cuteColourFor("source-credit", creditTuning.weights))
    val outline = MaterialTheme.colorScheme.outlineVariant
    val ink = if (url == null) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        modifier.fillMaxWidth().padding(top = 6.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(
            Modifier

                .then(
                    if (cute) {
                        Modifier.drawWithCache {
                            val path = wobblyPath(size, CuteBar, cuteSeed("source-credit"), creditTuning)
                            onDrawBehind {
                                drawWobblyPanel(path, fill, outline, CuteStroke.toPx())
                            }
                        }
                    } else {
                        Modifier.background(fill, CircleShape)
                    }
                )
                .clip(CircleShape)
                .then(
                    if (url == null) {
                        Modifier
                    } else {
                        Modifier.clickable(onClickLabel = openLabel) { uriHandler.openUri(url) }
                    }
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                Modifier.drawBehind {
                    if (url == null) return@drawBehind
                    val y = size.height - 1.5.dp.toPx()
                    drawLine(
                        color = ink,
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                },
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val label = @Composable { text: String ->
                    Text(text = text, style = MaterialTheme.typography.bodySmall, color = ink)
                }
                label(stringResource(R.string.source_credit))
                label(stringResource(R.string.source_credit_name))
                Image(
                    painter = painterResource(R.drawable.ic_gismeteo),
                    contentDescription = null,
                    modifier = Modifier.size(13.dp),
                )
                label(remember(fetchedAt) { retrievedStamp(fetchedAt) })
            }
        }
    }
}

private val HOUR = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

@Composable
private fun HourlyStrip(
    hours: List<HourlyForecast>,
    modifier: Modifier = Modifier,
) {
    val state = rememberCarouselState { hours.size }

    HorizontalMultiBrowseCarousel(
        state = state,
        preferredItemWidth = HourItemWidth,
        itemSpacing = 8.dp,
        contentPadding = PaddingValues(horizontal = 0.dp),
        modifier = modifier.fillMaxWidth().height(HourItemHeight),
    ) { index ->
        val hour = hours[index]

        val clock = remember(hour.valid) { hour.valid.format(HOUR) }
        val degrees = remember(hour.temperature) { signed(hour.temperature) }

        Box(
            Modifier
                .maskClip(MaterialTheme.shapes.largeIncreased)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = clock,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
                WeatherIcon(
                    code = hour.icon,
                    description = hour.description,
                    modifier = Modifier.size(HourIconSize),
                )
                Text(
                    text = degrees,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

private val HourItemWidth = 76.dp
private val HourItemHeight = 104.dp

private val previewWeather = CurrentWeather(
    city = "London",
    cityId = "4517",
    temperature = 12.0,
    feelsLike = 1.0,
    // Russian on purpose: it is what Gismeteo sends, so the preview takes the
    // same path through conditionText() that a device does.
    description = "Пасмурно, дождь",
    humidity = 92,
    pressure = 748,
    windSpeed = 7.0,
    icon = "d_c3_r2",
)

@Preview(name = "Now", showBackground = true, backgroundColor = 0xFF1E1E2E)
@Composable
private fun PreviewNow() {
    GisWrapTheme(Appearance(themeMode = ThemeMode.DARK)) { Surface(color = MaterialTheme.colorScheme.surface) {
        NowPane(
            weather = previewWeather,
            fetchedAt = LocalDateTime.of(2026, 8, 8, 14, 35),
            sourceUrl = Sources.cityUrl("surgut-4501"),
        )
    } }
}

@Preview(name = "Forecast", showBackground = true, backgroundColor = 0xFF1E1E2E)
@Composable
private fun PreviewForecast() {
    val today = LocalDate.of(2026, 8, 8)
    val icons = listOf("d_c3_r2", "d_c1", "d_c0", "n_c2", "d_c4_s1")
    val days = icons.mapIndexed { offset, icon ->
        DailyForecast(
            date = today.plusDays(offset.toLong()),
            tempMin = 2.0 + offset,
            tempMax = 10.0 + offset,
            humidity = 90 - offset,
            windSpeedMax = 3.0,
            description = "Облачно, дождь",
            icon = icon,
        )
    }
    GisWrapTheme(Appearance(themeMode = ThemeMode.DARK)) { Surface(color = MaterialTheme.colorScheme.surface) {
        DaysPane(
            forecast = Forecast("London", "4517", Period.WEEKS_2, ForecastOrigin.HTML_WIDGET, days),
            fetchedAt = LocalDateTime.of(2026, 8, 8, 14, 35),
            sourceUrl = Sources.pageUrl("surgut-4501", Period.WEEKS_2),
        )
    } }
}

@Preview(name = "Sky glyphs", showBackground = true, backgroundColor = 0xFF1E1E2E)
@Composable
private fun PreviewSkies() {
    GisWrapTheme(Appearance(themeMode = ThemeMode.DARK)) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("d_c0", "d_c2", "n_c0", "d_c3", "c4_r1", "c4_r2", "d_c3_s1", "d_c3_s2", "c4_st")
                    .forEach { WeatherIcon(it, modifier = Modifier.size(44.dp)) }
            }
        }
    }
}
