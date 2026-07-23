package com.kvyii.maelle.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kvyii.maelle.AppContainer
import com.kvyii.maelle.data.DownloadStatus
import com.kvyii.maelle.data.SeriesDownload
import com.kvyii.maelle.data.db.SeriesDownloadCount

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(container: AppContainer, onOpenSeries: (Long) -> Unit) {
    val vm: DownloadsViewModel = viewModel(factory = MaelleViewModelFactory(container))
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Downloads") }) }) { padding ->
        if (state.active.isEmpty() && state.downloaded.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "Nothing downloaded yet.\nUse the download button on a series to save chapters offline.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Scaffold
        }

        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            if (state.active.isNotEmpty()) {
                item { SectionHeader("Downloading now") }
                items(state.active, key = { "active-${it.seriesId}" }) { download ->
                    ActiveDownloadRow(
                        download = download,
                        onPause = { vm.pause(download.seriesId) },
                        onResume = { vm.resume(download.seriesId) },
                        onStop = { vm.stop(download.seriesId) },
                        onClear = { vm.clear(download.seriesId) },
                        onOpen = { onOpenSeries(download.seriesId) },
                    )
                    HorizontalDivider()
                }
            }
            if (state.downloaded.isNotEmpty()) {
                item { SectionHeader("Saved for offline") }
                items(state.downloaded, key = { "saved-${it.seriesId}" }) { entry ->
                    SavedRow(entry, onOpen = { onOpenSeries(entry.seriesId) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun ActiveDownloadRow(
    download: SeriesDownload,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    download.seriesName,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when (download.status) {
                        DownloadStatus.Running ->
                            if (download.total == null) "Preparing…"
                            else "Downloading ${download.done} / ${download.total}"
                        DownloadStatus.Paused ->
                            "Paused at ${download.done}" +
                                (download.total?.let { " / $it" } ?: "")
                        DownloadStatus.Cancelled ->
                            "Stopped — ${download.done - download.failed.size} saved"
                        DownloadStatus.Finished ->
                            if (download.failed.isEmpty()) "Done — ${download.done} saved"
                            else "Done — ${download.done - download.failed.size} saved, ${download.failed.size} failed"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = if (download.failed.isNotEmpty() && !download.active) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (download.active) {
                if (download.status == DownloadStatus.Paused) {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Resume")
                    }
                } else {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Filled.Pause, contentDescription = "Pause")
                    }
                }
                IconButton(onClick = onStop) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop")
                }
            } else {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss")
                }
            }
        }

        if (download.active) {
            val total = download.total
            if (total == null || total == 0) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp))
            } else {
                LinearProgressIndicator(
                    progress = { download.done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun SavedRow(entry: SeriesDownloadCount, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${entry.downloaded} of ${entry.total} chapters downloaded",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
