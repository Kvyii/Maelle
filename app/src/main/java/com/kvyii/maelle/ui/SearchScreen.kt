package com.kvyii.maelle.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.kvyii.maelle.AppContainer
import com.kvyii.maelle.core.SearchResponse
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(container: AppContainer, onOpenSeries: (Long) -> Unit) {
    val vm: SearchViewModel = viewModel(factory = MaelleViewModelFactory(container))
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var providerMenuOpen by remember { mutableStateOf(false) }
    var opening by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { TopAppBar(title = { Text("Search") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            Box {
                OutlinedButton(onClick = { providerMenuOpen = true }) {
                    Text(state.provider)
                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Choose provider")
                }
                DropdownMenu(expanded = providerMenuOpen, onDismissRequest = { providerMenuOpen = false }) {
                    vm.providerNames.forEach { name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                vm.setProvider(name)
                                providerMenuOpen = false
                            },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.query,
                onValueChange = vm::setQuery,
                label = { Text("Search ${state.provider}") },
                singleLine = true,
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { vm.search() }
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )

            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error)
                }

                state.results.isEmpty() && state.showingPopular ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Type above to search ${state.provider}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.showingPopular) {
                        item {
                            Text(
                                "Popular on ${state.provider}",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                    items(state.results, key = { it.url }) { result ->
                        SearchResultRow(
                            result = result,
                            loading = opening == result.url,
                            onClick = {
                                opening = result.url
                                scope.launch {
                                    try {
                                        val id = vm.open(result, addToLibrary = true)
                                        onOpenSeries(id)
                                    } finally {
                                        opening = null
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: SearchResponse, loading: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !loading, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = result.posterUrl,
            contentDescription = result.name,
            modifier = Modifier.size(width = 48.dp, height = 64.dp).clip(RoundedCornerShape(4.dp)),
        )
        Column(Modifier.padding(start = 12.dp).fillMaxWidth()) {
            Text(result.name, style = MaterialTheme.typography.bodyLarge, maxLines = 2)
            result.latestChapter?.let {
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (loading) {
            CircularProgressIndicator(Modifier.size(20.dp))
        }
    }
}
