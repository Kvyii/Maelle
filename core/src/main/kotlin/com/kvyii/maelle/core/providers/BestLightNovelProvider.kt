package com.kvyii.maelle.core.providers

import com.kvyii.maelle.core.http.HttpClient

import com.kvyii.maelle.core.LoadResponse
import com.kvyii.maelle.core.MainAPI
import com.kvyii.maelle.core.SearchResponse
import com.kvyii.maelle.core.fixUrlNull
import com.kvyii.maelle.core.newChapterData
import com.kvyii.maelle.core.newSearchResponse
import com.kvyii.maelle.core.newStreamResponse
import com.kvyii.maelle.core.setStatus
import com.kvyii.maelle.core.textClean

class BestLightNovelProvider(client: HttpClient) : MainAPI(client) {
    override val name = "BestLightNovel"
    override val mainUrl = "https://bestlightnovel.com"

    override suspend fun loadHtml(url: String): String? {
        val document = client.get(url).document
        val res = document.selectFirst("div.vung_doc")
        return res?.html().textClean?.replace("[Updated from F r e e w e b n o v e l. c o m]", "")
            ?.replace(
                "Find authorized novels in Webnovel，faster updates, better experience，Please click for visiting. ",
                ""
            )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = client.get("$mainUrl/search_novels/${query.replace(' ', '_')}").document

        val headers = document.select("div.danh_sach > div.list_category")
        return headers.mapNotNull {
            val head = it.selectFirst("> a")
            val name = head?.attr("title") ?: return@mapNotNull null
            val url = head.attr("href") ?: return@mapNotNull null

            newSearchResponse(name = name, url = url) {
                latestChapter = it.selectFirst("> a.chapter")?.text()
                posterUrl = fixUrlNull(head.selectFirst("> img")?.attr("src"))
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = client.get(url).document

        val infoHeaders = document.select("ul.truyen_info_right > li")

        val name = infoHeaders[0].selectFirst("> h1")?.text() ?: return null

        val chapterHeaders = document.select("div.chapter-list > div").mapNotNull {
            val spans = it.select("> span")
            val text = spans[0].selectFirst("> a")
            val cUrl = text?.attr("href") ?: return@mapNotNull null
            val cName = text.text() ?: return@mapNotNull null
            newChapterData(name = cName, url = cUrl) {
                dateOfRelease = spans[1].text()
            }
        }.reversed()

        return newStreamResponse(url = url, name = name, data = chapterHeaders) {
            for (a in infoHeaders[1].select("> a")) {
                val href = a?.attr("href")
                if (a.hasText() && (href?.length
                        ?: continue) > "$mainUrl/search_author/".length && href.startsWith("$mainUrl/search_author/")
                ) {
                    author = a.text()
                    break
                }
            }
            posterUrl = document.select("span.info_image > img").attr("src")
            tags = infoHeaders[2].select("> a").map { it.text() }
            synopsis = document.select("div.entry-header > div")[1].text().textClean
            setStatus(infoHeaders[3].selectFirst("> a")?.text())
            views = infoHeaders[6].text()
                .replace(",", "")
                .replace("\"", "").substring("View : ".length).toInt()
            try {
                val ratingHeader = infoHeaders[9].selectFirst("> em > em")?.select("> em")
                rating = (ratingHeader?.get(1)?.selectFirst("> em > em")?.text()?.toFloat()
                    ?.times(200))?.toInt() ?: 0

                peopleVoted = ratingHeader?.get(2)?.text()?.replace(",", "")?.toInt() ?: 0
            } catch (_: Throwable) {
            }
        }
    }
}