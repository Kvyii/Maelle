package com.kvyii.maelle.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "settings")

const val DEFAULT_ASSISTANT_MODEL = "google/gemini-2.0-flash-001"
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

class SettingsRepository(private val context: Context) {
    private object Keys {
        val apiKey = stringPreferencesKey("assistant_api_key")
        val model = stringPreferencesKey("assistant_model")
        val prompt = stringPreferencesKey("assistant_prompt")
        val reasoning = booleanPreferencesKey("assistant_reasoning")
    }

    val assistant: Flow<AssistantSettings> = context.dataStore.data.map { prefs ->
        AssistantSettings(
            apiKey = prefs[Keys.apiKey] ?: "",
            model = prefs[Keys.model] ?: DEFAULT_ASSISTANT_MODEL,
            prompt = prefs[Keys.prompt] ?: DEFAULT_ASSISTANT_PROMPT,
            reasoning = prefs[Keys.reasoning] ?: false,
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
}
