package com.cyberrin.giswrap.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import com.cyberrin.giswrap.domain.model.Period

class LiveSmokeTest {
    private fun live() = assumeTrue(
        System.getProperty("gismeteo.live") == "true",
        "set -Pgismeteo.live=true to run",
    )

    private fun <T> withSource(block: suspend (GismeteoRemoteDataSource) -> T): T {
        val http = HttpClient(OkHttp) {
            expectSuccess = false
            defaultRequest {
                header(
                    HttpHeaders.UserAgent,
                    "Mozilla/5.0 (X11; Linux x86_64; rv:155.0) Gecko/20100101 Firefox/155.0",
                )
            }
            install(HttpTimeout) {
                connectTimeoutMillis = 5_000
                requestTimeoutMillis = 20_000
            }
        }
        return try {
            runBlocking { block(GismeteoRemoteDataSource(http, Dispatchers.Default)) }
        } finally {
            http.close()
        }
    }

    @Test
    fun `search returns translated names`() {
        live()
        withSource { source ->
            val results = source.search("surgut")
            assertTrue(results.isNotEmpty(), "expected results")
            val city = results.first()

            assertTrue(city.name != city.slug, "expected a translated name, got ${city.name}")
            assertTrue(city.urlPath.isNotBlank())
        }
    }

    @Test
    fun `current conditions carry a temperature`() {
        live()
        withSource { source ->
            val weather = source.currentWeather("surgut-4954")
            assertNotNull(weather.temperature)
        }
    }

    @Test
    fun `every range answers`() {
        live()
        withSource { source ->
            Period.entries.forEach { period ->
                val forecast = source.forecast("surgut-4954", period)
                assertTrue(forecast.days.isNotEmpty(), "no days for ${period.slug}")
            }
        }
    }
}
