package com.cyberrin.giswrap.ui.art

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import com.cyberrin.giswrap.R
import com.cyberrin.giswrap.ui.theme.*

data class Sky(
    val night: Boolean = false,
    val cloud: Int = 0,
    val rain: Int = 0,
    val snow: Int = 0,
    val storm: Boolean = false,
    val fog: Boolean = false,
) {
    val wet: Boolean get() = rain > 0 || snow > 0 || storm

    companion object {
        // The Russian below is Gismeteo's wire format, not UI text -- it arrives in
        // Russian whatever the app language, and translating it would blind the art.
        // Fog and thunder appear only in the prose, never in the icon code.
        fun of(code: String?, description: String?): Sky {
            val base = parse(code)
            if (base.wet || description.isNullOrBlank()) return base
            val text = description.lowercase()

            return when {
                "гроз" in text -> base.copy(storm = true)

                "туман" in text || "дымк" in text || "мгл" in text ->
                    base.copy(fog = true)
                "снег" in text || "снеж" in text ->
                    base.copy(snow = if ("небольш" in text) 1 else 2)
                "дожд" in text || "ливен" in text || "морос" in text ->
                    base.copy(
                        rain = when {
                            "ливен" in text -> 3
                            "небольш" in text || "морос" in text -> 1
                            else -> 2
                        }
                    )
                else -> base
            }
        }

        fun parse(code: String?): Sky {
            if (code.isNullOrBlank()) return Sky()
            var sky = Sky()
            for (token in code.lowercase().split('.', '_')) {
                when {
                    token == "n" -> sky = sky.copy(night = true)

                    token == "st" -> sky = sky.copy(storm = true)
                    token.startsWith("c") -> token.drop(1).toIntOrNull()
                        ?.let { sky = sky.copy(cloud = it) }
                    token.startsWith("r") -> token.drop(1).toIntOrNull()
                        ?.let { sky = sky.copy(rain = it) }
                    token.startsWith("s") -> token.drop(1).toIntOrNull()
                        ?.let { sky = sky.copy(snow = it) }
                }
            }
            return sky
        }
    }
}

enum class WeatherArt(
    @DrawableRes val light: Int,
    @DrawableRes val dark: Int,
    @DrawableRes val cuteLight: Int,
    @DrawableRes val cuteDark: Int,
    val inkCenterY: Float,
) {
    SUN(R.drawable.wx_sun_light, R.drawable.wx_sun_dark,
        R.drawable.cute_sun_light, R.drawable.cute_sun_dark, 0.500f),
    MOON(R.drawable.wx_moon_light, R.drawable.wx_moon_dark,
        R.drawable.cute_moon_light, R.drawable.cute_moon_dark, 0.506f),
    SUN_CLOUD(R.drawable.wx_cloudy_sun_light, R.drawable.wx_cloudy_sun_dark,
        R.drawable.cute_cloudy_sun_light, R.drawable.cute_cloudy_sun_dark, 0.451f),
    MOON_CLOUD(R.drawable.wx_moon_with_sun_light, R.drawable.wx_moon_with_sun_dark,
        R.drawable.cute_moon_with_sun_light, R.drawable.cute_moon_with_sun_dark, 0.455f),
    LIGHT_CLOUDS(R.drawable.wx_light_clouds_light, R.drawable.wx_light_clouds_dark,
        R.drawable.cute_light_clouds_light, R.drawable.cute_light_clouds_dark, 0.495f),
    HEAVY_CLOUDS(R.drawable.wx_heavy_clouds_light, R.drawable.wx_heavy_clouds_dark,
        R.drawable.cute_heavy_clouds_light, R.drawable.cute_heavy_clouds_dark, 0.495f),
    MIST(R.drawable.wx_mist_light, R.drawable.wx_mist_dark,
        R.drawable.cute_mist_light, R.drawable.cute_mist_dark, 0.495f),
    RAIN(R.drawable.wx_rain_light, R.drawable.wx_rain_dark,
        R.drawable.cute_rain_light, R.drawable.cute_rain_dark, 0.549f),
    HEAVY_RAIN(R.drawable.wx_heavy_rain_light, R.drawable.wx_heavy_rain_dark,
        R.drawable.cute_heavy_rain_light, R.drawable.cute_heavy_rain_dark, 0.549f),
    SNOW(R.drawable.wx_snow_light, R.drawable.wx_snow_dark,
        R.drawable.cute_snow_light, R.drawable.cute_snow_dark, 0.566f),
    HEAVY_SNOW(R.drawable.wx_heavy_snow_light, R.drawable.wx_heavy_snow_dark,
        R.drawable.cute_heavy_snow_light, R.drawable.cute_heavy_snow_dark, 0.594f),
    LIGHTNING(R.drawable.wx_lightning_light, R.drawable.wx_lightning_dark,
        R.drawable.cute_lightning_light, R.drawable.cute_lightning_dark, 0.583f),
    THUNDERSTORM(R.drawable.wx_thunderstorm_light, R.drawable.wx_thunderstorm_dark,
        R.drawable.cute_thunderstorm_light, R.drawable.cute_thunderstorm_dark, 0.583f);

    @DrawableRes
    fun resource(dark: Boolean, cute: Boolean = false): Int = when {
        cute && dark -> cuteDark
        cute -> cuteLight
        dark -> this.dark
        else -> light
    }

    val centeringOffsetY: Float get() = 0.5f - inkCenterY

    companion object {
        fun of(sky: Sky): WeatherArt = when {
            sky.storm && sky.rain > 0 -> THUNDERSTORM
            sky.storm -> LIGHTNING

            sky.snow >= 2 -> HEAVY_SNOW
            sky.snow > 0 -> SNOW
            sky.rain >= 2 -> HEAVY_RAIN
            sky.rain > 0 -> RAIN

            sky.fog -> MIST

            sky.cloud >= 4 -> HEAVY_CLOUDS
            sky.cloud == 3 -> LIGHT_CLOUDS
            sky.cloud == 2 -> if (sky.night) MOON_CLOUD else SUN_CLOUD
            else -> if (sky.night) MOON else SUN
        }

        fun of(code: String?, description: String?): WeatherArt = of(Sky.of(code, description))
    }
}

@Composable
fun WeatherIcon(
    code: String?,
    description: String? = null,
    modifier: Modifier = Modifier,
    centerInk: Boolean = false,
) {
    val dark = LocalDarkWeatherArt.current
    val cute = LocalCuteTheme.current

    val art = remember(code, description) { WeatherArt.of(code, description) }
    Image(
        painter = painterResource(art.resource(dark, cute)),
        contentDescription = null,
        modifier = if (!centerInk) {
            modifier
        } else {
            modifier.graphicsLayer { translationY = art.centeringOffsetY * size.height }
        },
    )
}
