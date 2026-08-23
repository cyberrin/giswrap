package com.cyberrin.giswrap.data.remote

import com.cyberrin.giswrap.data.di.Dispatcher
import com.cyberrin.giswrap.data.di.GisDispatcher
import com.cyberrin.giswrap.data.remote.dto.UpstreamCity
import com.cyberrin.giswrap.data.remote.dto.UpstreamCityResponse
import com.cyberrin.giswrap.data.remote.dto.UpstreamSearchResponse
import com.cyberrin.giswrap.data.remote.dto.UpstreamWeatherResponse
import com.cyberrin.giswrap.data.remote.parser.Sources
import com.cyberrin.giswrap.domain.model.City
import com.cyberrin.giswrap.domain.model.CurrentWeather
import com.cyberrin.giswrap.domain.model.DailyForecast
import com.cyberrin.giswrap.domain.model.Forecast
import com.cyberrin.giswrap.domain.model.ForecastOrigin
import com.cyberrin.giswrap.domain.model.Period
import com.cyberrin.giswrap.domain.model.WeatherError
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class GismeteoRemoteDataSource @Inject constructor(
    private val http: HttpClient,
    @Dispatcher(GisDispatcher.Parsing) private val parsing: CoroutineDispatcher,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun get(url: String, asJson: Boolean = false): String {
        val response = try {
            http.get(url) {
                if (asJson) header(HttpHeaders.Accept, "application/json")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw UpstreamFailure(WeatherError.Unreachable, e)
        }

        if (response.status == HttpStatusCode.NotFound) {
            throw UpstreamFailure(WeatherError.CityNotFound)
        }
        if (response.status != HttpStatusCode.OK) {
            throw UpstreamFailure(WeatherError.UpstreamStatus(response.status.value))
        }
        return response.bodyAsText()
    }

    private inline fun <reified T> decode(body: String): T = try {
        json.decodeFromString<T>(body)
    } catch (e: Exception) {
        throw UpstreamFailure(WeatherError.BadPayload, e)
    }

    private suspend fun <T> parsed(block: () -> T): T = withContext(parsing) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw UpstreamFailure(
                WeatherError.BadPayload, e
            )
        }
    }

    suspend fun search(query: String): List<City> {
        val url = "$BASE/mq/city/q/?q=${query.urlEncoded()}" +
            // Inert -- upstream ignores it. Kept so this and client.py send byte-identical queries.
            "&geo=ru&latitude=50.714401&longitude=4.3989"
        val raw = decode<UpstreamSearchResponse>(get(url, asJson = true))

        return raw.data.map { item ->
            City(
                id = item.id,
                name = item.cityName() ?: item.slug,
                slug = item.slug,
                country = item.countryName().orEmpty(),
                district = item.districtName().orEmpty(),
                urlPath = item.id?.let { "${item.slug}-$it" } ?: item.slug,
                latitude = item.coordinates?.latitude,
                longitude = item.coordinates?.longitude,
            )
        }
    }

    suspend fun cityInfo(cityId: String): UpstreamCity? = try {
        decode<UpstreamCityResponse>(get("$BASE/mq/city/id/?id=$cityId&geo=ru", asJson = true)).data
    } catch (e: CancellationException) {
        throw e
    } catch (_: UpstreamFailure) {
        null
    }

    suspend fun currentWeather(cityPath: String): CurrentWeather {
        val cityId = cityPath.substringAfterLast('-')
        val url = "$BASE/mq/weather-and-cities/?ids=$cityId&weather=cw&geo=ru&lang=ru"
        val raw = decode<UpstreamWeatherResponse>(get(url, asJson = true))

        val item = raw.data.firstOrNull()
            ?: throw UpstreamFailure(
                WeatherError.NoWeatherHere
            )
        val weather = item.weather

        return CurrentWeather(
            city = item.city?.cityName() ?: cityPath,
            cityId = cityId,
            temperature = weather.temperatureAir,
            feelsLike = weather.temperatureFeelsLike,
            description = weather.description,
            humidity = weather.humidity,
            pressure = weather.pressure,
            windSpeed = weather.windSpeed,
            icon = weather.iconWeather,
        )
    }

    suspend fun forecast(cityPath: String, period: Period): Forecast {
        val cityId = cityPath.substringAfterLast('-')
        val origin = Sources.ORIGIN_FOR_PERIOD.getValue(period)
        var cityName: String? = null
        val days: List<DailyForecast>

        if (origin == ForecastOrigin.LEGACY_XML) {
            val body = get(Sources.legacyForecastUrl(cityId))
            val (name, parsedDays) = parsed { Sources.parseLegacyForecast(body) }
            cityName = name
            days = parsedDays
        } else {
            val (body, info) = coroutineScope {
                val page = async { get(Sources.pageUrl(cityPath, period)) }
                val details = async { cityInfo(cityId) }
                page.await() to details.await()
            }
            cityName = info?.cityName()
            val today = Sources.cityToday(info?.timeZoneName, info?.timeZone)
            days = parsed {
                if (origin == ForecastOrigin.HTML_MONTH) {
                    Sources.parseMonthForecast(body, today)
                } else {
                    Sources.parseWidgetForecast(body, today)
                }
            }
        }

        if (days.isEmpty()) {
            throw UpstreamFailure(
                WeatherError.NoForecastForRange
            )
        }

        val limit = Sources.DAY_LIMIT[period]
        return Forecast(
            city = cityName ?: cityPath,
            cityId = cityId,
            period = period,
            origin = origin,
            days = if (limit != null) days.take(limit) else days,
        )
    }

    companion object {
        const val BASE = "https://www.gismeteo.ru"
    }
}

class UpstreamFailure(
    val error: WeatherError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)

private fun String.urlEncoded(): String =
    URLEncoder.encode(trim(), "UTF-8").replace("+", "%20")
