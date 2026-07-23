package com.kvyii.maelle.core.http

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OkHttpBackend(
    builder: OkHttpClient.Builder = OkHttpClient.Builder(),
) : HttpClient {

    private val client: OkHttpClient = builder
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    override suspend fun get(
        url: String,
        headers: Map<String, String>,
        referer: String?,
        params: Map<String, String>,
        cookies: Map<String, String>,
    ): HttpResponse {
        val request = Request.Builder()
            .url(buildUrl(url, params))
            .applyHeaders(headers, referer, cookies)
            .get()
            .build()
        return client.newCall(request).await().toHttpResponse()
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
        val body = if (jsonBody != null) {
            jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        } else {
            FormBody.Builder().apply {
                data.forEach { (k, v) -> add(k, v) }
            }.build()
        }
        val request = Request.Builder()
            .url(buildUrl(url, params))
            .applyHeaders(headers, referer, cookies)
            .post(body)
            .build()
        return client.newCall(request).await().toHttpResponse()
    }

    private fun buildUrl(url: String, params: Map<String, String>): String {
        if (params.isEmpty()) return url
        val httpUrl = url.toHttpUrl().newBuilder()
        params.forEach { (k, v) -> httpUrl.addQueryParameter(k, v) }
        return httpUrl.build().toString()
    }

    private fun Request.Builder.applyHeaders(
        headers: Map<String, String>,
        referer: String?,
        cookies: Map<String, String>,
    ): Request.Builder {
        headers.forEach { (k, v) -> header(k, v) }
        if (headers.keys.none { it.equals("user-agent", ignoreCase = true) }) {
            header("User-Agent", USER_AGENT)
        }
        referer?.let { header("Referer", it) }
        if (cookies.isNotEmpty()) {
            header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }
        return this
    }

    private fun Response.toHttpResponse(): HttpResponse = use { response ->
        HttpResponse(
            url = response.request.url.toString(),
            code = response.code,
            headers = response.headers.toMap(),
            body = response.body?.bytes() ?: ByteArray(0),
        )
    }

    private fun okhttp3.Headers.toMap(): Map<String, String> =
        names().associateWith { get(it) ?: "" }

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
        cont.invokeOnCancellation { cancel() }
    }
}
