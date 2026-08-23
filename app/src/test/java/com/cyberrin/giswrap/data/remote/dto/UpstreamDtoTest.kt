package com.cyberrin.giswrap.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class UpstreamTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    @Test
    fun `unwrapped accepts both a bare scalar and a single-element array`() {
        val wrapped = json.decodeFromString<UpstreamCurrentWeather>(
            """{"temperatureAir": [12.5], "humidity": [90], "description": ["Пасмурно"]}"""
        )
        assertEquals(12.5, wrapped.temperatureAir!!, 0.0)
        assertEquals(90, wrapped.humidity)
        assertEquals(wrapped.description, "Пасмурно")

        val bare = json.decodeFromString<UpstreamCurrentWeather>(
            """{"temperatureAir": 12.5, "humidity": 90, "description": "Пасмурно"}"""
        )
        assertEquals(wrapped, bare)
    }

    @Test
    fun `unwrapped treats empty arrays and nulls as missing`() {
        val weather = json.decodeFromString<UpstreamCurrentWeather>(
            """{"temperatureAir": [], "humidity": null, "pressure": [null]}"""
        )
        assertNull(weather.temperatureAir)
        assertNull(weather.humidity)
        assertNull(weather.pressure)

        assertNull(weather.windSpeed)
    }

    @Test
    fun `names come from translations, not from the country and district objects`() {
        val response = json.decodeFromString<UpstreamSearchResponse>(resource("surgut_search.json"))
        val city = response.data.first()

        assertEquals(city.cityName(), "Сургут")
        assertEquals(city.countryName(), "Россия")
        assertEquals(city.districtName(), "Ханты-Мансийский автономный округ - Югра")
        assertEquals(city.slug, "surgut")
        assertEquals(city.timeZoneName, "Asia/Yekaterinburg")
        assertEquals(300, city.timeZone)
    }

    @Test
    fun `language falls back when the preferred one is absent`() {
        val response = json.decodeFromString<UpstreamSearchResponse>(resource("surgut_search.json"))
        val city = response.data.first()

        assertEquals(city.cityName("ru"), city.cityName("en"))
    }

    @Test
    fun `current weather decodes from a captured payload`() {
        val response = json.decodeFromString<UpstreamWeatherResponse>(resource("surgut_weather.json"))
        val item = response.data.first()

        assertEquals(item.city?.cityName(), "Сургут")

        assertEquals(true, item.weather.temperatureAir != null)
        assertEquals(true, item.weather.humidity != null)
        assertEquals(true, item.weather.pressure != null)
        assertEquals(true, item.weather.description != null)
    }
}
