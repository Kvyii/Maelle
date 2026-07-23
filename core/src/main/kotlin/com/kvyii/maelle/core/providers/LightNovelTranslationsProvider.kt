package com.kvyii.maelle.core.providers

import com.kvyii.maelle.core.http.HttpClient

import com.kvyii.maelle.core.Log
import com.kvyii.maelle.core.ChapterData
import com.kvyii.maelle.core.HeadMainPageResponse
import com.kvyii.maelle.core.LoadResponse
import com.kvyii.maelle.core.MainAPI
import com.kvyii.maelle.core.SearchResponse
import com.kvyii.maelle.core.fixUrl
import com.kvyii.maelle.core.fixUrlNull
import com.kvyii.maelle.core.logError
import com.kvyii.maelle.core.newChapterData
import com.kvyii.maelle.core.newSearchResponse
import com.kvyii.maelle.core.newStreamResponse
import com.kvyii.maelle.core.setStatus

class LightNovelTranslationsProvider(client: HttpClient) : MainAPI(client) {
    override val name = "Light Novel Translations"
    override val mainUrl = "https://lightnovelstranslations.com"

    override val hasMainPage = true

    // Permite elegir orden y estado de la novela en la UI
    override val mainCategories = listOf(
        "Most Liked" to "most-liked",
        "Most Recent" to "most-recent"
    )

    override val tags = listOf(
        "All" to "all",
        "Ongoing" to "ongoing",
        "Completed" to "completed"
    )

    override val orderBys = emptyList<Pair<String, String>>()

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?
    ): HeadMainPageResponse
    {
        val range = if (page == 1) {
            1..3
        } else {
            val actualPage = page + 2
            actualPage..actualPage
        }
        val novels = mutableListOf<SearchResponse>()
        var url = ""
        for(i in range){
            url = "$mainUrl/read/page/$i?sortby=$mainCategory&status=$tag"
            val document = client.get(url).document
             novels.addAll( document.select("div.read_list-story-item").mapNotNull { el ->
                val link = el.selectFirst(".item_thumb a") ?: return@mapNotNull null
                val img = el.selectFirst(".item_thumb img")?.attr("src")
                val title = link.attr("title")
                val href = link.attr("href")

                newSearchResponse(name = title, url = href) {
                    posterUrl = fixUrlNull(img)
                }
            })
        }

        return HeadMainPageResponse(url, novels)
    }

    override suspend fun load(url: String): LoadResponse
    {
        val document = client.get(url).document

        val title = document.selectFirst("div.novel_title h3")?.text()?.trim().orEmpty()
        val author = document.selectFirst("div.novel_detail_info li:contains(Author)")
            ?.text()?.trim().orEmpty()
        val cover = document.selectFirst("div.novel-image img")?.attr("src")
        val statusText = document.selectFirst("div.novel_status")?.text()?.trim()

        val synopsis = try {
            val body2 = client.get(url.replace("?tab=table_contents", "")).document
            body2.selectFirst("div.novel_text p")?.text()?.trim().orEmpty()
        } catch (t: Throwable) {
            logError(t)
            ""
        }

        val status = when (statusText) {
            "Ongoing" -> "Ongoing"
            "Hiatus" -> "On Hiatus"
            "Completed" -> "Completed"
            else -> null
        }

        val chapters = mutableListOf<ChapterData>()
        document.select("li.chapter-item.unlock").forEach { li ->
            val link = li.selectFirst("a") ?: return@forEach
            val chapterTitle = link.text().trim()
            val href = link.attr("href")

            chapters.add(
                newChapterData(
                    name = chapterTitle,
                    url = href
                )
            )
        }

        return newStreamResponse(title, fixUrl(url), chapters) {
            this.author = author
            this.posterUrl = fixUrlNull(cover)
            this.synopsis = synopsis
            setStatus(status)
        }
    }

    override suspend fun loadHtml(url: String): String? {
        return try {
            val document = client.get(url).document
            val content = document.selectFirst("div.text_story")
            content?.select("div.ads_content")?.remove()
            content?.html()
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        if (query.isBlank()) return emptyList()

        val formData = mapOf("field-search" to query)
        val response = client.post("$mainUrl/read", data = formData)
        val document = response.document

        return document.select("div.read_list-story-item").mapNotNull { el ->
            val link = el.selectFirst(".item_thumb a") ?: return@mapNotNull null
            val img = el.selectFirst(".item_thumb img")?.attr("src")
            val title = link.attr("title").orEmpty()
            val href = link.attr("href").orEmpty()

            newSearchResponse(title, href) {
                posterUrl = fixUrlNull(img)
            }
        }
    }
}