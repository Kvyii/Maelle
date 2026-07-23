package com.kvyii.maelle.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kvyii.maelle.AppContainer

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

    // The plain text the reader sees.
    val plainText = remember(state.html) {
        HtmlCompat.fromHtml(state.html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
    }
    var askOpen by remember { mutableStateOf(false) }
    var askText by remember { mutableStateOf("") }
    val sheetState = rememberModalBottomSheetState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.chapterName.ifBlank { "Reading" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { askOpen = true }) {
                        Icon(Icons.AutoMirrored.Filled.HelpOutline, contentDescription = "Ask the assistant")
                    }
                },
            )
        }
    ) { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            state.error != null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Error: ${state.error}", color = MaterialTheme.colorScheme.error)
            }

            else -> SelectionContainer {
                Text(
                    text = plainText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                )
            }
        }
    }

    if (askOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { askOpen = false },
            title = { Text("Ask the assistant") },
            text = {
                Column {
                    Text(
                        "Select and copy a word or phrase from the chapter, then paste it here.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextField(
                        value = askText,
                        onValueChange = { askText = it },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        if (askText.isNotBlank()) vm.explain(askText, plainText.take(2000))
                        askOpen = false
                    }
                ) { Text("Explain") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { askOpen = false }) { Text("Cancel") }
            },
        )
    }

    if (state.explanation != null || state.explaining) {
        ModalBottomSheet(onDismissRequest = vm::dismissExplanation, sheetState = sheetState) {
            Column(Modifier.padding(16.dp)) {
                Text("Assistant", style = MaterialTheme.typography.titleMedium)
                if (state.explaining) {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(
                        state.explanation.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}
