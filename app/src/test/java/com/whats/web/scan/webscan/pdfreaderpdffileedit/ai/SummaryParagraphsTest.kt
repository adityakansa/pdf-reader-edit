package com.whats.web.scan.webscan.pdfreaderpdffileedit.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FR-069. The model does not obey "write three paragraphs" — 2 to 11 were seen in testing — so the
 * clamp is what actually guarantees the shape of the answer. If it breaks, the feature breaks.
 */
class SummaryParagraphsTest {

    private val engine = SummaryEngine(
        delivery = SummaryModelDelivery(context = FakeContext),
        limits = AiLimits(context = FakeContext),
    )

    @Test
    fun `keeps the first three paragraphs`() {
        val clamped = engine.clampParagraphs("one\n\ntwo\n\nthree\n\nfour\n\nfive")
        assertEquals(listOf("one", "two", "three"), clamped.split("\n\n"))
    }

    @Test
    fun `drops exact repeats`() {
        val clamped = engine.clampParagraphs("one\n\none\n\ntwo")
        assertEquals(listOf("one", "two"), clamped.split("\n\n"))
    }

    @Test
    fun `splits a single paragraph at the sentence nearest the middle`() {
        val single = "First sentence here. Second sentence here. Third sentence here."
        val clamped = engine.clampParagraphs(single)
        val parts = clamped.split("\n\n")
        assertEquals(2, parts.size)
        assertTrue(parts.all { it.isNotBlank() })
        assertEquals(single.filterNot { it.isWhitespace() }, clamped.filterNot { it.isWhitespace() })
    }

    @Test
    fun `leaves a single sentence alone rather than cutting mid-word`() {
        assertEquals("Only one sentence", engine.clampParagraphs("Only one sentence"))
    }

    /** The clamp is pure string work; neither collaborator is touched by it. */
    private object FakeContext : android.content.Context() {
        override fun getPackageName(): String = "test"
        override fun getFilesDir(): java.io.File = java.io.File("build/tmp/test")
    }
}
