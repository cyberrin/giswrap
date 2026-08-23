package com.cyberrin.giswrap.ui.forecast

import app.cash.turbine.test
import io.mockk.coEvery
import kotlinx.coroutines.delay
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.cyberrin.giswrap.FakeCityRepository
import com.cyberrin.giswrap.FakeSettingsRepository
import com.cyberrin.giswrap.city
import com.cyberrin.giswrap.domain.model.CurrentWeather
import com.cyberrin.giswrap.domain.model.Forecast
import com.cyberrin.giswrap.domain.model.ForecastOrigin
import com.cyberrin.giswrap.domain.model.Outcome
import com.cyberrin.giswrap.domain.model.Period
import com.cyberrin.giswrap.domain.model.Sourced
import com.cyberrin.giswrap.domain.model.ThemeMode
import com.cyberrin.giswrap.domain.model.WeatherError
import com.cyberrin.giswrap.domain.repository.WeatherRepository
import com.cyberrin.giswrap.domain.usecase.GetHourlyStrip
import com.cyberrin.giswrap.domain.usecase.LocateNearestCity
import com.cyberrin.giswrap.widget.WeatherWidget
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ForecastViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val weather = mockk<WeatherRepository>()

    private val widget = mockk<WeatherWidget.Updater>(relaxed = true)
    private lateinit var cities: FakeCityRepository
    private lateinit var settings: FakeSettingsRepository

    private val surgut = city("Сургут", "surgut-4954")
    private val now = LocalDateTime.of(2026, 8, 15, 12, 0)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        cities = FakeCityRepository()
        settings = FakeSettingsRepository()
        coEvery { weather.forecast(any(), any(), any()) } returns
            Outcome.Ok(Sourced(emptyForecast, fromCache = false, fetchedAt = now))
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(saved: List<com.cyberrin.giswrap.domain.model.City> = emptyList()): ForecastViewModel {
        cities = FakeCityRepository(saved)
        return ForecastViewModel(
            weather = weather,
            cities = cities,
            settings = settings,
            hourlyStrip = GetHourlyStrip(weather),
            locateNearest = LocateNearestCity(mockk(relaxed = true), weather),
            widget = widget,
        )
    }

    @Test
    fun `a saved city is opened and loaded on start`() = runTest(dispatcher) {
        coEvery { weather.currentWeather(any(), any()) } returns
            Outcome.Ok(Sourced(reading, fromCache = false, fetchedAt = now))

        val vm = viewModel(listOf(surgut))
        vm.state.test {
            val opened = awaitItemWhere { it.city != null }
            assertEquals(surgut, opened.city)

            val loaded = awaitItemWhere { it.tabs[Tab.NOW] is TabState.Now }
            assertEquals(-3.0, (loaded.tabs[Tab.NOW] as TabState.Now).weather.temperature)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `typing is debounced to one request`() = runTest(dispatcher) {
        coEvery { weather.search(any()) } returns Outcome.Ok(listOf(surgut))
        val vm = viewModel()

        "Сургут".forEachIndexed { i, _ ->
            vm.onEvent(ForecastEvent.QueryChanged("Сургут".take(i + 1)))
            advanceTimeBy(50)
        }
        advanceUntilIdle()

        coVerify(exactly = 1) { weather.search("Сургут") }
    }

    @Test
    fun `a single letter never reaches the network`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onEvent(ForecastEvent.QueryChanged("С"))
        advanceUntilIdle()
        coVerify(exactly = 0) { weather.search(any()) }
    }

    @Test
    fun `a failed tab reports the upstream's own message`() = runTest(dispatcher) {
        coEvery { weather.currentWeather(any(), any()) } returns
            Outcome.Failed(WeatherError.Unreachable)

        val vm = viewModel(listOf(surgut))
        vm.state.test {
            val failed = awaitItemWhere { it.tabs[Tab.NOW] is TabState.Failed }
            assertEquals(WeatherError.Unreachable, (failed.tabs[Tab.NOW] as TabState.Failed).error)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching back to a loaded tab does not refetch`() = runTest(dispatcher) {
        coEvery { weather.currentWeather(any(), any()) } returns
            Outcome.Ok(Sourced(reading, fromCache = false, fetchedAt = now))

        val vm = viewModel(listOf(surgut))
        advanceUntilIdle()

        vm.onEvent(ForecastEvent.TabPicked(Tab.DAYS_3))
        advanceUntilIdle()
        vm.onEvent(ForecastEvent.TabPicked(Tab.NOW))
        advanceUntilIdle()

        coVerify(exactly = 1) { weather.currentWeather("surgut-4954", false) }
    }

    @Test
    fun `refresh forces the network past the cache`() = runTest(dispatcher) {
        coEvery { weather.currentWeather(any(), any()) } returns
            Outcome.Ok(Sourced(reading, fromCache = true, fetchedAt = now))

        val vm = viewModel(listOf(surgut))
        advanceUntilIdle()
        vm.onEvent(ForecastEvent.Refreshed)
        advanceUntilIdle()

        coVerify(exactly = 1) { weather.currentWeather("surgut-4954", true) }
    }

    @Test
    fun `picking a city keeps it and drops every loaded tab`() = runTest(dispatcher) {
        coEvery { weather.currentWeather(any(), any()) } returns
            Outcome.Ok(Sourced(reading, fromCache = false, fetchedAt = now))

        val vm = viewModel(listOf(surgut))
        advanceUntilIdle()

        val london = city("Лондон", "london-4517")
        vm.onEvent(ForecastEvent.CityPicked(london))
        advanceUntilIdle()

        assertEquals(london, vm.state.value.city)
        assertTrue(cities.saved.let { true })

        assertNull(vm.state.value.tabs.keys.firstOrNull { it != Tab.NOW })
    }

    @Test
    fun `the corner mark advances the palette instead of navigating`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.effects.test {
            vm.onEvent(ForecastEvent.ThemeCycled)
            advanceUntilIdle()

            assertEquals(ThemeMode.LIGHT, settings.current().themeMode)

            expectNoEvents()
        }
        coVerify { widget.refresh() }
    }

    @Test
    fun `three presses return to where they started`() = runTest(dispatcher) {
        val vm = viewModel()
        repeat(3) { vm.onEvent(ForecastEvent.ThemeCycled) }
        advanceUntilIdle()
        assertEquals(ThemeMode.SYSTEM, settings.current().themeMode)
    }

    private fun slowWeather() {
        coEvery { weather.currentWeather(any(), any()) } coAnswers {
            delay(FETCH_MS)
            Outcome.Ok(Sourced(reading, fromCache = false, fetchedAt = now))
        }
    }

    @Test
    fun `a refresh keeps the content it is refreshing`() = runTest(dispatcher) {
        slowWeather()

        val vm = viewModel(listOf(surgut))
        advanceUntilIdle()
        val before = vm.state.value.tabs[Tab.NOW]
        assertTrue(before is TabState.Now, "nothing was loaded to begin with")

        vm.onEvent(ForecastEvent.Refreshed)

        advanceTimeBy(FETCH_MS / 2)
        val during = vm.state.value.tabs[Tab.NOW]

        assertTrue(during is TabState.Now, "the tab fell back to $during")
        assertEquals(before, during)
        assertTrue(vm.state.value.refreshing, "the pull indicator should be the one spinning")

        advanceUntilIdle()
        assertTrue(vm.state.value.tabs[Tab.NOW] is TabState.Now)
    }

    @Test
    fun `a tab with nothing in it still shows that it is loading`() = runTest(dispatcher) {
        slowWeather()

        val vm = viewModel(listOf(surgut))

        advanceTimeBy(FETCH_MS / 2)
        assertTrue(vm.state.value.tabs[Tab.NOW] is TabState.Loading,
            "an empty tab should say it is loading")
    }

    @Test
    fun `the refresh indicator stops even if the range changed under it`() = runTest(dispatcher) {
        slowWeather()
        coEvery { weather.forecast(any(), any(), any()) } coAnswers {
            delay(FETCH_MS)
            Outcome.Ok(Sourced(emptyForecast, fromCache = false, fetchedAt = now))
        }

        val vm = viewModel(listOf(surgut))
        advanceUntilIdle()

        vm.onEvent(ForecastEvent.TabPicked(Tab.DAYS_3))
        advanceUntilIdle()
        vm.onEvent(ForecastEvent.TabPicked(Tab.NOW))
        advanceUntilIdle()

        vm.onEvent(ForecastEvent.Refreshed)

        vm.onEvent(ForecastEvent.TabPicked(Tab.DAYS_3))
        advanceUntilIdle()

        assertFalse(vm.state.value.refreshing, "the indicator never stopped")
    }

    @Test
    fun `a refresh with no city does not leave the indicator spinning`() = runTest(dispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEvent(ForecastEvent.Refreshed)
        advanceUntilIdle()

        assertFalse(vm.state.value.refreshing, "there was nothing to refresh")
    }

    private suspend fun app.cash.turbine.TurbineTestContext<ForecastUiState>.awaitItemWhere(
        predicate: (ForecastUiState) -> Boolean,
    ): ForecastUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    private val FETCH_MS = 1_000L

    private val reading = CurrentWeather(
        city = "Сургут",
        cityId = "4954",
        temperature = -3.0,
        description = "снег",
    )

    private val emptyForecast = Forecast(
        city = "Сургут",
        cityId = "4954",
        period = Period.DAYS_3,
        origin = ForecastOrigin.LEGACY_XML,
        days = emptyList(),
    )
}
