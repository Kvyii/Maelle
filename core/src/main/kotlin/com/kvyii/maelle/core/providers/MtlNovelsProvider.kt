package com.kvyii.maelle.core.providers

import com.kvyii.maelle.core.http.HttpClient

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.readValue
import com.kvyii.maelle.core.mapper
import com.kvyii.maelle.core.ErrorLoadingException
import com.kvyii.maelle.core.HeadMainPageResponse
import com.kvyii.maelle.core.LoadResponse
import com.kvyii.maelle.core.MainAPI
import com.kvyii.maelle.core.SearchResponse
import com.kvyii.maelle.core.fixUrlNull
import com.kvyii.maelle.core.newChapterData
import com.kvyii.maelle.core.newSearchResponse
import com.kvyii.maelle.core.newStreamResponse
import org.jsoup.Jsoup

class MtlNovelProvider(client: HttpClient) : MainAPI(client) {
    override val name = "MtlNovel"
    override val mainUrl = "https://mtlnovel.me"
    override val hasMainPage = true



    fun fixImage(url: String?): String? {
        return url?.replace(
            "https://mtlnovel.me/",
            "https://mtlnovel.me/wp-content/uploads/"
        )
    }

    override val tags = listOf(
        "All" to "",
        "Action" to "action",
        "Adult" to "adult",
        "Adventure" to "adventure",
        "Comedy" to "comedy",
        "Drama" to "drama",
        "Ecchi" to "ecchi",
        "Erciyuan" to "erciyuan",
        "Fan-Fiction" to "fan-fiction",
        "Fantasy" to "fantasy",
        "Game" to "game",
        "Gender Bender" to "gender-bender",
        "Harem" to "harem",
        "Historical" to "historical",
        "Horror" to "horror",
        "Josei" to "josei",
        "Martial Arts" to "martial-arts",
        "Mature" to "mature",
        "Mecha" to "mecha",
        "Military" to "military",
        "Mystery" to "mystery",
        "Psychological" to "psychological",
        "Romance" to "romance",
        "School Life" to "school-life",
        "Sci-fi" to "sci-fi",
        "Seinen" to "seinen",
        "Shoujo" to "shoujo",
        "Shoujo Ai" to "shoujo-ai",
        "Shounen" to "shounen",
        "Shounen Ai" to "shounen-ai",
        "Slice of Life" to "slice-of-life",
        "Smut" to "smut",
        "Sports" to "sports",
        "Supernatural" to "supernatural",
        "Tragedy" to "tragedy",
        "Two-dimensional" to "two-dimensional-novel",
        "Urban Life" to "urban-fiction",
        "Wuxia" to "wuxia",
        "Xianxia" to "xianxia",
        "Xuanhuan" to "xuanhuan",
        "Yaoi" to "yaoi",
        "Yuri" to "yuri",
    )

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse {
        val url =
            if (tag.isNullOrBlank()) "$mainUrl/list/?page=$page" else "$mainUrl/category/$tag/?page=$page"
        val document = client.get(url).document
        val headers = document.select("div.novel-box")

        val returnValue = headers.mapNotNull { h ->
            val name =
                h.selectFirst("h3")?.text()
                    ?: return@mapNotNull null
            val cUrl = fixUrlNull(h.selectFirst("a")?.attr("href"))?:""
            newSearchResponse(
                name = name,
                url = cUrl,
            ) {
                posterUrl = fixUrlNull(h.selectFirst("img")?.attr("src"))
            }
        }

        return HeadMainPageResponse(url, returnValue)
    }

    override suspend fun loadHtml(url: String): String? {
        return client.get(url).document.selectFirst("div.content.text-break")?.html()
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val response = client.get("$mainUrl/search/?keyword=$query").document

        return response.select("div.novel-box").mapNotNull { c->
            newSearchResponse(
                name = c.selectFirst("h3")?.text() ?: return@mapNotNull null,
                url = fixUrlNull(c.selectFirst("a")?.attr("href"))?: return@mapNotNull null
            ) {
                posterUrl = fixUrlNull(c.selectFirst("img")?.attr("src"))
            }
        }
    }


    override suspend fun load(url: String): LoadResponse? {
        val document = client.get(url).document
        val name = document.selectFirst("h5")?.text() ?: return null
        val chaptersProvider = Regex("(?<=\\?slug=)([^']+)").find(document.toString())?.value
        val chaptersGroup = client.get("$mainUrl/ajax/chapters/?slug=$chaptersProvider")
            .document.select("p.update-box-chapter")
            .mapNotNull { c ->
                val href = c.selectFirst("a")?.attr("href")?: return@mapNotNull null
                val cName = c.text()
                newChapterData(name = cName, url = href)
            }

        return newStreamResponse(url = url, name = name, data = chaptersGroup) {
            val lis = document.select("div.m-card li")

            author = lis.getOrNull(2)?.selectFirst("pull-right")?.text()

            posterUrl = fixUrlNull(document.selectFirst("div.content-main-image img")?.attr("src"))

            tags = lis.getOrNull(5)?.select("a")?.map { it.text() }

            synopsis = document.selectFirst("div.m-card.text-break")?.ownText()

            peopleVoted = 0
            rating = document.selectFirst("span.rating")
                ?.text()
                ?.trim()
                ?.toFloat()
                ?.times(200)
                ?.toInt()
        }

    }
}


private data class SearchResults(
    @get:JsonProperty("items") val items: List<Item>? = null
) {
    companion object {
        fun fromJson(json: String) = mapper.readValue<SearchResults>(json)
    }
}

private data class Item(
    @get:JsonProperty("query") val query: String? = null,
    @get:JsonProperty("results") val results: List<Result>? = null
)

private data class Result(
    @get:JsonProperty("title") val title: String? = null,
    @get:JsonProperty("permalink") val permalink: String? = null,
    @get:JsonProperty("thumbnail") val thumbnail: String? = null,
    @get:JsonProperty("shortname") val shortname: String? = null,
    @get:JsonProperty("cn") val cn: String? = null
)