package com.kvyii.maelle.core.providers

import com.kvyii.maelle.core.http.HttpClient

import com.fasterxml.jackson.annotation.JsonProperty
import com.kvyii.maelle.core.ErrorLoadingException
import com.kvyii.maelle.core.HeadMainPageResponse
import com.kvyii.maelle.core.LoadResponse
import com.kvyii.maelle.core.MainAPI
import com.kvyii.maelle.core.SearchResponse
import com.kvyii.maelle.core.fixUrlNull
import com.kvyii.maelle.core.logError
import com.kvyii.maelle.core.safe
import com.kvyii.maelle.core.newChapterData
import com.kvyii.maelle.core.newSearchResponse
import com.kvyii.maelle.core.newStreamResponse
import com.kvyii.maelle.core.parsed
import com.kvyii.maelle.core.setStatus
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.util.Date

class RoyalRoadProvider(client: HttpClient) : MainAPI(client) {
    override val name = "Royal Road"
    override val mainUrl = "https://www.royalroad.com"
    override val rateLimitTime = 500L
    override val hasMainPage = true



    override val orderBys = listOf(
        "Best Rated" to "best-rated",
        "Ongoing" to "active-popular",
        "Completed" to "complete",
        "Popular this week" to "weekly-popular",
        "Latest Updates" to "latest-updates",
        "New Releases" to "new-releases",
        "Trending" to "trending",
        "Rising Stars" to "rising-stars",
        "Writathon" to "writathon"
    )

    override val tags = listOf(
        "All" to ""
    ) + (listOf(
        "Wuxia" to "wuxia",
        //"Xianxia" to "xianxia",
        "War and Military" to "war_and_military",
        "Low Fantasy" to "low_fantasy",
        "High Fantasy" to "high_fantasy",
        "Mythos" to "mythos",
        "Martial Arts" to "martial_arts",
        "Secret Identity" to "secret_identity",
        "Cyberpunk" to "cyberpunk",
        "Virtual Reality" to "virtual_reality",
        "Time Loop" to "loop",
        "Space Opera" to "space_opera",
        "First Contact" to "first_contact",
        "Grimdark" to "grimdark",
        "Strong Lead" to "strong_lead",
        "Time Travel" to "time_travel",
        "Ruling Class" to "ruling_class",
        "Action" to "action",
        "Adventure" to "adventure",
        "Comedy" to "comedy",
        "Contemporary" to "contemporary",
        "Drama" to "drama",
        "Fantasy" to "fantasy",
        "Historical" to "historical",
        "Horror" to "horror",
        "Mystery" to "mystery",
        "Psychological" to "psychological",
        "Romance" to "romance",
        "Satire" to "satire",
        "Sci-fi" to "sci_fi",
        "Hard Sci-fi" to "hard_sci-fi",
        "Soft Sci-fi" to "soft_sci-fi",
        "LitRPG" to "litrpg",
        "Magic" to "magic",
        "GameLit" to "gamelit",
        "Male Lead" to "male_lead",
        "Female Lead" to "female_lead",
        "Portal Fantasy / Isekai" to "summoned_hero",
        "Reincarnation" to "reincarnation",
        "Harem" to "harem",
        "Gender Bender" to "gender_bender",
        "Anti-Hero Lead" to "anti-hero_lead",
        "Progression" to "progression",
        "Strategy" to "strategy",
        "Short Story" to "one_shot",
        "Tragedy" to "tragedy"
    ).sortedBy { it.first })



    data class RelatedData(
        @JsonProperty("synopsis")
        val synopsis: String?,
        @JsonProperty("overallScore")
        val overallScore: Double?,
        @JsonProperty("cover")
        val cover: String?,
        @JsonProperty("title")
        val title: String?,
        @JsonProperty("url")
        val url: String?,
        @JsonProperty("id")
        val id: Int?,
    )

    private suspend fun loadRelated(id: Int?): List<SearchResponse>? {
        if (id == null) return null
        return try {
            // https://www.royalroad.com/fictions/similar?fictionId=68679
            client.get("$mainUrl/fictions/similar?fictionId=$id").parsed<Array<RelatedData>>()
                .mapNotNull { data ->
                    newSearchResponse(
                        name = data.title ?: return@mapNotNull null,
                        url = data.url ?: return@mapNotNull null
                    ) {
                        posterUrl = fixUrlNull(data.cover)
                    }
                }
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    override suspend fun loadMainPage(
        page: Int,
        mainCategory: String?,
        orderBy: String?,
        tag: String?,
    ): HeadMainPageResponse {
        val url =
            "$mainUrl/fictions/$orderBy?page=$page${if (tag == null || tag == "") "" else "&genre=$tag"}"
        if (page > 1 && arrayOf("trending","rising-stars").contains(orderBy)) return HeadMainPageResponse(
            url,
            ArrayList()
        ) // TRENDING ONLY HAS 1 PAGE

        val response = client.get(url)

        val document = Jsoup.parse(response.text)

        val returnValue = document.select("div.fiction-list-item").mapNotNull { h ->
            val head = h.selectFirst("> div")
            val hInfo = head?.selectFirst("> h2.fiction-title > a")

            val name = hInfo?.text() ?: return@mapNotNull null
            val cUrl = hInfo.attr("href")

            //val tags = ArrayList(h.select("span.tags > a").map { t -> t.text() })
            newSearchResponse(name = name, url = cUrl) {
                latestChapter = try {
                    if (orderBy == "latest-updates") {
                        head.selectFirst("> ul.list-unstyled > li.list-item > a > span")?.text()
                    } else {
                        h.select("div.stats > div.col-sm-6 > span")[4].text()
                    }
                } catch (_: Throwable) {
                    null
                }
                rating =
                    head.selectFirst("> div.stats")?.select("> div")?.get(1)?.selectFirst("> span")
                        ?.attr("title")?.toFloatOrNull()?.times(200)?.toInt()
                posterUrl = fixUrlNull(h.selectFirst("> figure > a > img")?.attr("src"))
            }
        }
        return HeadMainPageResponse(url, returnValue)
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val document = client.get("$mainUrl/fictions/search?title=$query").document

        return document.select("div.fiction-list-item").mapNotNull { h ->
            val head = h.selectFirst("> div.search-content")
            val hInfo = head?.selectFirst("> h2.fiction-title > a")

            val name = hInfo?.text() ?: return@mapNotNull null
            val url = hInfo.attr("href")

            newSearchResponse(url = url, name = name) {
                posterUrl = fixUrlNull(h.selectFirst("> figure.text-center > a > img")?.attr("src"))
                rating =
                    head.selectFirst("> div.stats")?.select("> div")?.get(1)?.selectFirst("> span")
                        ?.attr("title")?.toFloatOrNull()?.times(200)?.toInt()
                // latestChapter = h.select("div.stats > div.col-sm-6 > span")[4].text()
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val response = client.get(url)
        val document = response.document
        val name = document.selectFirst("h1.font-white")?.text()
            ?: throw ErrorLoadingException("Name not found for '$url'\nmight be deleted or simply a malformed url")
        val fictionId =
            response.text.substringAfter("window.fictionId = ").substringBefore(";").toIntOrNull()

        val chapterHeaders = document.select("div.portlet-body > table > tbody > tr")
        val data = chapterHeaders.mapNotNull { c ->
            val cUrl = c.attr("data-url") ?: return@mapNotNull null
            val td = c.select("> td") // 0 = Name, 1 = Upload
            val cName = td.getOrNull(0)?.selectFirst("> a")?.text() ?: return@mapNotNull null

            newChapterData(name = cName, url = cUrl) {
                dateOfRelease = td[1].selectFirst("> a > time")?.text()
            }
        }

        return newStreamResponse(url = url, name = name, data = data) {
            related = loadRelated(fictionId)

            val statusTxt = document.select("div.col-md-8 > div.margin-bottom-10 > span.label")
            for (s in statusTxt) {
                if (s.hasText()) {
                    if (setStatus(s.text())) break
                }
            }

            val hStates = document.select("ul.list-unstyled")[1]
            val stats = hStates.select("> li")
            views = stats.getOrNull(1)?.text()?.replace(",", "")?.replace(".", "")?.toInt()
            posterUrl =
                document.selectFirst("div.fic-header > div > .cover-art-container > img")
                    ?.attr("src")

            val synoDescript = document.select("div.description > div")
            val synoParts = synoDescript.select("> p")
            synopsis = if (synoParts.isEmpty() && synoDescript.hasText()) {
                synoDescript.text().replace("\n", "\n\n") // JUST IN CASE
            } else {
                synoParts.joinToString(separator = "\n\n") { it.text() }
            }
            author = document.selectFirst("h4.font-white > span > a")?.text()
            val ratingAttr = document.selectFirst("span.font-red-sunglo")?.attr("data-content")
            tags = document.select("span.tags > a").map { it.text() }
            safe {
                rating =
                    (ratingAttr?.substring(0, ratingAttr.indexOf('/'))?.toFloat()?.times(200))?.toInt()
            }
        }
    }

    private fun addAuthorNotes(chapter: Element, document: Document) {
        val noteContainerClass = "qnauthornotecontainer"
        val noteContentClass = "qnauthornote"
        val noteSeparatorClass = "qnauthornoteseparator"
        val separatorLine = "â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”â”"
        val spacer = "&nbsp;"

        val noteBeforeChapter = StringBuilder()
        val noteAfterChapter = StringBuilder()

        document.select("div.author-note").forEach { authorNote ->
            val noteContainer = authorNote.parent() ?: return@forEach
            val noteParent = noteContainer.parent() ?: return@forEach
            val chapterParent = chapter.parent() ?: return@forEach

            if (noteParent == chapterParent) {
                val isNoteBeforeChapter = noteContainer.elementSiblingIndex() < chapter.elementSiblingIndex()
                val noteContent = authorNote.html().takeIf { it.isNotBlank() } ?: return@forEach

                if (isNoteBeforeChapter) {
                    noteBeforeChapter.append(noteContent)
                } else {
                    noteAfterChapter.append(noteContent)
                }
            }
        }

        if (noteBeforeChapter.isNotEmpty()) {
            val content = """
                <div class="$noteContainerClass">
                    <div class="$noteContentClass">$noteBeforeChapter</div>
                    <div class="$noteSeparatorClass"><p>$separatorLine</p><p>$spacer</p></div>
                </div>
                """.trimIndent()
                
            Jsoup.parse(content).selectFirst("div")?.let {
                chapter.prependChild(it)
            }
        }
        
        if (noteAfterChapter.isNotEmpty()) {
            val content = """
                <div class="$noteContainerClass">
                    <div class="$noteSeparatorClass"><p>$spacer</p><p>$separatorLine</p></div>
                    <div class="$noteContentClass">$noteAfterChapter</div>
                </div>
                """.trimIndent()
                
            Jsoup.parse(content).selectFirst("div")?.let {
                chapter.appendChild(it)
            }
        }
    }

    override suspend fun loadHtml(url: String): String? {
        val response = client.get(url)
        val document = Jsoup.parse(response.text)
        val styles = document.select("style")
        val hiddenRegex = Regex("^\\s*(\\..*)\\s*\\{", RegexOption.MULTILINE)
        val chap = document.selectFirst("div.chapter-content") ?: return null
        addAuthorNotes(chap, document)

        styles.forEach { style ->
            hiddenRegex.findAll(style.toString()).forEach {
                val className = it.groupValues[1]
                if (className.isNotEmpty()) {
                    chap.select(className).remove()
                }
            }
        }

        return chap.html()
    }
}