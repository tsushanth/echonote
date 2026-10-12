package com.kreativekoala.echonote.service.summary

import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SummaryStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun stored(text: String) = StoredSummary(
        SummaryResult(listOf("One point.", "Two \"quoted\" points 要点。"), SummaryKind.EXTRACTIVE),
        hashTranscript(text), 123L
    )

    @Test fun roundTripsAndDetectsStaleTranscript() {
        val files = SummaryFiles(tmp.newFolder("summaries"))
        files.save("abc-123", stored("hello world"))
        val back = files.load("abc-123")!!
        assertEquals(listOf("One point.", "Two \"quoted\" points 要点。"), back.result.points)
        assertEquals(SummaryKind.EXTRACTIVE, back.result.kind)
        assertFalse(back.isStaleFor("hello world"))
        assertFalse(back.isStaleFor("  hello world \n"))
        assertTrue(back.isStaleFor("hello there world"))
    }

    @Test fun deleteAndPrune() {
        val dir = tmp.newFolder("s2")
        val files = SummaryFiles(dir)
        files.save("keep", stored("x")); files.save("drop", stored("x")); files.save("gone", stored("x"))
        files.delete("gone")
        assertNull(files.load("gone"))
        files.pruneExcept(setOf("keep"))
        assertTrue(files.load("keep") != null)
        assertNull(files.load("drop"))
    }

    @Test fun corruptFileIsTreatedAsMissingAndPathTricksAreRejected() {
        val dir = tmp.newFolder("s3")
        val files = SummaryFiles(dir)
        java.io.File(dir, "bad.json").writeText("{not json")
        assertNull(files.load("bad"))
        assertNull(files.load("missing"))
        files.save("../escape", stored("x")) // must not write outside the folder
        assertFalse(java.io.File(dir.parentFile, "escape.json").exists())
        files.delete("../x") // swallowed
    }

    // The fail-safe chain: AI null, AI throwing, and AI absent all give the extractive result.
    private class Fake(override val kind: SummaryKind, val answer: () -> SummaryResult?) : Summarizer {
        override suspend fun summarize(text: String, language: TranscriptionLanguage,
            segments: List<com.kreativekoala.echonote.data.model.TranscriptSegment>) = answer()
    }

    @Test fun chainFallsBackToExtractive() = runBlocking {
        val ext = Fake(SummaryKind.EXTRACTIVE) { SummaryResult(listOf("e"), SummaryKind.EXTRACTIVE) }
        val ok = SummaryResult(listOf("a"), SummaryKind.ON_DEVICE_AI)
        val lang = TranscriptionLanguage.ENGLISH
        assertEquals(ok, summarize(ext, Fake(SummaryKind.ON_DEVICE_AI) { ok }, "t", lang, emptyList()))
        assertEquals(SummaryKind.EXTRACTIVE, summarize(ext, Fake(SummaryKind.ON_DEVICE_AI) { null }, "t", lang, emptyList())!!.kind)
        assertEquals(SummaryKind.EXTRACTIVE, summarize(ext, Fake(SummaryKind.ON_DEVICE_AI) { error("boom") }, "t", lang, emptyList())!!.kind)
        assertEquals(SummaryKind.EXTRACTIVE, summarize(ext, null, "t", lang, emptyList())!!.kind)
    }

}
