package com.cyberrin.giswrap.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

data class City(
    val id: Int?,
    val name: String,
    val slug: String,
    val country: String,
    val district: String,
    val urlPath: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val where: String
        get() = listOf(district, country).filter { it.isNotBlank() }.joinToString(", ")
}

data class CurrentWeather(
    val city: String,
    val cityId: String,
    val temperature: Double? = null,
    val feelsLike: Double? = null,
    val description: String? = null,
    val humidity: Int? = null,
    val pressure: Int? = null,
    val windSpeed: Double? = null,
    val icon: String? = null,
)

data class HourlyForecast(
    val valid: LocalDateTime,
    val temperature: Double? = null,
    val feelsLike: Double? = null,
    val pressure: Int? = null,
    val humidity: Int? = null,
    val windSpeed: Double? = null,
    val windGust: Double? = null,
    val windDirection: Int? = null,
    val cloudiness: Int? = null,
    val description: String? = null,
    val icon: String? = null,
)

data class DailyForecast(
    val date: LocalDate,
    val tempMin: Double? = null,
    val tempMax: Double? = null,
    val pressureMin: Int? = null,
    val pressureMax: Int? = null,
    val humidity: Int? = null,
    val windSpeedMax: Double? = null,
    val windGust: Double? = null,
    val precipitationMm: Double? = null,
    val description: String? = null,
    val icon: String? = null,
    val hours: List<HourlyForecast> = emptyList(),
)

data class Forecast(
    val city: String,
    val cityId: String,
    val period: Period,
    val origin: ForecastOrigin,
    val days: List<DailyForecast>,
)

enum class Period(val slug: String) {
    DAYS_3("3-days"),
    WEEKS_2("2-weeks"),
    MONTH("month"),
}

enum class ForecastOrigin(val label: String) {
    LEGACY_XML("legacy-xml"),
    HTML_WIDGET("html-widget"),
    HTML_MONTH("html-month"),
}

data class Sourced<T>(
    val value: T,
    val fromCache: Boolean,
    val fetchedAt: LocalDateTime,
)

data class Place(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)
