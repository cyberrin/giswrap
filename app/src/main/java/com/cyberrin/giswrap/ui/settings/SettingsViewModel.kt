package com.cyberrin.giswrap.ui.settings

import android.os.Build
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cyberrin.giswrap.data.local.UserFileStore
import com.cyberrin.giswrap.domain.model.Appearance
import com.cyberrin.giswrap.domain.model.City
import com.cyberrin.giswrap.domain.repository.CityRepository
import com.cyberrin.giswrap.domain.repository.SettingsRepository
import com.cyberrin.giswrap.widget.WeatherWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class SettingsUiState(
    val appearance: Appearance = Appearance(),
    val savedCities: List<City> = emptyList(),
    val primaryPath: String? = null,
    val dynamicColourAvailable: Boolean = false,
)

sealed interface SettingsEvent {
    data class AppearanceChanged(val appearance: Appearance) : SettingsEvent

    data class AppearanceDragged(val appearance: Appearance) : SettingsEvent

    data class PrimaryPicked(val city: City) : SettingsEvent
    data class CityRemoved(val city: City) : SettingsEvent
    data class BackgroundPicked(val uri: String?) : SettingsEvent
    data object Back : SettingsEvent
}

sealed interface SettingsEffect {
    data object Close : SettingsEffect
    data class Message(val text: String) : SettingsEffect
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val cities: CityRepository,
    private val files: UserFileStore,
    private val widget: WeatherWidget.Updater,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SettingsUiState(
            dynamicColourAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
        )
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _effects = Channel<SettingsEffect>(Channel.BUFFERED)
    val effects: Flow<SettingsEffect> = _effects.receiveAsFlow()

    private var settleJob: Job? = null

    init {
        combine(settings.appearance, cities.saved, cities.primaryPath, ::Triple)
            .onEach { (appearance, saved, primary) ->
                _state.update {
                    it.copy(appearance = appearance, savedCities = saved, primaryPath = primary)
                }
            }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: SettingsEvent) = when (event) {
        is SettingsEvent.AppearanceChanged -> commit(event.appearance)
        is SettingsEvent.AppearanceDragged -> settle(event.appearance)
        is SettingsEvent.PrimaryPicked -> viewModelScope.launch {
            cities.setPrimary(event.city)
            widget.refresh()
        }.let { }
        is SettingsEvent.CityRemoved -> viewModelScope.launch {
            cities.remove(event.city)
            widget.refresh()
        }.let { }
        is SettingsEvent.BackgroundPicked -> setBackground(event.uri)
        SettingsEvent.Back -> viewModelScope.launch { _effects.send(SettingsEffect.Close) }.let { }
    }

    private fun commit(appearance: Appearance) {
        settleJob?.cancel()
        viewModelScope.launch {
            settings.update { appearance }
            widget.refresh()
        }
    }

    private fun settle(appearance: Appearance) {
        _state.update { it.copy(appearance = appearance) }
        settleJob?.cancel()
        settleJob = viewModelScope.launch {
            delay(QUIET_MS)
            settings.update { appearance }
            widget.refresh()
        }
    }

    private fun setBackground(uri: String?) {
        viewModelScope.launch {
            uri?.let(files::persistRead)
            settings.update { it.copy(backgroundUri = uri) }
        }
    }

    private companion object {
        const val QUIET_MS = 250L
    }
}
