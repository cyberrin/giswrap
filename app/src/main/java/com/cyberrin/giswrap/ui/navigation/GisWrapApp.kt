package com.cyberrin.giswrap.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cyberrin.giswrap.domain.model.Appearance
import com.cyberrin.giswrap.domain.repository.SettingsRepository
import com.cyberrin.giswrap.ui.forecast.ForecastScreenRoute
import com.cyberrin.giswrap.ui.search.SearchScreenRoute
import com.cyberrin.giswrap.ui.settings.SettingsScreenRoute
import com.cyberrin.giswrap.ui.common.AppLanguageProvider
import com.cyberrin.giswrap.ui.theme.GisWrapTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable

@Serializable
object ForecastRoute

@Serializable
data class SearchRoute(val query: String = "")

@Serializable
object SettingsRoute

private const val SCREEN_IN = 180
private const val SCREEN_OUT = 120

@Composable
fun GisWrapApp() {
    val themeViewModel: ThemeViewModel = hiltViewModel()
    val appearance by themeViewModel.appearance.collectAsStateWithLifecycle()

    AppLanguageProvider(appearance.language) {
        GisWrapTheme(appearance = appearance) {
            val navController = rememberNavController()

            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                NavHost(
                    navController = navController,
                    startDestination = ForecastRoute,
                    enterTransition = { fadeIn(tween(SCREEN_IN)) + slideInVertically(tween(SCREEN_IN)) { it / 14 } },
                    exitTransition = { fadeOut(tween(SCREEN_OUT)) },
                    popEnterTransition = { fadeIn(tween(SCREEN_IN)) },
                    popExitTransition = { fadeOut(tween(SCREEN_OUT)) + slideOutVertically(tween(SCREEN_OUT)) { it / 14 } },
                ) {
                    composable<ForecastRoute> {
                        ForecastScreenRoute(
                            appearance = appearance,
                            onOpenSearch = { query -> navController.navigate(SearchRoute(query)) },
                            onOpenSettings = { navController.navigate(SettingsRoute) },
                        )
                    }
                    composable<SearchRoute> {
                        SearchScreenRoute(onClose = { navController.popBackStack() })
                    }
                    composable<SettingsRoute> {
                        SettingsScreenRoute(onClose = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}

@HiltViewModel
class ThemeViewModel @Inject constructor(
    settings: SettingsRepository,
) : ViewModel() {
    val appearance: StateFlow<Appearance> = settings.appearance.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = Appearance(),
    )
}
