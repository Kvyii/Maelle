package com.kvyii.maelle.ui

import android.app.Activity
import android.graphics.Typeface
import android.util.TypedValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kvyii.maelle.AppContainer
import com.kvyii.maelle.data.ReaderFont

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    container: AppContainer,
    seriesId: Long,
    chapterId: Long,
    onBack: () -> Unit,
) {
    val vm: ReaderViewModel = viewModel(
        factory = MaelleViewModelFactory(container, mapOf("seriesId" to seriesId, "chapterId" to chapterId)),
        key = "reader-$seriesId-$chapterId",
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val prefs by vm.preferences.collectAsStateWithLifecycle()

    val typeface = when (prefs.font) {
        ReaderFont.Sans -> Typeface.SANS_SERIF
        ReaderFont.Serif -> Typeface.SERIF
        ReaderFont.Mono -> Typeface.MONOSPACE
    }
    val composeFont = when (prefs.font) {
        ReaderFont.Sans -> FontFamily.SansSerif
        ReaderFont.Serif -> FontFamily.Serif
        ReaderFont.Mono -> FontFamily.Monospace
    }
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()

    val listState = rememberLazyListState()
    val sheetState = rememberModalBottomSheetState()

    // Current selection captured from a native SelectableTextView (Cassie-style);
    // when non-empty, the Explain bar appears.
    var selection by remember { mutableStateOf("") }
    fun dismissSelection() { selection = "" }

    // Infinite scroll: append the next chapter as we approach the bottom, and
    // prepend the previous one as we approach the top.
    LaunchedEffect(listState, state.loaded.size) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val total = layout.totalItemsCount
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: 0
            val firstVisible = layout.visibleItemsInfo.firstOrNull()?.index ?: 0
            Triple(total, lastVisible, firstVisible)
        }.collect { (total, lastVisible, firstVisible) ->
            if (total > 0 && lastVisible >= total - 2) vm.loadNext()
            if (firstVisible <= 1) vm.loadPrevious()
        }
    }

    // Keep the top-bar title in sync with whichever chapter heading is on top.
    LaunchedEffect(listState, state.loaded) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .collect { idx ->
                state.loaded.getOrNull(idx)?.let { vm.onChapterVisible(it.id) }
            }
    }

    // Mark a chapter read only once its body has been fully scrolled past — i.e.
    // the reader reached the bottom of that chapter, not merely opened it.
    LaunchedEffect(listState) {
        snapshotFlow {
            val layout = listState.layoutInfo
            val viewportEnd = layout.viewportEndOffset
            // Body items whose bottom edge is at/above the viewport bottom.
            layout.visibleItemsInfo
                .filter { (it.key as? String)?.startsWith("body-") == true }
                .filter { it.offset + it.size <= viewportEnd }
                .mapNotNull { (it.key as? String)?.removePrefix("body-")?.toLongOrNull() }
                .toSet()
        }.collect { finishedIds ->
            finishedIds.forEach { vm.onChapterFinished(it) }
        }
    }

    // Immersive full-screen while reading: hide the system bars on enter and
    // restore them on exit. Exit is the phone Back button / gesture (nav pop).
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    Box(Modifier.fillMaxSize()) {
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error)
            }

            else -> Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = WindowInsets.systemBars.asPaddingValues().let { bars ->
                        PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = bars.calculateTopPadding() + 16.dp,
                            bottom = bars.calculateBottomPadding() + 24.dp,
                        )
                    },
                ) {
                    if (state.prepending) {
                        item(key = "loading-top") { LoadingRow() }
                    }

                    state.loaded.forEachIndexed { i, chapter ->
                        // A thin divider separates one chapter from the next.
                        if (i > 0) {
                            item(key = "divider-${chapter.id}") {
                                HorizontalDivider(Modifier.padding(vertical = 20.dp))
                            }
                        }

                        item(key = "title-${chapter.id}") {
                            Text(
                                chapter.name,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontFamily = composeFont,
                                    fontWeight = FontWeight.Bold,
                                ),
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                            )
                        }

                        item(key = "body-${chapter.id}") {
                            val bodyText = remember(chapter.id, chapter.body) {
                                htmlToParagraphs(chapter.body).joinToString("\n\n")
                            }
                            ChapterBody(
                                text = bodyText,
                                typeface = typeface,
                                textColor = textColor,
                                fontSizeSp = prefs.fontSize.toFloat(),
                                onSelectionChanged = { selection = it },
                            )
                        }
                    }

                    if (state.appending) {
                        item(key = "loading-bottom") { LoadingRow() }
                    } else if (state.atLastChapter && state.loaded.isNotEmpty()) {
                        item(key = "end-of-series") {
                            Column(Modifier.fillMaxWidth()) {
                                HorizontalDivider(Modifier.padding(top = 20.dp))
                                Text(
                                    "No further chapters",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                )
                            }
                        }
                    }
                }

                // Explain bar, pinned to the bottom whenever text is selected.
                if (selection.isNotBlank()) {
                    ExplainBar(
                        onExplain = {
                            val sel = selection
                            dismissSelection()
                            if (sel.isNotBlank()) {
                                vm.explain(sel, contextAround(state.loaded.joinToString("\n\n") { it.body }, sel))
                            }
                        },
                        onDismiss = { dismissSelection() },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }

    if (state.explanation != null || state.explaining) {
        ModalBottomSheet(onDismissRequest = vm::dismissExplanation, sheetState = sheetState) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 48.dp),
            ) {
                Text("Assistant", style = MaterialTheme.typography.titleMedium)
                if (state.explaining) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    // The assistant replies in Markdown; render it and pad the
                    // bottom so the last line isn't flush against the sheet edge.
                    MarkdownText(
                        markdown = state.explanation.orEmpty() + "\n\n    ",
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingRow() {
    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp))
    }
}

/**
 * A chapter's body in a native selectable TextView so we can read the exact
 * selection (Compose hides this). The AndroidView update guards each property
 * so recomposition never resets the text and wipes an active selection.
 */
@Composable
private fun ChapterBody(
    text: String,
    typeface: Typeface,
    textColor: Int,
    fontSizeSp: Float,
    onSelectionChanged: (String) -> Unit,
) {
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            SelectableTextView(ctx).apply {
                setLineSpacing(0f, 1.5f)
                setTextIsSelectable(true)
                onSelectionChangedListener = { start, end ->
                    if (end > start) onSelectionChanged(currentSelection().trim())
                }
            }
        },
        update = { tv ->
            if (tv.text?.toString() != text) tv.text = text
            if (tv.currentTextColor != textColor) tv.setTextColor(textColor)
            if (tv.typeface != typeface) tv.typeface = typeface
            val appliedSp = tv.tag as? Float
            if (appliedSp != fontSizeSp) {
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, fontSizeSp)
                tv.tag = fontSizeSp
            }
        },
    )
}

/**
 * Fixed "Explain ✕" bar pinned to the bottom of the reader while text is
 * selected. Always on-screen — no popup positioning to get wrong.
 */
@Composable
private fun ExplainBar(
    onExplain: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        tonalElevation = 3.dp,
        shadowElevation = 6.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp),
        ) {
            TextButton(
                onClick = onExplain,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                ),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text("  Explain selection")
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
            }
        }
    }
}

/**
 * Split reader HTML into clean paragraph strings, then join with blank lines.
 * Providers vary: some wrap paragraphs in <p>, some separate with <br>, some
 * return near-plain text. Prefer real <p> elements; otherwise fall back to
 * splitting on <br> and line breaks so we never get a wall of text.
 */
private fun htmlToParagraphs(html: String): List<String> {
    if (html.isBlank()) return emptyList()

    val doc = org.jsoup.Jsoup.parse(html)
    val pElements = doc.select("p")
    if (pElements.size >= 2) {
        val paras = pElements.map { it.text().trim() }.filter { it.isNotEmpty() }
        if (paras.size >= 2) return paras
    }

    doc.select("br").after("\n")
    doc.select("p, div, h1, h2, h3, h4, li").append("\n")
    val text = doc.wholeText()
    return text
        .split(Regex("\n+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
}

/** A window of [full] around the first occurrence of [selected], for context. */
private fun contextAround(full: String, selected: String, radius: Int = 600): String {
    val idx = full.indexOf(selected)
    if (idx < 0) return full.take(radius * 2)
    val start = (idx - radius).coerceAtLeast(0)
    val end = (idx + selected.length + radius).coerceAtMost(full.length)
    return full.substring(start, end)
}
