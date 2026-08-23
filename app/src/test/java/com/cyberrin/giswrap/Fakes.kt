package com.cyberrin.giswrap

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.cyberrin.giswrap.domain.model.Appearance
import com.cyberrin.giswrap.domain.model.City
import com.cyberrin.giswrap.domain.repository.CityRepository
import com.cyberrin.giswrap.domain.repository.SettingsRepository

class FakeCityRepository(initial: List<City> = emptyList()) : CityRepository {
    private val cities = MutableStateFlow(initial)
    private val primaryOf = MutableStateFlow(initial.firstOrNull()?.urlPath)

    override val saved: Flow<List<City>> = cities
    override val primaryPath: Flow<String?> = primaryOf
    override val primary: Flow<City?> = combine(cities, primaryOf) { list, path ->
        list.firstOrNull { it.urlPath == path } ?: list.firstOrNull()
    }

    override suspend fun save(city: City) {
        if (cities.value.none { it.urlPath == city.urlPath }) {
            cities.value = cities.value + city
        }
        if (primaryOf.value == null) primaryOf.value = city.urlPath
    }

    override suspend fun remove(city: City) {
        cities.value = cities.value.filterNot { it.urlPath == city.urlPath }
        if (primaryOf.value == city.urlPath) primaryOf.value = cities.value.firstOrNull()?.urlPath
    }

    override suspend fun setPrimary(city: City) {
        primaryOf.value = city.urlPath
    }

    override suspend fun primary(): City? = primary.first()
}

class FakeSettingsRepository(
    initial: Appearance = Appearance(),
    asked: Boolean = true,
) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    private val askedState = MutableStateFlow(asked)

    override val appearance: Flow<Appearance> = state
    override val locationAsked: Flow<Boolean> = askedState

    override suspend fun current(): Appearance = state.value

    override suspend fun update(edit: (Appearance) -> Appearance) {
        state.value = edit(state.value)
    }

    override suspend fun markLocationAsked() {
        askedState.value = true
    }
}

fun city(name: String, path: String = name.lowercase()) = City(
    id = null,
    name = name,
    slug = name.lowercase(),
    country = "Россия",
    district = "Тест",
    urlPath = path,
)
