package com.cyberrin.giswrap.domain.usecase

import com.cyberrin.giswrap.domain.model.City
import com.cyberrin.giswrap.domain.model.HourlyForecast
import com.cyberrin.giswrap.domain.model.Outcome
import com.cyberrin.giswrap.domain.model.Period
import com.cyberrin.giswrap.domain.repository.LocationRepository
import com.cyberrin.giswrap.domain.repository.WeatherRepository
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class LocateNearestCity @Inject constructor(
    private val location: LocationRepository,
    private val weather: WeatherRepository,
) {
    data class Located(val query: String, val nearest: City, val alternatives: List<City>)

    suspend operator fun invoke(): Outcome<Located> {
        val place = when (val fix = location.currentPlace()) {
            is Outcome.Failed -> return fix
            is Outcome.Ok -> fix.value
        }
        val cities = when (val found = weather.search(place.name)) {
            is Outcome.Failed -> return found
            is Outcome.Ok -> found.value
        }
        val nearest = cities.nearestTo(place.latitude, place.longitude)
            ?: return Outcome.Failed(
                com.cyberrin.giswrap.domain.model.WeatherError.NoCitiesNear(place.name)
            )
        return Outcome.Ok(Located(place.name, nearest, cities))
    }
}

class GetHourlyStrip @Inject constructor(
    private val weather: WeatherRepository,
) {
    suspend operator fun invoke(
        cityPath: String,
        now: LocalDateTime = LocalDateTime.now(),
    ): List<HourlyForecast> {
        val forecast = weather.forecast(cityPath, Period.DAYS_3)
        val days = (forecast as? Outcome.Ok)?.value?.value?.days ?: return emptyList()
        return days.flatMap { it.hours }

            .filter { it.valid.isAfter(now.minusHours(1)) }
            .filter { it.valid.isBefore(now.plusHours(HOURS_AHEAD)) }
    }

    private companion object {
        const val HOURS_AHEAD = 24L
    }
}

fun List<City>.nearestTo(latitude: Double, longitude: Double): City? =
    filter { it.latitude != null && it.longitude != null }
        .minByOrNull { distanceKm(latitude, longitude, it.latitude!!, it.longitude!!) }

private const val EARTH_RADIUS_KM = 6371.0088

fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)

    // min() guards asin against a hair over 1.0 from rounding.
    return 2 * EARTH_RADIUS_KM * asin(min(1.0, sqrt(a)))
}
