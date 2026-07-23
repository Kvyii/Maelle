package com.kvyii.maelle.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kvyii.maelle.AppContainer
import com.kvyii.maelle.data.db.ChapterEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SeriesScreen(
    container: AppContainer,
    seriesId: Long,
    onBack: () -> Unit,
    onOpenChapter: (Long) -> Unit,
) {
    val vm: SeriesViewModel = viewModel(
        factory = MaelleViewModelFactory(container, mapOf("seriesId" to seriesId)),
        key = "series-$seriesId",
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val series = state.series
    var markSheetFor by remember { mutableStateOf<ChapterEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(series?.name ?: "Loading…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (series != null) {
                        IconButton(onClick = vm::downloadAllUnread) {
                            Icon(Icons.Filled.Download, contentDescription = "Download all unread")
                        }
                        IconButton(onClick = vm::toggleLibrary) {
                            if (series.inLibrary) {
                                Icon(Icons.Filled.Star, contentDescription = "In library")
                            } else {
                                Icon(Icons.Outlined.StarBorder, contentDescription = "Add to library")
                            }
                        }
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            state.downloadProgress?.let { progress ->
                item {
                    DownloadBanner(progress, onDismiss = vm::dismissDownloadProgress)
                }
            }
            item {
                Column(Modifier.padding(16.dp)) {
                    series?.author?.let {
                        Text("by $it", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "${state.chapters.size} chapters · ${state.readCount} read",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    series?.synopsis?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                HorizontalDivider()
            }

            // Chapters are already newest-first from the DAO.
            items(state.chapters, key = { it.id }) { chapter ->
                ChapterRow(
                    chapter = chapter,
                    onClick = { onOpenChapter(chapter.id) },
                    onLongClick = { markSheetFor = chapter },
                    onToggleRead = { vm.toggleRead(chapter) },
                    onDownload = { vm.downloadChapter(chapter) },
                )
                HorizontalDivider()
            }
        }
    }

    markSheetFor?.let { chapter ->
        MarkRangeSheet(
            chapter = chapter,
            onAction = { below, read ->
                vm.markRange(chapter, below = below, read = read)
                markSheetFor = null
            },
            onDismiss = { markSheetFor = null },
        )
    }
}

/**
 * Long-press sheet: pick a direction (this & below = older, this & above =
 * newer) and a state (read/unread). Ranges always include the pressed chapter.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkRangeSheet(
    chapter: ChapterEntity,
    onAction: (below: Boolean, read: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(bottom = 24.dp)) {
            Text(
                chapter.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            SheetAction("Mark this & all below as read", Icons.Filled.CheckCircle) {
                onAction(true, true)
            }
            SheetAction("Mark this & all below as unread", Icons.Filled.RadioButtonUnchecked) {
                onAction(true, false)
            }
            HorizontalDivider()
            SheetAction("Mark this & all above as read", Icons.Filled.CheckCircle) {
                onAction(false, true)
            }
            SheetAction("Mark this & all above as unread", Icons.Filled.RadioButtonUnchecked) {
                onAction(false, false)
            }
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(label, modifier = Modifier.padding(start = 16.dp))
    }
}

@Composable
private fun DownloadBanner(progress: DownloadProgress, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        when {
            !progress.finished -> {
                val total = if (progress.total == Int.MAX_VALUE) null else progress.total
                Text(
                    if (total == null) "Preparing downloads…"
                    else "Downloading ${progress.done} / $total",
                    style = MaterialTheme.typography.labelLarge,
                )
                if (total == null) {
                    androidx.compose.material3.LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                } else {
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progress.done.toFloat() / total.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }

            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (progress.total == 0) "Nothing to download — all unread chapters are saved."
                        else "Downloaded ${progress.total - progress.failed.size} of ${progress.total} chapters.",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (progress.failed.isNotEmpty()) {
                        Text(
                            "Failed after retries: ${progress.failed.take(3).joinToString()}" +
                                if (progress.failed.size > 3) " +${progress.failed.size - 3} more" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterRow(
    chapter: ChapterEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleRead: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                chapter.name,
                style = MaterialTheme.typography.bodyMedium,
                color = if (chapter.isRead) {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            chapter.dateOfRelease?.let {
                Text(it, style = MaterialTheme.typography.labelSmall)
            }
        }
        IconButton(onClick = onDownload) {
            if (chapter.isDownloaded) {
                Icon(Icons.Filled.DownloadDone, contentDescription = "Downloaded", tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Filled.Download, contentDescription = "Download")
            }
        }
        IconButton(onClick = onToggleRead) {
            if (chapter.isRead) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Read", tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Filled.RadioButtonUnchecked, contentDescription = "Unread")
            }
        }
    }
}
