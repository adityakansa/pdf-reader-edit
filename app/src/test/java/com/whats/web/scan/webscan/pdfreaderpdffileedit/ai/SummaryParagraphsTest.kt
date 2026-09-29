package com.whats.web.scan.webscan.pdfreaderpdffileedit.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-069. The model does not obey "write three paragraphs" — 2 to 11 were seen in testing — so the
 * clamp is what actually guarantees the shape of the answer. If it breaks, the feature breaks.
 */
class SummaryParagraphsTest {

    @Test
    fun `keeps the first three paragraphs`() {
        val clamped = SummaryParagraphs.clamp("one\n\ntwo\n\nthree\n\nfour\n\nfive")
        assertEquals(listOf("one", "two", "three"), clamped.split("\n\n"))
    }

    @Test
    fun `drops exact repeats`() {
        val clamped = SummaryParagraphs.clamp("one\n\none\n\ntwo")
        assertEquals(listOf("one", "two"), clamped.split("\n\n"))
    }

    @Test
    fun `splits a single paragraph at the sentence nearest the middle`() {
        val single = "First sentence here. Second sentence here. Third sentence here."
        val clamped = SummaryParagraphs.clamp(single)
        val parts = clamped.split("\n\n")
        assertEquals(2, parts.size)
        assertTrue(parts.all { it.isNotBlank() })
        assertEquals(single.filterNot { it.isWhitespace() }, clamped.filterNot { it.isWhitespace() })
    }

    @Test
    fun `leaves a single sentence alone rather than cutting mid-word`() {
        assertEquals("Only one sentence", SummaryParagraphs.clamp("Only one sentence"))
    }

    @Test
    fun `holds back a token that stops inside a UTF-8 character`() {
        val euro = "a€".toByteArray(Charsets.UTF_8) // 'a' + 3-byte sequence
        assertEquals("a", SummaryParagraphs.decodeComplete(euro.copyOf(2)))
        assertEquals("a", SummaryParagraphs.decodeComplete(euro.copyOf(3)))
        assertEquals("a€", SummaryParagraphs.decodeComplete(euro))
    }

    @Test
    fun `decodes four-byte characters once complete`() {
        val emoji = "x😀".toByteArray(Charsets.UTF_8)
        assertEquals(5, emoji.size)
        assertEquals(1, SummaryParagraphs.completeUtf8Length(emoji.copyOf(4)))
        assertEquals(5, SummaryParagraphs.completeUtf8Length(emoji))
    }
}
