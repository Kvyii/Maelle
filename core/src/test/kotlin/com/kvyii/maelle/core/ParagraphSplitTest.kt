package com.kvyii.maelle.core

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Mirror of ReaderScreen.htmlToParagraphs (which lives in :app and can't be
 * unit-tested on the JVM). Keep this in sync; it guards the paragraph-splitting
 * logic against the "wall of text" regression across provider markup styles.
 */
private fun htmlToParagraphs(html: String): List<String> {
    if (html.isBlank()) return emptyList()
    val doc = org.jsoup.Jsoup.parse(html)
    val pElements = doc.select("p")
    if (pElements.size >= 2) {
        val paras = pElements.map { it.text().trim() }.filter { it.isNotEmpty() }
        if (paras.size >= 2) return paras
    }
    doc.select("br").after("\n")
    doc.select("p, div, h1, h2, h3, h4, li").append("\n")
    val text = doc.wholeText()
    return text.split(Regex("\n+")).map { it.trim() }.filter { it.isNotEmpty() }
}

class ParagraphSplitTest {
    @Test
    fun `p-tag chapters split into paragraphs`() {
        val html = "<p>First.</p><p>Second.</p><p>Third.</p>"
        assertTrue(htmlToParagraphs(html).size == 3)
    }

    @Test
    fun `br-separated chapters split into paragraphs`() {
        val html = "<div>One line.<br><br>Two line.<br><br>Three line.</div>"
        val paras = htmlToParagraphs(html)
        assertTrue(paras.size >= 3) { "expected 3+, got ${paras.size}: $paras" }
    }

    @Test
    fun `single-br separated chapters still split`() {
        val html = "Alpha.<br>Beta.<br>Gamma."
        val paras = htmlToParagraphs(html)
        assertTrue(paras.size == 3) { "expected 3, got ${paras.size}: $paras" }
    }

    @Test
    fun `plain newline text splits`() {
        val html = "Line one.\nLine two.\nLine three."
        val paras = htmlToParagraphs(html)
        assertTrue(paras.size == 3) { "expected 3, got ${paras.size}: $paras" }
    }

    @Test
    fun `single blob stays one paragraph`() {
        val html = "<p>Just one paragraph with no breaks at all here.</p>"
        assertTrue(htmlToParagraphs(html).size == 1)
    }
}
