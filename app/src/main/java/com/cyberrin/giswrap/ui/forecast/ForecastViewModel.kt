package com.cyberrin.giswrap.ui.forecast

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyberrin.giswrap.domain.model.WeatherError
import com.cyberrin.giswrap.R
import com.cyberrin.giswrap.domain.model.City
import com.cyberrin.giswrap.domain.model.CurrentWeather
import com.cyberrin.giswrap.domain.model.Forecast
import com.cyberrin.giswrap.domain.model.HourlyForecast
import com.cyberrin.giswrap.domain.model.Outcome
import com.cyberrin.giswrap.domain.model.Period
import com.cyberrin.giswrap.domain.repository.CityRepository
import com.cyberrin.giswrap.domain.repository.SettingsRepository
import com.cyberrin.giswrap.domain.repository.WeatherRepository
import com.cyberrin.giswrap.domain.usecase.GetHourlyStrip
import com.cyberrin.giswrap.domain.usecase.LocateNearestCity
import com.cyberrin.giswrap.widget.WeatherWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDateTime
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class ForecastUiState(
    val city: City? = null,
    val tab: Tab = Tab.NOW,
    val tabs: Map<Tab, TabState> = emptyMap(),
    val query: String = "",
    val searching: Boolean = false,
    val locating: Boolean = false,
    val suggestionsOpen: Boolean = false,
    val suggestions: List<City> = emptyList(),
    val searchError: WeatherError? = null,
    val searched: Boolean = false,
    val refreshing: Boolean = false,
    val askLocation: Boolean = false,
    val savedCities: List<City> = emptyList(),
) {
    val current: TabState? get() = tabs[tab]
}

@Immutable
sealed interface TabState {
    data object Loading : TabState

    data class Failed(val error: WeatherError) : TabState

    data class Now(
        val weather: CurrentWeather,
        val cached: Boolean,
        val fetchedAt: LocalDateTime,
        val hours: List<HourlyForecast> = emptyList(),
    ) : TabState

    data class Days(
        val forecast: Forecast,
        val cached: Boolean,
        val fetchedAt: LocalDateTime,
    ) : TabState
}

sealed interface ForecastEvent {
    data class QueryChanged(val value: String) : ForecastEvent
    data object SearchSubmitted : ForecastEvent
    data object SuggestionsDismissed : ForecastEvent
    data class CityPicked(val city: City) : ForecastEvent
    data class TabPicked(val tab: Tab) : ForecastEvent
    data object Refreshed : ForecastEvent
    data object LocateRequested : ForecastEvent
    data object LocationDenied : ForecastEvent
    data object LocationPromptShown : ForecastEvent
    data object SettingsOpened : ForecastEvent

    data object ThemeCycled : ForecastEvent
}

sealed interface ForecastEffect {
    data class OpenSearch(val query: String) : ForecastEffect
    data object OpenSettings : ForecastEffect
    data object AskLocationPermission : ForecastEffect
}

enum class Tab(
    @param:StringRes val shortRes: Int,
    val period: Period?,
) {
    NOW(R.string.tab_now_short, null),
    DAYS_3(R.string.tab_3_days_short, Period.DAYS_3),
    WEEKS_2(R.string.tab_2_weeks_short, Period.WEEKS_2),
    MONTH(R.string.tab_month_short, Period.MONTH),
}

@HiltViewModel
class ForecastViewModel @Inject constructor(
    private val weather: WeatherRepository,
    private val cities: CityRepository,
    private val settings: SettingsRepository,
    private val hourlyStrip: GetHourlyStrip,
    private val locateNearest: LocateNearestCity,
    private val widget: WeatherWidget.Updater,
) : ViewModel() {
    private val _state = MutableStateFlow(ForecastUiState())
    val state: StateFlow<ForecastUiState> = _state.asStateFlow()

    private val _effects = Channel<ForecastEffect>(Channel.BUFFERED)
    val effects: Flow<ForecastEffect> = _effects.receiveAsFlow()

    private val fetchJobs = mutableMapOf<Tab, Job>()

    private var searchJob: Job? = null

    init {
        cities.saved
            .onEach { saved -> _state.update { it.copy(savedCities = saved) } }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val primary = cities.primary()
            when {
                primary != null -> {
                    _state.update { it.copy(city = primary, query = primary.name) }
                    loadTab(Tab.NOW)
                }

                !settings.locationAsked.first() ->
                    _state.update { it.copy(askLocation = true) }
            }
        }
    }

    fun onEvent(event: ForecastEvent) = when (event) {
        is ForecastEvent.QueryChanged -> onQueryChanged(event.value)
        ForecastEvent.SearchSubmitted -> submitSearch()
        ForecastEvent.SuggestionsDismissed ->
            _state.update { it.copy(suggestionsOpen = false) }
        is ForecastEvent.CityPicked -> pick(event.city)
        is ForecastEvent.TabPicked -> selectTab(event.tab)
        ForecastEvent.Refreshed -> refresh()
        ForecastEvent.LocateRequested -> locate()
        ForecastEvent.LocationDenied -> _state.update {
            it.copy(locating = false, searchError = WeatherError.LocationDenied)
        }
        ForecastEvent.LocationPromptShown -> markAsked()
        ForecastEvent.SettingsOpened -> emit(ForecastEffect.OpenSettings)
        ForecastEvent.ThemeCycled -> cycleTheme()
    }

    private fun cycleTheme() {
        viewModelScope.launch {
            settings.update { it.copy(themeMode = it.themeMode.next()) }
            widget.refresh()
        }
    }

    private fun emit(effect: ForecastEffect) {
        viewModelScope.launch { _effects.send(effect) }
    }

    private fun onQueryChanged(value: String) {
        _state.update { it.copy(query = value, suggestionsOpen = value.isNotBlank()) }

        searchJob?.cancel()
        val query = value.trim()
        if (query.length < MIN_QUERY) {
            _state.update {
                it.copy(searching = false, searched = false, suggestions = emptyList(), searchError = null)
            }
            return
        }
        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            runSearch(query)
        }
    }

    private fun submitSearch() {
        searchJob?.cancel()
        val query = _state.value.query.trim()
        if (query.isEmpty()) return
        _state.update { it.copy(suggestionsOpen = false) }
        emit(ForecastEffect.OpenSearch(query))
    }

    private suspend fun runSearch(query: String) {
        _state.update { it.copy(searching = true, searchError = null) }
        when (val found = weather.search(query)) {
            is Outcome.Ok -> {
                if (_state.value.query.trim() != query) return
                _state.update {
                    it.copy(searching = false, searched = true, suggestions = found.value)
                }
            }
            is Outcome.Failed -> _state.update {
                it.copy(
                    searching = false,
                    searched = true,
                    suggestions = emptyList(),
                    searchError = found.error,
                )
            }
        }
    }

    private fun locate() {
        searchJob?.cancel()
        _state.update { it.copy(locating = true, searchError = null) }
        searchJob = viewModelScope.launch {
            when (val located = locateNearest()) {
                is Outcome.Ok -> {
                    _state.update {
                        it.copy(
                            locating = false,
                            searched = true,
                            query = located.value.query,
                            suggestions = located.value.alternatives,
                        )
                    }
                    pick(located.value.nearest)
                }
                is Outcome.Failed -> _state.update {
                    it.copy(locating = false, searchError = located.error)
                }
            }
        }
    }

    private fun markAsked() {
        viewModelScope.launch {
            settings.markLocationAsked()
            _state.update { it.copy(askLocation = false) }
        }
    }

    private fun pick(city: City) {
        cancelFetches()
        viewModelScope.launch { cities.save(city) }
        _state.update {
            it.copy(
                city = city,
                tabs = emptyMap(),
                suggestionsOpen = false,
                searching = false,
                askLocation = false,
                query = city.name,
            )
        }
        loadTab(_state.value.tab)
    }

    private fun selectTab(tab: Tab) {
        _state.update { it.copy(tab = tab) }
        loadTab(tab)
    }

    private fun refresh() {
        val tab = _state.value.tab

        fetchJobs.remove(tab)?.cancel()

        if (_state.value.city == null) return

        _state.update { it.copy(refreshing = true) }
        loadTab(tab, force = true)
    }

    private fun cancelFetches() {
        fetchJobs.values.forEach(Job::cancel)
        fetchJobs.clear()
    }

    private fun loadTab(tab: Tab, force: Boolean = false) {
        val city = _state.value.city ?: return
        val existing = _state.value.tabs[tab]
        if (!force && (existing is TabState.Now || existing is TabState.Days)) return

        if (fetchJobs[tab]?.isActive == true) return

        if (existing !is TabState.Now && existing !is TabState.Days) {
            putTab(tab, TabState.Loading)
        }
        fetchJobs[tab] = viewModelScope.launch {
            try {
                val period = tab.period
                val loaded = if (period == null) {
                    when (val got = weather.currentWeather(city.urlPath, refresh = force)) {
                        is Outcome.Ok -> TabState.Now(
                            weather = got.value.value,
                            cached = got.value.fromCache,
                            fetchedAt = got.value.fetchedAt,
                            hours = hourlyStrip(city.urlPath),
                        )
                        is Outcome.Failed -> TabState.Failed(got.error)
                    }
                } else {
                    when (val got = weather.forecast(city.urlPath, period, refresh = force)) {
                        is Outcome.Ok -> TabState.Days(
                            forecast = got.value.value,
                            cached = got.value.fromCache,
                            fetchedAt = got.value.fetchedAt,
                        )
                        is Outcome.Failed -> TabState.Failed(got.error)
                    }
                }

                if (_state.value.city?.urlPath == city.urlPath) putTab(tab, loaded)
            } finally {
                fetchJobs.remove(tab)

                if (force) _state.update { it.copy(refreshing = false) }
            }
        }
    }

    private fun putTab(tab: Tab, tabState: TabState) =
        _state.update { it.copy(tabs = it.tabs + (tab to tabState)) }

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val MIN_QUERY = 2
    }
}
