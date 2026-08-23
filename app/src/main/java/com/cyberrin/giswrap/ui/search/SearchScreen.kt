package com.cyberrin.giswrap.ui.search

import com.cyberrin.giswrap.ui.common.text
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cyberrin.giswrap.R
import com.cyberrin.giswrap.ui.art.*
import com.cyberrin.giswrap.ui.common.*
import com.cyberrin.giswrap.ui.theme.*
import kotlinx.coroutines.flow.collect

@Composable
fun SearchScreenRoute(
    onClose: () -> Unit,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SearchEffect.Close -> onClose()
            }
        }
    }

    BackHandler { viewModel.onEvent(SearchEvent.Back) }

    Surface(
        Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding()) {
            SearchPage(state = state, onEvent = viewModel::onEvent)
        }
    }
}

@Composable
internal fun SearchPage(state: SearchUiState, onEvent: (SearchEvent) -> Unit) {
    val searchError = state.error
    when {
        state.searching && state.results.isEmpty() -> Loading()
        searchError != null -> Notice(searchError.text(), error = true)
        state.results.isEmpty() && state.searched -> Notice(stringResource(R.string.search_nothing_found, state.query))
        else -> LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.search_results_count, state.results.size, state.query),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }
            items(state.results, key = { it.urlPath }, contentType = { "city" }) { city ->
                SoftCard(onClick = { onEvent(SearchEvent.CityPicked(city)) }, identity = city.urlPath) {
                    Text(city.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        city.where.ifBlank { "—" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
