package com.kreativekoala.echonote.ui

import com.kreativekoala.echonote.data.model.TranscriptSegment
import com.kreativekoala.echonote.ui.playback.buildParagraphs
import org.junit.Assert.assertEquals
import org.junit.Test

class TranscriptParagraphsTest {
    private fun w(start: Long, end: Long, speaker: Int? = null) = TranscriptSegment("x", start, end, speaker)

    @Test
    fun longPauseSplitsParagraphs() {
        val p = buildParagraphs(listOf(w(0, 300), w(400, 700), w(2500, 2800)))
        assertEquals(listOf(0 to 2, 2 to 3), p.map { it.firstWord to it.endWord })
    }

    @Test
    fun speakerChangeSplitsEvenWithoutAPauseAndKeepsTheLabel() {
        val p = buildParagraphs(listOf(w(0, 300, 0), w(310, 600, 0), w(620, 900, 1), w(910, 1200, 1)))
        assertEquals(listOf(0 to 2, 2 to 4), p.map { it.firstWord to it.endWord })
        assertEquals(listOf<Int?>(0, 1), p.map { it.speaker })
    }

    @Test
    fun unlabelledWordsNeverSplitOnSpeaker() {
        assertEquals(1, buildParagraphs(listOf(w(0, 300), w(310, 600), w(620, 900))).size)
    }
}
