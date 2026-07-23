package com.kvyii.maelle.assistant

import com.fasterxml.jackson.databind.ObjectMapper
import com.kvyii.maelle.data.AssistantSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Calls OpenRouter's chat-completions endpoint to explain selected text.
 * Ported from Cassie's OpenRouterHelper, decoupled from any global app singleton.
 */
class OpenRouterClient(
    private val client: OkHttpClient = OkHttpClient(),
    private val mapper: ObjectMapper = ObjectMapper(),
) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun explain(
        selected: String,
        paragraph: String,
        settings: AssistantSettings,
    ): String = withContext(Dispatchers.IO) {
        require(settings.isConfigured) { "OpenRouter API key not set" }

        val fullPrompt = buildPrompt(settings.prompt, selected, paragraph)
        val body = """
            {
              "model": ${jsonString(settings.model)},
              "messages": [
                { "role": "user", "content": ${jsonString(fullPrompt)} }
              ],
              "reasoning": { "enabled": ${settings.reasoning} }
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer ${settings.apiKey}")
            .header("Content-Type", "application/json")
            .post(body.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}: ${text.take(200)}")
            }
            extractContent(text) ?: throw Exception("Unexpected API response format")
        }
    }

    private fun buildPrompt(template: String, selected: String, paragraph: String): String =
        "You are a language assistant in an ebook reader. You have been provided a snippet of text:" +
            "\n\n$paragraph\n\n$template\n\nThe user is asking about:\n\n$selected"

    private fun jsonString(s: String): String {
        val escaped = s
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
        return "\"$escaped\""
    }

    private fun extractContent(json: String): String? = try {
        mapper.readTree(json)["choices"]?.get(0)?.get("message")?.get("content")?.asText()
    } catch (_: Exception) {
        null
    }

    companion object {
        const val ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"
    }
}
