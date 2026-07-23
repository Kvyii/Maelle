package com.kvyii.maelle.core.providers

import com.kvyii.maelle.core.http.HttpClient

import com.kvyii.maelle.core.HeadMainPageResponse
import com.kvyii.maelle.core.LoadResponse
import com.kvyii.maelle.core.MainAPI
import com.kvyii.maelle.core.SearchResponse
import com.kvyii.maelle.core.fixUrlNull
import com.kvyii.maelle.core.newChapterData
import com.kvyii.maelle.core.newSearchResponse
import com.kvyii.maelle.core.newStreamResponse
import org.jsoup.Jsoup
import kotlin.math.roundToInt

// cloudflare
class NovelsOnlineProvider(client: HttpClient) : MainAPI(client) {
    override val name = "NovelsOnline"
    override val mainUrl = "https://novelsonline.org"
    override val hasMainPage = true

    override val tags = listOf(
        "All" to "",
        "Action" to "action",
        "Adventure" to "adventure",
        "Celebrity" to "celebrity",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Ecchi" to "ecchi",
        "Fantasy" to "fantasy",
        "Gender Bender" to "gender-bender",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Josei" to "josei",
        "Martial Arts" to "martial-arts",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Mystery" to "mystery",
        "Psychological" to "psychological",
        "Romance" to "romance",
        "School Life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shotacon" to "shotacon",
        "Shoujo" to "shoujo",
        "Shoujo Ai" to "shoujo-ai",
        "Shounen" to "shounen",
        "Shounen Ai" to "shounen-ai",
        "Slice of Life" to "slice-of-life",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "Tragedy" to "tragedy",
        "Wuxia" to "wuxia",
        "Xianxia" to "xianxia",
        "Xuanhuan" to "xuanhuan",
        "Yaoi" to "yaoi",
        "Yuri" to "yuri",
    )
    //private val interceptor = CloudflareKiller()

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url =
            if (tag.isNullOrBlank()) "$mainUrl/top-novel/$page" else "$mainUrl/category/$tag/$page"

        val document = client.get(url).document

        val headers = document.select("div.top-novel-block")

        val list = headers.map { h ->
            val adata = h.selectFirst("div.top-novel-header > h2 > a")
            newSearchResponse(
                name = adata!!.text(),
                url = adata.attr("href"),
            ) {
                //posterHeaders = interceptor.getCookieHeaders(url).toMap()
                posterUrl = h.selectFirst("div.top-novel-content > div.top-novel-cover > a > img")!!
                    .attr("src")
            }
        }
        return HeadMainPageResponse(url, list)
    }

    override suspend fun loadHtml(url: String): String? {
        val response = client.get(url)
        val document =
            Jsoup.parse(response.text.replace("Your browser does not support JavaScript!", ""))
        return document.selectFirst("#contentall")!!.html()
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = client.post(
            "$mainUrl/sResults.php",
            data = mapOf("q" to query)
        ).document
        return document.select("li").mapNotNull { h ->
            newSearchResponse(
                name = h.text(),
                url = h.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            ) {
                posterUrl = h.selectFirst("img")?.attr("src")
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = client.get(url).document
        val name = document.selectFirst("h1")!!.text()

        val data = document.select("ul.chapter-chs > li > a").map { c ->
            newChapterData(name = c.text(), url = c.attr("href"))
        }

        return newStreamResponse(url = url, name = name, data = data) {
            author =
                document.selectFirst("div.novel-details > div:nth-child(5) > div.novel-detail-body")
                    ?.select("li")?.joinToString { it.text() }
            tags =
                document.selectFirst("div.novel-details > div:nth-child(2) > div.novel-detail-body")
                    ?.select("li")?.map { it.text() }
            rating =
                document.selectFirst("div.novel-right > div > div:nth-child(6) > div.novel-detail-body")
                    ?.text()?.toFloatOrNull()?.times(100)?.roundToInt()
            synopsis =
                document.selectFirst("div.novel-right > div > div:nth-child(1) > div.novel-detail-body")
                    ?.text()
            posterUrl = fixUrlNull(
                document.select("div.novel-left > div.novel-cover > a > img").attr("src")
            )
        }
    }
}