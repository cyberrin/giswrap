package com.cyberrin.giswrap.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.cyberrin.giswrap.MainActivity
import com.cyberrin.giswrap.R
import com.cyberrin.giswrap.domain.model.Appearance
import com.cyberrin.giswrap.ui.common.forLanguage
import com.cyberrin.giswrap.domain.model.CurrentWeather
import com.cyberrin.giswrap.domain.model.Outcome
import com.cyberrin.giswrap.domain.repository.CityRepository
import com.cyberrin.giswrap.domain.repository.SettingsRepository
import com.cyberrin.giswrap.domain.repository.WeatherRepository
import com.cyberrin.giswrap.ui.art.Sky
import com.cyberrin.giswrap.ui.art.WeatherArt
import com.cyberrin.giswrap.ui.common.signed
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

class WeatherWidget : GlanceAppWidget() {
    // Not Single: that reports the provider minimum, so LocalSize is wrong and the panel draws square.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val deps = EntryPointAccessors.fromApplication(context, WidgetEntryPoint::class.java)

        // Settings are read in here, not in provideGlance -- that runs once per session and would freeze them.
        provideContent {
            val appearance by deps.settings().appearance
                .collectAsState(initial = Appearance())
            val city by deps.cities().primary.collectAsState(initial = null)

            val weather by produceState<CurrentWeather?>(null, city) {
                value = city?.let {
                    (deps.weather().currentWeather(it.urlPath) as? Outcome.Ok)?.value?.value
                }
            }

            val dark = appearance.themeMode.isDark(context.isNightMode)
            val colours = widgetColours(appearance, dark)

            GlanceTheme {
                WidgetFace(
                    weather = weather,
                    cityName = city?.name,
                    colours = colours,
                    appearance = appearance,
                    dark = dark,
                )
            }
        }
    }

    @Composable
    private fun WidgetFace(
        weather: CurrentWeather?,
        cityName: String?,
        colours: WidgetColours,
        appearance: Appearance,
        dark: Boolean,
    ) {
        val context = LocalContext.current
        val size = LocalSize.current

        val aspect = if (size.height.value <= 0f) 1f else size.width.value / size.height.value

        val cellShortDp = minOf(size.width.value, size.height.value)
        val panel = remember(aspect, cellShortDp, colours) {
            widgetPanelBitmap(aspect, cellShortDp, colours)
        }

        val art = weather?.let { WeatherArt.of(Sky.of(it.icon, it.description)) }

        val density = context.resources.displayMetrics.density
        val iconPx = (stackedFace(size.height.value, READING_DP.value).first * density).toInt()
        val icon = remember(art, colours.cute, dark, iconPx) {
            art?.let {
                weatherIconBitmap(context, it.resource(dark = dark, cute = colours.cute), iconPx)
            }
        }

        val label = weather?.temperature?.let(::signed)
            ?: cityName
            ?: remember(appearance.language) {
                context.forLanguage(appearance.language)
            }.getString(R.string.widget_no_city)
        val labelBitmap = remember(label, appearance.font, colours) {
            temperatureBitmap(
                context = context,
                text = label,
                font = appearance.font,
                colours = colours,
            )
        }

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .clickable(actionStartActivity(MainActivity::class.java)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(panel),
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = GlanceModifier.fillMaxSize(),
            )

            if (isSideBySide(aspect)) {
                Row(
                    modifier = GlanceModifier.fillMaxSize().padding(GAP),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (icon != null) {
                            Image(
                                provider = ImageProvider(icon),
                                contentDescription = weather?.description,
                                contentScale = ContentScale.Fit,
                                modifier = GlanceModifier
                                    .fillMaxWidth()
                                    .height(stackedFace(size.height.value, 0f).first.dp),
                            )
                        }
                    }
                    Box(
                        modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Reading(label, labelBitmap, colours)
                    }
                }
            } else {
                val (iconDp, gapDp) = stackedFace(size.height.value, READING_DP.value)
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Spacer(GlanceModifier.height(gapDp.dp))
                        Image(
                            provider = ImageProvider(icon),
                            contentDescription = weather?.description,
                            contentScale = ContentScale.Fit,
                            modifier = GlanceModifier.fillMaxWidth().height(iconDp.dp),
                        )
                    }
                    Spacer(GlanceModifier.height(gapDp.dp))
                    Reading(label, labelBitmap, colours)
                    Spacer(GlanceModifier.height(gapDp.dp))
                }
            }
        }
    }

    @Composable
    private fun Reading(label: String, painted: Bitmap?, colours: WidgetColours) {
        if (painted != null) {
            Image(
                provider = ImageProvider(painted),
                contentDescription = label,
                contentScale = ContentScale.Fit,
                modifier = GlanceModifier.height(28.dp),
            )
        } else {
            Text(
                text = label,
                style = TextStyle(
                    color = ColorProvider(colours.text),
                    fontSize = 24.sp * colours.textScale,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
    }

    @Singleton
    class Updater @Inject constructor(
        @ApplicationContext private val context: Context,
    ) {
        suspend fun refresh() {
            runCatching { WeatherWidget().updateAll(context) }
        }
    }
}

private val GAP = 10.dp

private val READING_DP = 30.dp

class WeatherWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeatherWidget()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun settings(): SettingsRepository
    fun cities(): CityRepository
    fun weather(): WeatherRepository
}

private val Context.isNightMode: Boolean
    get() = resources.configuration.uiMode and
        android.content.res.Configuration.UI_MODE_NIGHT_MASK ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES
