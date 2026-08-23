package com.cyberrin.giswrap.ui.settings

import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import com.cyberrin.giswrap.FakeCityRepository
import com.cyberrin.giswrap.FakeSettingsRepository
import com.cyberrin.giswrap.widget.WeatherWidget

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private lateinit var settings: FakeSettingsRepository
    private lateinit var viewModel: SettingsViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        settings = FakeSettingsRepository()
        viewModel = SettingsViewModel(
            settings = settings,
            cities = FakeCityRepository(),
            files = mockk(relaxed = true),
            widget = mockk<WeatherWidget.Updater>(relaxed = true),
        )
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun binding() = SettingsBinding(
        appearance = viewModel.state.value.appearance,
        savedCities = emptyList(),
        primaryPath = null,
        dynamicColorSupported = true,
        dark = false,
        onEvent = viewModel::onEvent,
    )

    @Test
    fun `the widget border switch goes both ways`() = runTest(dispatcher) {
        advanceUntilIdle()

        binding().onToggleWidgetBorder(true)
        advanceUntilIdle()
        assertTrue(settings.current().widget.border, "turning it on did not stick")
        assertTrue(viewModel.state.value.appearance.widget.border, "the switch did not follow")

        binding().onToggleWidgetBorder(false)
        advanceUntilIdle()
        assertFalse(settings.current().widget.border, "turning it off did not stick")
        assertFalse(viewModel.state.value.appearance.widget.border, "the switch did not follow")
    }

    @Test
    fun `one switch does not reset another`() = runTest(dispatcher) {
        advanceUntilIdle()
        binding().onToggleWidgetBorder(true)
        advanceUntilIdle()
        binding().onToggleWidgetHeroShape(true)
        advanceUntilIdle()

        assertTrue(settings.current().widget.border, "the border was lost")
        assertTrue(settings.current().widget.heroShape, "the hero shape was lost")
    }
}
