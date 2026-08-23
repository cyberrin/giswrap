package com.cyberrin.giswrap.data.repository

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.cyberrin.giswrap.data.local.db.ForecastCacheDao
import com.cyberrin.giswrap.data.local.db.ForecastCacheEntity
import com.cyberrin.giswrap.data.remote.GismeteoRemoteDataSource
import com.cyberrin.giswrap.data.remote.UpstreamFailure
import com.cyberrin.giswrap.domain.model.CurrentWeather
import com.cyberrin.giswrap.domain.model.Outcome
import com.cyberrin.giswrap.domain.model.WeatherError

class WeatherRepositoryImplTest {
    private val remote = mockk<GismeteoRemoteDataSource>()
    private val cache = FakeCacheDao()
    private val repository = WeatherRepositoryImpl(remote, cache)

    private val reading = CurrentWeather(city = "Сургут", cityId = "4954", temperature = -3.0)

    @Test
    fun `the first read goes to the network and the second does not`() = runTest {
        coEvery { remote.currentWeather(any()) } returns reading

        val first = repository.currentWeather("surgut-4954")
        val second = repository.currentWeather("surgut-4954")

        assertFalse((first as Outcome.Ok).value.fromCache)
        assertTrue((second as Outcome.Ok).value.fromCache)
        coVerify(exactly = 1) { remote.currentWeather(any()) }
    }

    @Test
    fun `a cache hit reports when the network answered, not when it was read`() = runTest {
        coEvery { remote.currentWeather(any()) } returns reading

        val fresh = (repository.currentWeather("surgut-4954") as Outcome.Ok).value
        val hit = (repository.currentWeather("surgut-4954") as Outcome.Ok).value

        assertEquals(fresh.fetchedAt, hit.fetchedAt)
    }

    @Test
    fun `refresh skips a live entry`() = runTest {
        coEvery { remote.currentWeather(any()) } returns reading

        repository.currentWeather("surgut-4954")
        val forced = repository.currentWeather("surgut-4954", refresh = true)

        assertFalse((forced as Outcome.Ok).value.fromCache)
        coVerify(exactly = 2) { remote.currentWeather(any()) }
    }

    @Test
    fun `an expired entry is not served`() = runTest {
        coEvery { remote.currentWeather(any()) } returns reading
        repository.currentWeather("surgut-4954")

        cache.age(millis = 31 * 60 * 1000)
        val second = repository.currentWeather("surgut-4954")

        assertFalse((second as Outcome.Ok).value.fromCache)
    }

    @Test
    fun `a payload an older build wrote is a miss, not a crash`() = runTest {
        coEvery { remote.currentWeather(any()) } returns reading
        cache.put(ForecastCacheEntity(
            key = "surgut-4954:now",
            payload = """{"shape":"from a build that no longer exists"}""",
            fetchedAtMillis = System.currentTimeMillis(),
            fetchedAtLocal = "2026-08-15T12:00",
        ))

        val got = repository.currentWeather("surgut-4954")

        assertFalse((got as Outcome.Ok).value.fromCache)
        assertEquals(-3.0, got.value.value.temperature)
    }

    @Test
    fun `a failed refresh keeps the reading it failed to replace`() = runTest {
        coEvery { remote.currentWeather(any()) } returns reading
        repository.currentWeather("surgut-4954")

        coEvery { remote.currentWeather(any()) } throws
            UpstreamFailure(WeatherError.Unreachable)
        val failed = repository.currentWeather("surgut-4954", refresh = true)
        assertTrue(failed is Outcome.Failed)

        coEvery { remote.currentWeather(any()) } throws
            UpstreamFailure(WeatherError.Unreachable)
        val after = repository.currentWeather("surgut-4954")
        assertTrue((after as Outcome.Ok).value.fromCache, "the cached reading was thrown away")
        assertEquals(-3.0, after.value.value.temperature)
    }

    @Test
    fun `a successful refresh replaces what was there`() = runTest {
        coEvery { remote.currentWeather(any()) } returns reading
        repository.currentWeather("surgut-4954")

        val warmer = reading.copy(temperature = 5.0)
        coEvery { remote.currentWeather(any()) } returns warmer
        repository.currentWeather("surgut-4954", refresh = true)

        coEvery { remote.currentWeather(any()) } throws
            UpstreamFailure(WeatherError.Unreachable)
        val served = repository.currentWeather("surgut-4954")
        assertEquals(5.0, (served as Outcome.Ok).value.value.temperature)
    }

    @Test
    fun `an upstream failure keeps its own error type`() = runTest {
        coEvery { remote.currentWeather(any()) } throws
            UpstreamFailure(WeatherError.UpstreamStatus(503))

        val got = repository.currentWeather("surgut-4954")

        assertEquals(WeatherError.UpstreamStatus(503), (got as Outcome.Failed).error)
    }

    private class FakeCacheDao : ForecastCacheDao {
        private val rows = mutableMapOf<String, ForecastCacheEntity>()

        override suspend fun fresh(key: String, notBefore: Long): ForecastCacheEntity? =
            rows[key]?.takeIf { it.fetchedAtMillis > notBefore }

        override suspend fun put(entry: ForecastCacheEntity) {
            rows[entry.key] = entry
        }

        override suspend fun evict(key: String) {
            rows.remove(key)
        }

        override suspend fun sweep(notBefore: Long) {
            rows.values.removeAll { it.fetchedAtMillis <= notBefore }
        }

        fun age(millis: Long) {
            rows.replaceAll { _, row -> row.copy(fetchedAtMillis = row.fetchedAtMillis - millis) }
        }
    }
}
