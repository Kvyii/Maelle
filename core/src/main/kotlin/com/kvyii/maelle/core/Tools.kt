package com.kvyii.maelle.core

import java.net.URLEncoder

/** Re-export so ported providers can `import com.kvyii.maelle.core.USER_AGENT`. */
const val USER_AGENT = com.kvyii.maelle.core.http.USER_AGENT

/** JVM stand-in for android.util.Log so ported providers keep their logging lines. */
object Log {
    fun d(tag: String, msg: String?) = println("D/$tag: $msg")
    fun i(tag: String, msg: String?) = println("I/$tag: $msg")
    fun w(tag: String, msg: String?) = println("W/$tag: $msg")
    fun e(tag: String, msg: String?) = System.err.println("E/$tag: $msg")
}

fun logError(throwable: Throwable) {
    System.err.println("ERROR: ${throwable.message}")
    throwable.printStackTrace()
}

inline fun <T> safe(block: () -> T): T? {
    return try {
        block()
    } catch (t: Throwable) {
        logError(t)
        null
    }
}

fun String.toRate(maxRate: Int = 10): Int {
    return this
        .replace(Regex("[^.0-9]"), "")
        .toFloatOrNull()
        ?.times(1000 / maxRate)
        ?.toInt() ?: 0
}

fun String.toVote(): Int {
    val k = this.contains("K", true)
    return this
        .replace(Regex("[^.0-9]"), "")
        .toFloatOrNull()
        ?.times(if (k) 1000 else 1)
        ?.toInt() ?: 0
}

fun String.toChapters(): String = this.replace(Regex("[^0-9]"), "")

fun String.clean(): String {
    return this
        .replace(Regex("[\\n\\t\\r]"), "")
        .replace(Regex("[ ]{2,}"), " ")
}

fun String.synopsis(): String {
    return this
        .replace(Regex("(\\. )"), ".\n\n")
        .replace(Regex("[\\t\\r]"), "")
        .replace(Regex("\\n{1}"), "\n\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .replace(Regex("[ ]{2,}"), " ")
}

/**
 * JVM stand-in for android.net.Uri.Builder, covering the surface the ported
 * providers use: appendPath / query parameters / toString.
 */
class UrlBuilder(base: String) {
    private val paths = mutableListOf<String>()
    private val queries = mutableListOf<Pair<String, String>>()
    private val root: String

    init {
        val split = base.substringBefore('?')
        root = split.trimEnd('/')
        base.substringAfter('?', "").split('&').forEach { q ->
            if (q.isNotBlank()) {
                queries.add(q.substringBefore('=') to q.substringAfter('=', ""))
            }
        }
    }

    fun appendPath(path: String): UrlBuilder {
        paths.add(URLEncoder.encode(path, Charsets.UTF_8.name()).replace("+", "%20"))
        return this
    }

    fun appendQueryParameter(key: String, value: String): UrlBuilder {
        queries.add(key to URLEncoder.encode(value, Charsets.UTF_8.name()))
        return this
    }

    override fun toString(): String {
        val path = if (paths.isEmpty()) "" else "/" + paths.joinToString("/")
        val query = if (queries.isEmpty()) "" else
            "?" + queries.joinToString("&") { "${it.first}=${it.second}" }
        return "$root$path$query"
    }
}

fun String.toUrlBuilderSafe(): UrlBuilder = UrlBuilder(this)

fun UrlBuilder.ifCase(case: Boolean, action: UrlBuilder.() -> UrlBuilder): UrlBuilder = when {
    case -> action(this)
    else -> this
}

fun UrlBuilder.addPath(vararg path: String): UrlBuilder =
    path.fold(this) { builder, s ->
        builder.appendPath(s)
    }

fun UrlBuilder.add(vararg query: Pair<String, Any>): UrlBuilder =
    query.fold(this) { builder, s ->
        builder.appendQueryParameter(s.first, s.second.toString())
    }

fun UrlBuilder.add(key: String, value: Any): UrlBuilder =
    appendQueryParameter(key, value.toString())
