package com.kvyii.maelle.core

import com.kvyii.maelle.core.http.HttpClient
import org.jsoup.Jsoup

/**
 * Base class for all content providers. Pure JVM: the only outside dependency
 * is the injected [client], so every provider can run in a plain unit test.
 */
abstract class MainAPI(val client: HttpClient) {
    abstract val name: String
    abstract val mainUrl: String

    open val lang = "en" // ISO 639-1

    /** Minimum delay between requests to this provider, 0 = no limit. */
    open val rateLimitTime: Long = 0

    open val hasMainPage = false

    open val mainCategories: List<Pair<String, String>> = listOf()
    open val orderBys: List<Pair<String, String>> = listOf()
    open val tags: List<Pair<String, String>> = listOf()

    open suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        throw NotImplementedError()
    }

    open suspend fun search(query: String): List<SearchResponse>? {
        throw NotImplementedError()
    }

    open suspend fun load(url: String): LoadResponse? {
        throw NotImplementedError()
    }

    open suspend fun loadHtml(url: String): String? {
        throw NotImplementedError()
    }
}

class ErrorLoadingException(message: String? = null) : Exception(message)

fun MainAPI.fixUrlNull(url: String?): String? {
    if (url.isNullOrEmpty()) {
        return null
    }
    return fixUrl(url)
}

fun MainAPI.fixUrl(url: String): String {
    if (url.startsWith("http")) {
        return url
    }

    val startsWithNoHttp = url.startsWith("//")
    if (startsWithNoHttp) {
        return "https:$url"
    } else {
        if (url.startsWith('/')) {
            return mainUrl + url
        }
        return "$mainUrl/$url"
    }
}

//\.([A-z]) instead of \.([^-\s]) to preserve numbers like 17.4
val String?.textClean: String?
    get() = (this
        ?.replace("\\.([A-z]|\\+)".toRegex(), "$1")
        ?.replace("\\+([A-z])".toRegex(), "$1")
            )

fun stripHtml(
    txt: String,
    chapterName: String? = null,
    chapterIndex: Int? = null,
    stripAuthorNotes: Boolean = false,
): String {
    val document = Jsoup.parse(txt)
    try {
        if (stripAuthorNotes) {
            document.select("div.qnauthornotecontainer").remove()
        }
        if (chapterName != null && chapterIndex != null) {
            for (a in document.allElements) {
                if (a != null && a.hasText() &&
                    (a.text() == chapterName || (a.tagName() == "h3" && a.text()
                        .startsWith("Chapter ${chapterIndex + 1}")))
                ) {
                    a.remove() // remove the chapter title embedded in the content
                    break
                }
            }
        }
    } catch (_: Exception) {
    }

    return document.html()
        .replace("<p>.*<strong>Translator:.*?Editor:.*>".toRegex(), "")
        .replace("<.*?Translator:.*?Editor:.*?>".toRegex(), "")
}

data class HeadMainPageResponse(
    val url: String,
    val list: List<SearchResponse>,
)

data class SearchResponse(
    val name: String,
    val url: String,
    var posterUrl: String? = null,
    var rating: Int? = null,
    var latestChapter: String? = null,
    val apiName: String,
    var posterHeaders: Map<String, String>? = null,
)

fun MainAPI.newSearchResponse(
    name: String,
    url: String,
    fix: Boolean = true,
    initializer: SearchResponse.() -> Unit = { },
): SearchResponse {
    val builder =
        SearchResponse(name = name, url = if (fix) fixUrl(url) else url, apiName = this.name)
    builder.initializer()

    return builder
}

enum class ReleaseStatus {
    Ongoing,
    Completed,
    Paused,
    Dropped,
    Stubbed,
}

fun LoadResponse.setStatus(status: String?): Boolean {
    if (status == null) {
        return false
    }
    this.status = when (status.lowercase().trim()) {
        "ongoing", "on-going", "on_going" -> ReleaseStatus.Ongoing
        "completed", "complete", "done" -> ReleaseStatus.Completed
        "hiatus", "paused", "pause" -> ReleaseStatus.Paused
        "dropped", "drop" -> ReleaseStatus.Dropped
        "stub", "stubbed" -> ReleaseStatus.Stubbed
        else -> return false
    }
    return true
}

interface LoadResponse {
    val url: String
    val name: String
    var author: String?
    var posterUrl: String?

    // rating is from 0-1000
    var rating: Int?
    var peopleVoted: Int?
    var views: Int?
    var synopsis: String?
    var tags: List<String>?
    var status: ReleaseStatus?
    var posterHeaders: Map<String, String>?

    val apiName: String
    var related: List<SearchResponse>?
}

/** A novel read chapter-by-chapter from the source site. */
data class StreamResponse(
    override val url: String,
    override val name: String,
    val data: List<ChapterData>,
    override val apiName: String,
    override var author: String? = null,
    override var posterUrl: String? = null,
    override var rating: Int? = null,
    override var peopleVoted: Int? = null,
    override var views: Int? = null,
    override var synopsis: String? = null,
    override var tags: List<String>? = null,
    override var status: ReleaseStatus? = null,
    override var posterHeaders: Map<String, String>? = null,
    var nextChapter: ChapterData? = null,
    override var related: List<SearchResponse>? = null,
) : LoadResponse

suspend fun MainAPI.newStreamResponse(
    name: String,
    url: String,
    data: List<ChapterData>,
    fix: Boolean = true,
    initializer: suspend StreamResponse.() -> Unit = { },
): StreamResponse {
    val builder = StreamResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        data = data
    )
    builder.initializer()

    return builder
}

data class DownloadLink(
    override val url: String,
    override val name: String,
    val referer: String? = null,
    val headers: Map<String, String> = mapOf(),
    val params: Map<String, String> = mapOf(),
    val cookies: Map<String, String> = mapOf(),
    /// used for sorting, approximate download speed in kb/s
    val kbPerSec: Long = 1,
) : DownloadLinkType

data class DownloadExtractLink(
    override val url: String,
    override val name: String,
    val referer: String? = null,
    val headers: Map<String, String> = mapOf(),
    val params: Map<String, String> = mapOf(),
    val cookies: Map<String, String> = mapOf(),
) : DownloadLinkType

interface DownloadLinkType {
    val url: String
    val name: String
}

/** A book downloaded as a complete file (epub) rather than scraped per chapter. */
data class EpubResponse(
    override val url: String,
    override val name: String,
    override var author: String? = null,
    override var posterUrl: String? = null,
    override var rating: Int? = null,
    override var peopleVoted: Int? = null,
    override var views: Int? = null,
    override var synopsis: String? = null,
    override var tags: List<String>? = null,
    override var status: ReleaseStatus? = null,
    override var posterHeaders: Map<String, String>? = null,
    var downloadLinks: List<DownloadLink>,
    var downloadExtractLinks: List<DownloadExtractLink>,
    override val apiName: String,
    override var related: List<SearchResponse>? = null,
) : LoadResponse

suspend fun MainAPI.newEpubResponse(
    name: String,
    url: String,
    links: List<DownloadLinkType>,
    fix: Boolean = true,
    initializer: suspend EpubResponse.() -> Unit = { },
): EpubResponse {
    val builder = EpubResponse(
        name = name,
        url = if (fix) fixUrl(url) else url,
        apiName = this.name,
        downloadLinks = links.filterIsInstance<DownloadLink>().toList(),
        downloadExtractLinks = links.filterIsInstance<DownloadExtractLink>().toList()
    )
    builder.initializer()

    return builder
}

data class ChapterData(
    val name: String,
    val url: String,
    var dateOfRelease: String? = null,
    val views: Int? = null,
)

fun MainAPI.newChapterData(
    name: String,
    url: String,
    fix: Boolean = true,
    initializer: ChapterData.() -> Unit = { },
): ChapterData {
    val builder = ChapterData(name = name, url = if (fix) fixUrl(url) else url)
    builder.initializer()

    return builder
}
