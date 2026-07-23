package com.kvyii.maelle.data

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

sealed interface UpdateResult {
    /** A newer release exists; [version] is its tag, [url] its release page. */
    data class Available(val version: String, val url: String) : UpdateResult
    object UpToDate : UpdateResult
    data class Error(val message: String) : UpdateResult
}

/**
 * Checks the GitHub Releases API for a version newer than what's installed.
 * Compares dotted numeric versions (e.g. 1.2.0), ignoring any leading "v" and
 * build suffixes like "-DEBUG".
 */
class UpdateChecker(
    private val client: OkHttpClient = OkHttpClient(),
    private val mapper: ObjectMapper = ObjectMapper(),
) {
    suspend fun check(currentVersion: String): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(LATEST_RELEASE_URL)
                .header("Accept", "application/vnd.github+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 404) {
                    return@withContext UpdateResult.Error("No releases published yet.")
                }
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext UpdateResult.Error("GitHub returned HTTP ${response.code}.")
                }
                val root = mapper.readTree(body)
                val tag = root["tag_name"]?.asText()
                    ?: return@withContext UpdateResult.Error("Malformed release data.")
                val htmlUrl = root["html_url"]?.asText() ?: RELEASES_URL

                if (isNewer(latest = tag, current = currentVersion)) {
                    UpdateResult.Available(tag, htmlUrl)
                } else {
                    UpdateResult.UpToDate
                }
            }
        } catch (e: Exception) {
            UpdateResult.Error(e.message ?: "Couldn't reach GitHub.")
        }
    }

    private fun isNewer(latest: String, current: String): Boolean {
        val l = parseVersion(latest)
        val c = parseVersion(current)
        val size = maxOf(l.size, c.size)
        for (i in 0 until size) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }

    /** "v1.2.0-DEBUG" -> [1, 2, 0]; non-numeric trailing parts are dropped. */
    private fun parseVersion(raw: String): List<Int> =
        raw.trim().removePrefix("v").removePrefix("V")
            .substringBefore('-')
            .split('.')
            .map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

    companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/Kvyii/Maelle/releases/latest"
        const val RELEASES_URL = "https://github.com/Kvyii/Maelle/releases"
    }
}
