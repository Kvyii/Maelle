package com.kvyii.maelle.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kvyii.maelle.AppContainer
import com.kvyii.maelle.data.AppTheme
import com.kvyii.maelle.data.AssistantSettings
import com.kvyii.maelle.data.ReaderFont
import com.kvyii.maelle.data.ReaderPreferences

/** Settings hub: a menu of sub-screens. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onOpen: (String) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SettingsMenuItem(
                icon = Icons.Filled.Tune,
                title = "Preferences",
                subtitle = "Fonts, font size, download path",
                onClick = { onOpen("settings/preferences") },
            )
            HorizontalDivider()
            SettingsMenuItem(
                icon = Icons.Filled.Palette,
                title = "Themes",
                subtitle = "Claire · Obscur",
                onClick = { onOpen("settings/themes") },
            )
            HorizontalDivider()
            SettingsMenuItem(
                icon = Icons.Filled.AutoAwesome,
                title = "AI Reading Assistant",
                subtitle = "OpenRouter key, model, prompt",
                onClick = { onOpen("settings/assistant") },
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f).padding(start = 16.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSubScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        }
    ) { padding ->
        content(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        )
    }
}

/** Settings > Preferences — reader font, font size, download path. */
@Composable
fun PreferencesScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = MaelleViewModelFactory(container))
    val saved by vm.readerPreferences.collectAsStateWithLifecycle()

    var font by remember { mutableStateOf(saved.font) }
    var fontSize by remember { mutableStateOf(saved.fontSize) }
    var downloadPath by remember { mutableStateOf(saved.downloadPath) }

    LaunchedEffect(saved) {
        font = saved.font
        fontSize = saved.fontSize
        downloadPath = saved.downloadPath
    }

    fun persist() = vm.savePreferences(ReaderPreferences(font, fontSize, downloadPath))

    SettingsSubScaffold("Preferences", onBack) { modifier ->
        Column(modifier) {
            Text("Reader font", style = MaterialTheme.typography.titleSmall)
            ReaderFont.entries.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = font == option, onClick = {
                            font = option
                            persist()
                        })
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = font == option, onClick = null)
                    Text(option.label, Modifier.padding(start = 8.dp))
                }
            }

            Text(
                "Font size: ${fontSize}sp",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            Slider(
                value = fontSize.toFloat(),
                onValueChange = { fontSize = it.toInt() },
                onValueChangeFinished = ::persist,
                valueRange = 12f..32f,
                steps = 19,
            )

            Text(
                "Download path",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 16.dp),
            )
            OutlinedTextField(
                value = downloadPath,
                onValueChange = { downloadPath = it },
                placeholder = { Text("Leave blank for app-internal storage") },
                supportingText = {
                    Text("Downloaded chapters are stored here. Blank keeps them private to the app.")
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(onClick = ::persist, modifier = Modifier.padding(top = 12.dp)) {
                Text("Save")
            }
        }
    }
}

/** Settings > Themes — Claire (light) and Obscur (OLED). */
@Composable
fun ThemesScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = MaelleViewModelFactory(container))
    val current by vm.theme.collectAsStateWithLifecycle()

    SettingsSubScaffold("Themes", onBack) { modifier ->
        Column(modifier) {
            ThemeOption(
                theme = AppTheme.Claire,
                description = "Light, calm, easy on daylight eyes.",
                selected = current == AppTheme.Claire,
                onSelect = vm::setTheme,
            )
            ThemeOption(
                theme = AppTheme.Obscur,
                description = "Pure-black OLED with violet and teal highlights.",
                selected = current == AppTheme.Obscur,
                onSelect = vm::setTheme,
            )
            ThemeOption(
                theme = AppTheme.System,
                description = "Follow the system light/dark setting.",
                selected = current == AppTheme.System,
                onSelect = vm::setTheme,
            )
        }
    }
}

@Composable
private fun ThemeOption(
    theme: AppTheme,
    description: String,
    selected: Boolean,
    onSelect: (AppTheme) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = { onSelect(theme) })
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.padding(start = 8.dp)) {
            Text(theme.label, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** Settings > AI Reading Assistant — the OpenRouter configuration. */
@Composable
fun AssistantSettingsScreen(container: AppContainer, onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel(factory = MaelleViewModelFactory(container))
    val saved by vm.settings.collectAsStateWithLifecycle()

    var apiKey by remember { mutableStateOf(saved.apiKey) }
    var model by remember { mutableStateOf(saved.model) }
    var prompt by remember { mutableStateOf(saved.prompt) }
    var reasoning by remember { mutableStateOf(saved.reasoning) }

    LaunchedEffect(saved) {
        apiKey = saved.apiKey
        model = saved.model
        prompt = saved.prompt
        reasoning = saved.reasoning
    }

    SettingsSubScaffold("AI Reading Assistant", onBack) { modifier ->
        Column(modifier) {
            Text(
                "Long-press text in the reader to get an OpenRouter-powered explanation.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("OpenRouter API key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                label = { Text("Model") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                label = { Text("Prompt") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Enable reasoning", Modifier.weight(1f))
                Switch(checked = reasoning, onCheckedChange = { reasoning = it })
            }
            Button(
                onClick = {
                    vm.save(AssistantSettings(apiKey.trim(), model.trim(), prompt, reasoning))
                },
                modifier = Modifier.padding(top = 16.dp),
            ) {
                Text("Save")
            }
        }
    }
}
