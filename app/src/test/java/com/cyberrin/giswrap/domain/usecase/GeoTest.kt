package com.cyberrin.giswrap.domain.usecase

import com.cyberrin.giswrap.data.remote.dto.UpstreamSearchResponse
import com.cyberrin.giswrap.domain.model.City

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeoTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun resource(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)) { "missing fixture $name" }
            .bufferedReader().use { it.readText() }

    private val deviceLat = 61.254
    private val deviceLon = 73.396

    private fun searchResults(fixture: String): List<City> =
        json.decodeFromString<UpstreamSearchResponse>(resource(fixture)).data.map { item ->
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

    @Test
    fun `distance matches known separations`() {
        val surgutToMoscow = distanceKm(61.254, 73.396, 55.7558, 37.6173)
        assertTrue(surgutToMoscow in 2100.0..2170.0, "got $surgutToMoscow km")

        assertEquals(0.0, distanceKm(61.254, 73.396, 61.254, 73.396), 0.0001)
        assertEquals(
            distanceKm(61.254, 73.396, 55.7558, 37.6173),
            distanceKm(55.7558, 37.6173, 61.254, 73.396),
            0.0001,
        )
    }

    @Test
    fun `longitude is worth less than latitude at high latitude`() {
        val alongLongitude = distanceKm(61.0, 73.0, 61.0, 74.0)
        val alongLatitude = distanceKm(61.0, 73.0, 62.0, 73.0)
        assertTrue(alongLongitude < alongLatitude * 0.6, "longitude $alongLongitude should be well under latitude $alongLatitude")
    }

    @Test
    fun `nearest picks the local city, not the same name 1500km away`() {
        val results = searchResults("surgut_search.json")
        val nearest = results.nearestTo(deviceLat, deviceLon)

        assertNotNull(nearest)

        assertEquals(nearest!!.urlPath, "surgut-3994")

        val faraway = results.first { it.urlPath == "surgut-11990" }
        val distance = distanceKm(deviceLat, deviceLon, faraway.latitude!!, faraway.longitude!!)
        assertTrue(distance > 1000, "the decoy really is far away: $distance km")
    }

    @Test
    fun `nearest is not simply the first result`() {
        val results = searchResults("surgut_search.json")

        val fromSamara = results.nearestTo(53.2, 50.15)
        assertEquals(fromSamara!!.urlPath, "surgut-11990")
        assertTrue(results.first().urlPath != fromSamara.urlPath, "ordering is distance-blind")
    }

    @Test
    fun `results without coordinates are ignored rather than crashing`() {
        val withoutCoords = listOf(
            City(1, "Nowhere", "nowhere", "", "", "nowhere-1"),
        )
        assertNull(withoutCoords.nearestTo(deviceLat, deviceLon))

        val mixed = withoutCoords + City(
            2, "Somewhere", "somewhere", "", "", "somewhere-2",
            latitude = 61.3, longitude = 73.4,
        )
        assertEquals(mixed.nearestTo(deviceLat, deviceLon)?.urlPath, "somewhere-2")
    }

    @Test
    fun `search results carry coordinates`() {
        val results = searchResults("surgut_search.json")
        assertTrue(results.all { it.latitude != null && it.longitude != null })
    }
}
