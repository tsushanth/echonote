package com.kreativekoala.echonote.util

import com.kreativekoala.echonote.data.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleCuesTest {
    private fun w(word: String, start: Long, end: Long) = TranscriptSegment(word, start, end)

    @Test
    fun emptyWordsGiveNoCues() {
        assertTrue(buildSubtitleCues(emptyList()).isEmpty())
    }

    @Test
    fun cueTimesFollowTheSpeechNotTheDuration() {
        val cues = buildSubtitleCues(listOf(w("hello", 4000, 4400), w("world", 4450, 4900)))
        assertEquals(1, cues.size)
        assertEquals(4000L, cues[0].startMs)
        assertEquals(4900L, cues[0].endMs)
        assertEquals("hello world", cues[0].text)
    }

    @Test
    fun longPauseStartsANewCue() {
        val cues = buildSubtitleCues(listOf(w("one", 0, 300), w("two", 400, 700), w("three", 2000, 2300)))
        assertEquals(listOf("one two", "three"), cues.map { it.text })
    }

    @Test
    fun longRunsAreSplitByWordCount() {
        val words = (0 until 25).map { w("w$it", it * 200L, it * 200L + 150) }
        val cues = buildSubtitleCues(words)
        assertTrue(cues.all { it.text.split(" ").size <= 10 })
        assertEquals(25, cues.sumOf { it.text.split(" ").size })
    }

    @Test
    fun srtAndVttUseTheirOwnTimestampSeparators() {
        val cues = listOf(SubtitleCue(3_661_005, 3_662_000, "hi"))
        assertEquals("1\n01:01:01,005 --> 01:01:02,000\nhi\n\n", cues.toSrt().replace("\r\n", "\n"))
        assertEquals("WEBVTT\n\n01:01:01.005 --> 01:01:02.000\nhi\n\n", cues.toVtt().replace("\r\n", "\n"))
    }
}
