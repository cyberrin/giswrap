package com.cyberrin.giswrap.ui.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.cyberrin.giswrap.domain.model.WeatherError
import com.cyberrin.giswrap.domain.model.City
import com.cyberrin.giswrap.domain.model.Outcome
import com.cyberrin.giswrap.domain.repository.CityRepository
import com.cyberrin.giswrap.domain.repository.WeatherRepository
import com.cyberrin.giswrap.ui.navigation.SearchRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: List<City> = emptyList(),
    val error: WeatherError? = null,
    val searched: Boolean = false,
)

sealed interface SearchEvent {
    data class QueryChanged(val value: String) : SearchEvent
    data object Submitted : SearchEvent
    data class CityPicked(val city: City) : SearchEvent
    data object Back : SearchEvent
}

sealed interface SearchEffect {
    data object Close : SearchEffect
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val weather: WeatherRepository,
    private val cities: CityRepository,
) : ViewModel() {
    private val route: SearchRoute = savedState.toRoute()

    private val _state = MutableStateFlow(SearchUiState(query = route.query))
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _effects = Channel<SearchEffect>(Channel.BUFFERED)
    val effects: Flow<SearchEffect> = _effects.receiveAsFlow()

    private var searchJob: Job? = null

    init {
        if (route.query.isNotBlank()) search(route.query)
    }

    fun onEvent(event: SearchEvent) = when (event) {
        is SearchEvent.QueryChanged -> _state.update { it.copy(query = event.value) }
        SearchEvent.Submitted -> search(_state.value.query.trim())
        is SearchEvent.CityPicked -> save(event.city)
        SearchEvent.Back -> close()
    }

    private fun search(query: String) {
        if (query.isEmpty()) return

        searchJob?.cancel()
        _state.update { it.copy(searching = true, error = null) }
        searchJob = viewModelScope.launch {
            when (val found = weather.search(query)) {
                is Outcome.Ok -> _state.update {
                    it.copy(searching = false, searched = true, results = found.value)
                }
                is Outcome.Failed -> _state.update {
                    it.copy(
                        searching = false,
                        searched = true,
                        results = emptyList(),
                        error = found.error,
                    )
                }
            }
        }
    }

    private fun save(city: City) {
        viewModelScope.launch {
            cities.save(city)
            cities.setPrimary(city)
            _effects.send(SearchEffect.Close)
        }
    }

    private fun close() {
        viewModelScope.launch { _effects.send(SearchEffect.Close) }
    }
}
