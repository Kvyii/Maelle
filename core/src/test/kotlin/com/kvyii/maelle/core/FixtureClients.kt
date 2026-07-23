package com.kvyii.maelle.core

import com.kvyii.maelle.core.http.HttpClient
import com.kvyii.maelle.core.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

data class StoredResponse(
    val method: String = "GET",
    val url: String = "",
    val finalUrl: String = "",
    val code: Int = 0,
    val body: String = "",
)

class FixtureMissingException(key: String, url: String) :
    Exception("No fixture '$key' for $url — record it with: gradlew :core:test -Dmaelle.live=true -Dmaelle.record=true")

fun fixtureKey(
    method: String,
    url: String,
    params: Map<String, String>,
    data: Map<String, String> = emptyMap(),
): String {
    val canonical = buildString {
        append(method).append(' ').append(url)
        params.entries.sortedBy { it.key }.forEach { append("|p:${it.key}=${it.value}") }
        data.entries.sortedBy { it.key }.forEach { append("|d:${it.key}=${it.value}") }
    }
    val digest = MessageDigest.getInstance("SHA-1")
        .digest(canonical.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(12)
    val slug = url
        .removePrefix("https://").removePrefix("http://")
        .replace(Regex("[^A-Za-z0-9.-]"), "_")
        .take(60)
    return "${method}_${slug}_$digest"
}

/** Replays previously recorded responses; never touches the network. */
class FixtureHttpClient(private val dir: Path) : HttpClient {

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        referer: String?,
        params: Map<String, String>,
        cookies: Map<String, String>,
    ): HttpResponse = replay(fixtureKey("GET", url, params), url)

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        referer: String?,
        params: Map<String, String>,
        cookies: Map<String, String>,
        data: Map<String, String>,
        jsonBody: String?,
    ): HttpResponse = replay(fixtureKey("POST", url, params, data), url)

    private fun replay(key: String, url: String): HttpResponse {
        val file = dir.resolve("$key.json")
        if (!Files.exists(file)) throw FixtureMissingException(key, url)
        val stored = parseJson<StoredResponse>(Files.readString(file))
        return HttpResponse(
            url = stored.finalUrl.ifEmpty { stored.url },
            code = stored.code,
            headers = emptyMap(),
            body = stored.body.toByteArray(),
        )
    }
}

/** Passes requests through to [delegate] and saves each response as a fixture. */
class RecordingHttpClient(
    private val delegate: HttpClient,
    private val dir: Path,
) : HttpClient {

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        referer: String?,
        params: Map<String, String>,
        cookies: Map<String, String>,
    ): HttpResponse {
        val response = delegate.get(url, headers, referer, params, cookies)
        save(fixtureKey("GET", url, params), "GET", url, response)
        return response
    }

    override suspend fun post(
        url: String,
        headers: Map<String, String>,
        referer: String?,
        params: Map<String, String>,
        cookies: Map<String, String>,
        data: Map<String, String>,
        jsonBody: String?,
    ): HttpResponse {
        val response = delegate.post(url, headers, referer, params, cookies, data, jsonBody)
        save(fixtureKey("POST", url, params, data), "POST", url, response)
        return response
    }

    private fun save(key: String, method: String, url: String, response: HttpResponse) {
        Files.createDirectories(dir)
        val stored = StoredResponse(
            method = method,
            url = url,
            finalUrl = response.url,
            code = response.code,
            body = response.text,
        )
        Files.writeString(dir.resolve("$key.json"), mapper.writeValueAsString(stored))
    }
}
