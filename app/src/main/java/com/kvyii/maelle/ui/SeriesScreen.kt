package com.kvyii.maelle.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
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
    var downloadMenuOpen by remember { mutableStateOf(false) }
    var synopsisExpanded by remember { mutableStateOf(false) }

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
                        Box {
                            IconButton(onClick = { downloadMenuOpen = true }) {
                                Icon(Icons.Filled.Download, contentDescription = "Download options")
                            }
                            DropdownMenu(
                                expanded = downloadMenuOpen,
                                onDismissRequest = { downloadMenuOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Download next 5") },
                                    onClick = { vm.downloadUnread(5); downloadMenuOpen = false },
                                )
                                DropdownMenuItem(
                                    text = { Text("Download next 10") },
                                    onClick = { vm.downloadUnread(10); downloadMenuOpen = false },
                                )
                                DropdownMenuItem(
                                    text = { Text("Download next 25") },
                                    onClick = { vm.downloadUnread(25); downloadMenuOpen = false },
                                )
                                DropdownMenuItem(
                                    text = { Text("Download all unread") },
                                    onClick = { vm.downloadUnread(null); downloadMenuOpen = false },
                                )
                            }
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
            state.download?.let { download ->
                item {
                    DownloadBanner(
                        download = download,
                        onPause = vm::pauseDownload,
                        onResume = vm::resumeDownload,
                        onStop = vm::stopDownload,
                        onDismiss = vm::dismissDownload,
                    )
                }
            }
            item {
                Column(
                    Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    series?.posterUrl?.let { poster ->
                        AsyncImage(
                            model = poster,
                            contentDescription = series.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(150.dp)
                                .aspectRatio(0.7f)
                                .clip(RoundedCornerShape(12.dp)),
                        )
                    }
                    series?.author?.let {
                        Text(
                            "by $it",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                    Text(
                        "${state.chapters.size} chapters · ${state.readCount} read",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    series?.apiName?.let {
                        Text(
                            "from $it",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    series?.synopsis?.let {
                        // Tap to expand the full synopsis, tap again to collapse.
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = if (synopsisExpanded) Int.MAX_VALUE else 6,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clickable { synopsisExpanded = !synopsisExpanded },
                        )
                    }
                    // Resume picks up at the start of the most recently opened chapter
                    // (not the last *finished* one), so returning to the reader never
                    // requires scrolling back to find your spot.
                    val resumeChapter = state.chapters.firstOrNull { it.id == series?.lastReadChapterId }
                        ?: state.chapters.lastOrNull()
                    resumeChapter?.let { chapter ->
                        Button(
                            onClick = { onOpenChapter(chapter.id) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp),
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                "  Resume · ${chapter.name}",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
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
private fun DownloadBanner(
    download: com.kvyii.maelle.data.SeriesDownload,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (download.status) {
                        com.kvyii.maelle.data.DownloadStatus.Running ->
                            if (download.total == null) "Preparing downloads…"
                            else "Downloading ${download.done} / ${download.total}"
                        com.kvyii.maelle.data.DownloadStatus.Paused ->
                            "Paused at ${download.done}" + (download.total?.let { " / $it" } ?: "")
                        com.kvyii.maelle.data.DownloadStatus.Cancelled ->
                            "Stopped — ${download.done - download.failed.size} chapters saved"
                        com.kvyii.maelle.data.DownloadStatus.Finished ->
                            if (download.total == 0) "Nothing to download — all unread chapters are saved."
                            else "Downloaded ${download.done - download.failed.size} of ${download.total} chapters."
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
                if (download.failed.isNotEmpty()) {
                    Text(
                        "Failed after retries: ${download.failed.take(3).joinToString()}" +
                            if (download.failed.size > 3) " +${download.failed.size - 3} more" else "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (download.active) {
                if (download.status == com.kvyii.maelle.data.DownloadStatus.Paused) {
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
                androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Dismiss") }
            }
        }

        if (download.active) {
            val total = download.total
            if (total == null || total == 0) {
                androidx.compose.material3.LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
            } else {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { download.done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                )
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
            RelativeTime.format(chapter.dateOfRelease)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // A single trailing icon: a filled tick once downloaded, otherwise a
        // download affordance. Read/unread is conveyed by the greyed title, and
        // toggled via long-press — no separate read tick.
        IconButton(onClick = onDownload) {
            if (chapter.isDownloaded) {
                Icon(Icons.Filled.CheckCircle, contentDescription = "Downloaded", tint = MaterialTheme.colorScheme.primary)
            } else {
                Icon(Icons.Filled.Download, contentDescription = "Download")
            }
        }
    }
}
