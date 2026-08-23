package com.cyberrin.giswrap.data.repository

import com.cyberrin.giswrap.data.local.LocationDataSource
import com.cyberrin.giswrap.data.local.NoFix
import com.cyberrin.giswrap.data.local.datastore.SettingsDataStore
import com.cyberrin.giswrap.data.local.db.CachedCurrent
import com.cyberrin.giswrap.data.local.db.CachedForecast
import com.cyberrin.giswrap.data.local.db.ForecastCacheDao
import com.cyberrin.giswrap.data.local.db.ForecastCacheEntity
import com.cyberrin.giswrap.data.local.db.SavedCityDao
import com.cyberrin.giswrap.data.local.db.toCached
import com.cyberrin.giswrap.data.local.db.toDomain
import com.cyberrin.giswrap.data.local.db.toEntity
import com.cyberrin.giswrap.data.remote.GismeteoRemoteDataSource
import com.cyberrin.giswrap.data.remote.UpstreamFailure
import com.cyberrin.giswrap.domain.model.Appearance
import com.cyberrin.giswrap.domain.model.City
import com.cyberrin.giswrap.domain.model.CurrentWeather
import com.cyberrin.giswrap.domain.model.Forecast
import com.cyberrin.giswrap.domain.model.Outcome
import com.cyberrin.giswrap.domain.model.Period
import com.cyberrin.giswrap.domain.model.Place
import com.cyberrin.giswrap.domain.model.Sourced
import com.cyberrin.giswrap.domain.model.WeatherError
import com.cyberrin.giswrap.domain.repository.CityRepository
import com.cyberrin.giswrap.domain.repository.LocationRepository
import com.cyberrin.giswrap.domain.repository.SettingsRepository
import com.cyberrin.giswrap.domain.repository.WeatherRepository
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json

@Singleton
class WeatherRepositoryImpl @Inject constructor(
    private val remote: GismeteoRemoteDataSource,
    private val cache: ForecastCacheDao,
) : WeatherRepository {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun currentWeather(
        city: String,
        refresh: Boolean,
    ): Outcome<Sourced<CurrentWeather>> = cached(
        key = "$city:now",
        refresh = refresh,
        decode = { json.decodeFromString<CachedCurrent>(it).toDomain() },
        encode = { json.encodeToString(it.toCached()) },
        fetch = { remote.currentWeather(city) },
    )

    override suspend fun forecast(
        city: String,
        period: Period,
        refresh: Boolean,
    ): Outcome<Sourced<Forecast>> = cached(
        key = "$city:${period.slug}",
        refresh = refresh,
        decode = { json.decodeFromString<CachedForecast>(it).toDomain() },
        encode = { json.encodeToString(it.toCached()) },
        fetch = { remote.forecast(city, period) },
    )

    override suspend fun search(query: String): Outcome<List<City>> =
        attempt { remote.search(query) }

    private suspend fun <T> cached(
        key: String,
        refresh: Boolean,
        decode: (String) -> T,
        encode: (T) -> String,
        fetch: suspend () -> T,
    ): Outcome<Sourced<T>> {
        if (!refresh) {
            val hit = cache.fresh(key, System.currentTimeMillis() - TTL_MILLIS)
            if (hit != null) {
                val value = runCatching { decode(hit.payload) }.getOrNull()
                if (value != null) {
                    return Outcome.Ok(
                        Sourced(value, fromCache = true, LocalDateTime.parse(hit.fetchedAtLocal))
                    )
                }
                cache.evict(key)
            }
        }

        return attempt {
            val value = fetch()
            val now = System.currentTimeMillis()
            val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
            cache.put(ForecastCacheEntity(key, encode(value), now, local.toString()))

            cache.sweep(now - TTL_MILLIS)
            Sourced(value, fromCache = false, local)
        }
    }

    private inline fun <T> attempt(block: () -> T): Outcome<T> = try {
        Outcome.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: UpstreamFailure) {
        Outcome.Failed(e.error)
    } catch (e: Exception) {
        Outcome.Failed(WeatherError.BadPayload)
    }

    private companion object {
        const val TTL_MILLIS = 30 * 60 * 1000L
    }
}

@Singleton
class CityRepositoryImpl @Inject constructor(
    private val dao: SavedCityDao,
) : CityRepository {
    override val saved: Flow<List<City>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override val primaryPath: Flow<String?> = dao.observePrimaryPath()

    override suspend fun save(city: City) = dao.add(city.toEntity())

    override suspend fun remove(city: City) = dao.remove(city.urlPath)

    override suspend fun setPrimary(city: City) = dao.markPrimary(city.urlPath)

    override val primary: Flow<City?> = dao.observePrimary().map { it?.toDomain() }

    override suspend fun primary(): City? = primary.first()
}

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val store: SettingsDataStore,
) : SettingsRepository {
    override val appearance: Flow<Appearance> = store.appearance
    override val locationAsked: Flow<Boolean> = store.locationAsked
    override suspend fun current(): Appearance = store.current()
    override suspend fun update(edit: (Appearance) -> Appearance) = store.update(edit)
    override suspend fun markLocationAsked() = store.markLocationAsked()
}

@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val source: LocationDataSource,
) : LocationRepository {
    override suspend fun currentPlace(): Outcome<Place> = try {
        val place = withTimeoutOrNull(FIX_TIMEOUT_MS) { source.currentPlace() }
        if (place == null) {
            Outcome.Failed(
                WeatherError.LocationTimeout
            )
        } else {
            Outcome.Ok(place)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: NoFix) {
        Outcome.Failed(e.error)
    } catch (e: SecurityException) {
        Outcome.Failed(WeatherError.LocationDenied)
    }

    private companion object {
        const val FIX_TIMEOUT_MS = 20_000L
    }
}
