package com.cyberrin.giswrap.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.cyberrin.giswrap.R
import com.cyberrin.giswrap.domain.model.WeatherError

// The only place a WeatherError becomes words. Exhaustive by `when`, so a new
// error variant fails the build here rather than reaching a user untranslated.
@Composable
fun WeatherError.text(): String = when (this) {
    WeatherError.Unreachable -> stringResource(R.string.error_unreachable)
    is WeatherError.UpstreamStatus -> stringResource(R.string.error_upstream_status, status)
    WeatherError.CityNotFound -> stringResource(R.string.error_city_not_found)
    WeatherError.NoWeatherHere -> stringResource(R.string.error_no_weather_here)
    WeatherError.NoForecastForRange -> stringResource(R.string.error_no_forecast_for_range)
    WeatherError.BadPayload -> stringResource(R.string.error_bad_payload)
    is WeatherError.NoCitiesNear -> stringResource(R.string.error_no_cities_near, place)
    WeatherError.LocationTimeout -> stringResource(R.string.error_location_timeout)
    WeatherError.LocationUnavailable -> stringResource(R.string.error_location_unavailable)
    WeatherError.LocationDenied -> stringResource(R.string.error_location_denied)
    WeatherError.LocationOff -> stringResource(R.string.error_location_off)
    WeatherError.LocationNoProvider -> stringResource(R.string.error_location_no_provider)
    WeatherError.LocationNoName -> stringResource(R.string.error_location_no_name)
}
