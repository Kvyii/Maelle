package com.kvyii.maelle.core.http

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

const val USER_AGENT =
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

class HttpResponse(
    val url: String,
    val code: Int,
    val headers: Map<String, String>,
    val body: ByteArray,
) {
    val isSuccessful: Boolean get() = code in 200..299
    val text: String by lazy { body.decodeToString() }
    val document: Document by lazy { Jsoup.parse(text, url) }
}

class HttpException(val url: String, val code: Int) :
    Exception("HTTP $code for $url")

/**
 * The only way providers talk to the network. Implementations: [OkHttpBackend]
 * for production, fixture/replay clients in tests.
 */
interface HttpClient {
    suspend fun get(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
    ): HttpResponse

    suspend fun post(
        url: String,
        headers: Map<String, String> = emptyMap(),
        referer: String? = null,
        params: Map<String, String> = emptyMap(),
        cookies: Map<String, String> = emptyMap(),
        data: Map<String, String> = emptyMap(),
        jsonBody: String? = null,
    ): HttpResponse
}
