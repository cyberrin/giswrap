package com.cyberrin.giswrap.data.remote.parser

import com.cyberrin.giswrap.domain.model.DailyForecast

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.xml.parsers.DocumentBuilderFactory
import java.time.LocalDate
import java.time.LocalDateTime

class SourcesTest {
    private fun assertEquals(expected: Any?, actual: Any?, message: String) =
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual, message)

    private val json = Json { ignoreUnknownKeys = true }
    private val cities = listOf("surgut", "london")

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    private fun golden(name: String): JsonObject =
        json.parseToJsonElement(resource(name)).jsonObject

    private fun JsonObject.double(key: String): Double? =
        get(key)?.jsonPrimitive?.takeIf { it.content != "null" }?.content?.toDouble()

    private fun JsonObject.int(key: String): Int? = double(key)?.toInt()

    private fun JsonObject.string(key: String): String? =
        get(key)?.jsonPrimitive?.takeIf { it.content != "null" && !it.content.isEmpty() }?.content

    private fun assertMatches(expected: JsonObject, actual: DailyForecast, where: String) {
        assertEquals(expected.string("date"), actual.date.toString(), "$where date")
        assertEquals(expected.double("temp_min"), actual.tempMin, "$where temp_min")
        assertEquals(expected.double("temp_max"), actual.tempMax, "$where temp_max")
        assertEquals(expected.int("pressure_min"), actual.pressureMin, "$where pressure_min")
        assertEquals(expected.int("pressure_max"), actual.pressureMax, "$where pressure_max")
        assertEquals(expected.int("humidity"), actual.humidity, "$where humidity")
        assertEquals(expected.double("wind_speed_max"), actual.windSpeedMax, "$where wind_speed_max")
        assertEquals(expected.double("precipitation_mm"), actual.precipitationMm, "$where precipitation_mm")
        assertEquals(expected.string("description"), actual.description, "$where description")
        assertEquals(expected.string("icon"), actual.icon, "$where icon")
    }

    @Test
    fun `legacy xml matches python`() {
        for (city in cities) {
            val expected = golden("${city}_golden_legacy.json")
            val (name, days) = Sources.parseLegacyForecast(resource("${city}_legacy.xml"))

            assertEquals(expected.string("city_name"), name, "$city city name")
            val expectedDays = expected["days"]!!.jsonArray
            assertEquals(expectedDays.size, days.size, "$city day count")

            expectedDays.forEachIndexed { i, element ->
                assertMatches(element.jsonObject, days[i], "$city legacy day $i")

                assertEquals(element.jsonObject.double("wind_gust"), days[i].windGust, "$city legacy day $i wind_gust")
            }
        }
    }

    @Test
    fun `legacy xml hourly detail matches python`() {
        for (city in cities) {
            val expected = golden("${city}_golden_legacy.json")
            val (_, days) = Sources.parseLegacyForecast(resource("${city}_legacy.xml"))

            expected["days"]!!.jsonArray.forEachIndexed { dayIndex, dayElement ->
                val expectedHours = dayElement.jsonObject["hours"]!!.jsonArray
                val hours = days[dayIndex].hours
                assertEquals(expectedHours.size, hours.size, "$city day $dayIndex hour count")

                expectedHours.forEachIndexed { i, hourElement ->
                    val e = hourElement.jsonObject
                    val a = hours[i]
                    val at = "$city day $dayIndex hour $i"
                    assertEquals(LocalDateTime.parse(e.string("valid")!!), a.valid, "$at valid")
                    assertEquals(e.double("temperature"), a.temperature, "$at temperature")
                    assertEquals(e.double("feels_like"), a.feelsLike, "$at feels_like")
                    assertEquals(e.int("pressure"), a.pressure, "$at pressure")
                    assertEquals(e.int("humidity"), a.humidity, "$at humidity")
                    assertEquals(e.double("wind_speed"), a.windSpeed, "$at wind_speed")
                    assertEquals(e.double("wind_gust"), a.windGust, "$at wind_gust")
                    assertEquals(e.int("wind_direction"), a.windDirection, "$at wind_direction")
                    assertEquals(e.int("cloudiness"), a.cloudiness, "$at cloudiness")
                    assertEquals(e.string("description"), a.description, "$at description")
                    assertEquals(e.string("icon"), a.icon, "$at icon")
                }
            }

            assertTrue(days.any { it.hours.isNotEmpty() }, "$city has some hourly detail")
        }
    }

    @Test
    fun `widget html matches python`() {
        for (city in cities) {
            val expected = golden("${city}_golden_widget.json")
            val today = LocalDate.parse(expected.string("today")!!)
            val days = Sources.parseWidgetForecast(resource("${city}_widget.html"), today)

            val expectedDays = expected["days"]!!.jsonArray
            assertEquals(expectedDays.size, days.size, "$city widget day count")
            expectedDays.forEachIndexed { i, element ->
                assertMatches(element.jsonObject, days[i], "$city widget day $i")
            }
        }
    }

    @Test
    fun `month html matches python`() {
        for (city in cities) {
            val expected = golden("${city}_golden_month.json")
            val today = LocalDate.parse(expected.string("today")!!)
            val days = Sources.parseMonthForecast(resource("${city}_month.html"), today)

            val expectedDays = expected["days"]!!.jsonArray
            assertEquals(expectedDays.size, days.size, "$city month day count")
            expectedDays.forEachIndexed { i, element ->
                assertMatches(element.jsonObject, days[i], "$city month day $i")
            }

            assertTrue(days.all { !it.date.isBefore(today) }, "$city month starts no earlier than today")
        }
    }

    @Test
    fun `parsers return empty rather than throwing on unrelated html`() {
        val today = LocalDate.of(2026, 8, 7)
        assertEquals(emptyList<DailyForecast>(), Sources.parseWidgetForecast("<html></html>", today))
        assertEquals(emptyList<DailyForecast>(), Sources.parseMonthForecast("<html></html>", today))
    }

    @Test
    fun `cell links map to dates`() {
        val today = LocalDate.of(2026, 8, 7)
        assertEquals(today, Sources.cellDate("/weather-surgut-3994/", today))
        assertEquals(today.plusDays(1), Sources.cellDate("/weather-surgut-3994/tomorrow/", today))

        assertEquals(today.plusDays(2), Sources.cellDate("/weather-surgut-3994/3-day/", today))
        assertNull(Sources.cellDate(null, today))
        assertNull(Sources.cellDate("", today))
        assertNull(Sources.cellDate("/weather-surgut-3994/month/", today))
    }

    @Test
    fun `city today prefers the iana name over the offset`() {
        assertNotNull(Sources.cityToday("Asia/Yekaterinburg", 300))

        assertNotNull(Sources.cityToday("Mars/Olympus_Mons", 300))

        assertNotNull(Sources.cityToday(null, null))
    }

    @Test
    fun `city today respects a large offset`() {
        val utc = Sources.cityToday(null, null)
        val ahead = Sources.cityToday(null, 14 * 60)
        val behind = Sources.cityToday(null, -11 * 60)
        assertTrue(!ahead.isBefore(utc), "ahead is not behind")
        assertTrue(!behind.isAfter(utc), "behind is not ahead")
    }

    @Test
    fun `a feature this parser does not have is not fatal`() {
        val factory = DocumentBuilderFactory.newInstance()
        with(Sources) {
            factory.harden("http://example.invalid/no/such/feature", true)
        }
        assertNotNull(factory.newDocumentBuilder(), "the factory must still build")
    }
}
