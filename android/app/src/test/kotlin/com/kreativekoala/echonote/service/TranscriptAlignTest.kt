package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptSegment
import com.kreativekoala.echonote.data.model.toJson
import com.kreativekoala.echonote.data.model.toTranscriptSegments
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptAlignTest {
    @Test
    fun subwordTokensJoinIntoWords() {
        val words = tokensToWords(
            arrayOf(" Hel", "lo", ",", " wor", "ld", "."),
            floatArrayOf(0.0f, 0.2f, 0.4f, 0.8f, 1.0f, 1.3f),
            FloatArray(0),
            offsetMs = 0
        )
        assertEquals(listOf("Hello,", "world."), words.map { it.word })
        assertEquals(0L, words[0].startMs)
        assertEquals(800L, words[0].endMs)
        assertEquals(800L, words[1].startMs)
    }

    @Test
    fun offsetShiftsEveryWordAndSpecialTokensAreDropped() {
        val words = tokensToWords(
            arrayOf("<|en|>", " hi", " there"),
            floatArrayOf(0f, 0.5f, 1.0f),
            floatArrayOf(0f, 0.4f, 0.6f),
            offsetMs = 28_000
        )
        assertEquals(listOf("hi", "there"), words.map { it.word })
        assertEquals(28_500L, words[0].startMs)
        assertEquals(29_600L, words[1].endMs)
    }

    @Test
    fun mismatchedTimestampsGiveNoWordsInsteadOfCrashing() {
        assertTrue(tokensToWords(arrayOf(" a", " b"), floatArrayOf(0f), FloatArray(0), 0).isEmpty())
    }

    @Test
    fun speakersAreRenumberedByFirstAppearance() {
        val words = listOf(
            TranscriptSegment("a", 0, 400), TranscriptSegment("b", 500, 900),
            TranscriptSegment("c", 2100, 2500), TranscriptSegment("d", 3100, 3500)
        )
        val spans = listOf(SpeakerSpan(0, 1000, 7), SpeakerSpan(2000, 2600, 3), SpeakerSpan(3000, 3600, 7))
        assertEquals(listOf(0, 0, 1, 0), assignSpeakers(words, spans).map { it.speaker })
    }

    @Test
    fun wordInAGapTakesTheNearestSpeaker() {
        val words = listOf(TranscriptSegment("x", 1200, 1300))
        val spans = listOf(SpeakerSpan(0, 1000, 0), SpeakerSpan(2000, 3000, 1))
        assertEquals(0, assignSpeakers(words, spans).single().speaker)
    }

    @Test
    fun noSpansLeavesWordsUnlabelled() {
        val words = listOf(TranscriptSegment("x", 0, 100))
        assertNull(assignSpeakers(words, emptyList()).single().speaker)
    }

    @Test
    fun quietestFramePicksTheSilentGap() {
        val s = ShortArray(16000) { 5000 }
        for (i in 8000 until 9600) s[i] = 0
        val start = quietestFrameStart(s, 4000, 15000)
        assertTrue("start=$start", start in 7200..8800)
    }

    @Test
    fun speakerSurvivesJsonRoundTripAndOldJsonStillLoads() {
        val rt = listOf(TranscriptSegment("hi", 0, 100, 1), TranscriptSegment("yo", 100, 200)).toJson().toTranscriptSegments()
        assertEquals(1, rt[0].speaker)
        assertNull(rt[1].speaker)
        val old = """[{"word":"a","startMs":1,"endMs":2}]""".toTranscriptSegments()
        assertNull(old.single().speaker)
    }

    @Test
    fun sentenceTakesItsMajoritySpeakerSoTurnsBreakAtSentenceEnds() {
        fun w(t: String, a: Long, b: Long, sp: Int?) = TranscriptSegment(t, a, b, sp)
        val words = listOf(
            w("Let's", 0, 300, 0), w("start", 300, 600, 0), w("the", 600, 800, 0),
            w("numbers.", 900, 1400, 1),            // late word time puts the last word in speaker 1
            w("Sure,", 2000, 2300, 1), w("revenue", 2300, 2800, 1), w("grew.", 2800, 3200, 1)
        )
        assertEquals(listOf(0, 0, 0, 0, 1, 1, 1), smoothSpeakersBySentence(words).map { it.speaker })
    }

    @Test
    fun smoothingLeavesUnlabelledWordsAlone() {
        val words = listOf(TranscriptSegment("a.", 0, 100), TranscriptSegment("b.", 100, 200))
        assertEquals(words, smoothSpeakersBySentence(words))
    }
}
