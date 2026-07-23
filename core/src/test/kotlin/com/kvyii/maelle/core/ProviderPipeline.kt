package com.kvyii.maelle.core

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * The extraction contract every provider must satisfy: a search returns
 * results, the first result loads details, and content is extractable
 * (chapter text for stream providers, download links for epub providers).
 */
object ProviderPipeline {

    /** Search terms known to return results on each site; keyed by provider name. */
    private val queries = mapOf(
        "Annas Archive" to "the martian",
        "Royal Road" to "mother of learning",
        "Scribblehub" to "reincarnated",
        "ReadFrom.Net" to "harry potter",
        "Graycity.net" to "harry potter",
        "IndoWebNovel" to "martial",
        "SakuraNovel" to "martial",
        "MeioNovel" to "martial",
        "MoreNovel" to "martial",
        "KolNovel" to "solo",
        "WTR-LAB" to "martial peak",
    )

    private const val DEFAULT_QUERY = "sword"

    fun queryFor(api: MainAPI): String = queries[api.name] ?: DEFAULT_QUERY

    suspend fun verifyExtraction(api: MainAPI) {
        val query = queryFor(api)
        val results = api.search(query)
        assertTrue(!results.isNullOrEmpty(), "${api.name}: search('$query') returned no results")

        val first = results!!.first()
        assertTrue(first.name.isNotBlank(), "${api.name}: first search result has a blank title")
        assertTrue(first.url.startsWith("http"), "${api.name}: bad result url '${first.url}'")

        val details = api.load(first.url)
        assertNotNull(details, "${api.name}: load('${first.url}') returned null")

        when (details) {
            is StreamResponse -> {
                assertTrue(
                    details.data.isNotEmpty(),
                    "${api.name}: no chapters extracted for '${details.name}'"
                )
                val chapter = details.data.first()
                assertTrue(chapter.url.startsWith("http"), "${api.name}: bad chapter url '${chapter.url}'")
                val html = api.loadHtml(chapter.url)
                val textLength = html?.let { org.jsoup.Jsoup.parse(it).text().length } ?: 0
                assertTrue(
                    textLength > 200,
                    "${api.name}: chapter '${chapter.name}' extracted only $textLength chars of text"
                )
            }

            is EpubResponse -> {
                assertTrue(
                    details.downloadLinks.isNotEmpty() || details.downloadExtractLinks.isNotEmpty(),
                    "${api.name}: no download links extracted for '${details.name}'"
                )
            }

            else -> throw AssertionError("${api.name}: unexpected LoadResponse type ${details!!::class}")
        }
    }
}
