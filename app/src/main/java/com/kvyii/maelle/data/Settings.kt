package com.kvyii.maelle.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

const val DEFAULT_ASSISTANT_MODEL = "google/gemini-3-flash-preview"
const val DEFAULT_ASSISTANT_PROMPT =
    "Explain this concisely for a reader who is unfamiliar with it. " +
        "If it is a word or phrase in another language, translate it and give brief context."

data class AssistantSettings(
    val apiKey: String = "",
    val model: String = DEFAULT_ASSISTANT_MODEL,
    val prompt: String = DEFAULT_ASSISTANT_PROMPT,
    val reasoning: Boolean = false,
) {
    val isConfigured: Boolean get() = apiKey.isNotBlank()
}

enum class AppTheme(val id: String, val label: String) {
    System("system", "System"),
    Claire("claire", "Claire (light)"),
    Obscur("obscur", "Obscur (OLED)");

    companion object {
        fun fromId(id: String?): AppTheme = entries.firstOrNull { it.id == id } ?: System
    }
}

enum class ReaderFont(val id: String, val label: String) {
    Sans("sans", "Sans serif"),
    Serif("serif", "Serif"),
    Mono("mono", "Monospace");

    companion object {
        fun fromId(id: String?): ReaderFont = entries.firstOrNull { it.id == id } ?: Sans
    }
}

data class ReaderPreferences(
    val font: ReaderFont = ReaderFont.Sans,
    val fontSize: Int = 18,
    /** Blank = app-internal storage. */
    val downloadPath: String = "",
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val apiKey = stringPreferencesKey("assistant_api_key")
        val model = stringPreferencesKey("assistant_model")
        val prompt = stringPreferencesKey("assistant_prompt")
        val reasoning = booleanPreferencesKey("assistant_reasoning")

        val theme = stringPreferencesKey("app_theme")

        val font = stringPreferencesKey("reader_font")
        val fontSize = intPreferencesKey("reader_font_size")
        val downloadPath = stringPreferencesKey("download_path")
    }

    val assistant: Flow<AssistantSettings> = context.dataStore.data.map { prefs ->
        AssistantSettings(
            apiKey = prefs[Keys.apiKey] ?: "",
            model = prefs[Keys.model] ?: DEFAULT_ASSISTANT_MODEL,
            prompt = prefs[Keys.prompt] ?: DEFAULT_ASSISTANT_PROMPT,
            reasoning = prefs[Keys.reasoning] ?: false,
        )
    }

    val theme: Flow<AppTheme> = context.dataStore.data.map { prefs ->
        AppTheme.fromId(prefs[Keys.theme])
    }

    val readerPreferences: Flow<ReaderPreferences> = context.dataStore.data.map { prefs ->
        ReaderPreferences(
            font = ReaderFont.fromId(prefs[Keys.font]),
            fontSize = prefs[Keys.fontSize] ?: 18,
            downloadPath = prefs[Keys.downloadPath] ?: "",
        )
    }

    suspend fun update(settings: AssistantSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.apiKey] = settings.apiKey
            prefs[Keys.model] = settings.model
            prefs[Keys.prompt] = settings.prompt
            prefs[Keys.reasoning] = settings.reasoning
        }
    }

    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { it[Keys.theme] = theme.id }
    }

    suspend fun updateReaderPreferences(preferences: ReaderPreferences) {
        context.dataStore.edit { prefs ->
            prefs[Keys.font] = preferences.font.id
            prefs[Keys.fontSize] = preferences.fontSize
            prefs[Keys.downloadPath] = preferences.downloadPath
        }
    }
}
