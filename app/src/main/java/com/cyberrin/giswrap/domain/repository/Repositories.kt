package com.cyberrin.giswrap.domain.repository

import com.cyberrin.giswrap.domain.model.Appearance
import com.cyberrin.giswrap.domain.model.City
import com.cyberrin.giswrap.domain.model.CurrentWeather
import com.cyberrin.giswrap.domain.model.Forecast
import com.cyberrin.giswrap.domain.model.Outcome
import com.cyberrin.giswrap.domain.model.Period
import com.cyberrin.giswrap.domain.model.Place
import com.cyberrin.giswrap.domain.model.Sourced
import kotlinx.coroutines.flow.Flow

interface WeatherRepository {
    suspend fun currentWeather(city: String, refresh: Boolean = false): Outcome<Sourced<CurrentWeather>>

    suspend fun forecast(city: String, period: Period, refresh: Boolean = false): Outcome<Sourced<Forecast>>

    suspend fun search(query: String): Outcome<List<City>>
}

interface CityRepository {
    val saved: Flow<List<City>>

    val primaryPath: Flow<String?>

    suspend fun save(city: City)

    suspend fun remove(city: City)

    suspend fun setPrimary(city: City)

    val primary: Flow<City?>

    suspend fun primary(): City?
}

interface SettingsRepository {
    val appearance: Flow<Appearance>

    suspend fun current(): Appearance

    suspend fun update(edit: (Appearance) -> Appearance)

    val locationAsked: Flow<Boolean>

    suspend fun markLocationAsked()
}

interface LocationRepository {
    suspend fun currentPlace(): Outcome<Place>
}
