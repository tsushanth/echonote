package com.kreativekoala.echonote.util

import com.kreativekoala.echonote.data.model.TranscriptSegment
import com.kreativekoala.echonote.service.groupTokens
import com.kreativekoala.echonote.service.joinWords
import com.kreativekoala.echonote.service.needsSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Arabic, Persian, Urdu and Hebrew export, and the spacing rules for Thai and CJK. */
class RtlExportTest {
    private fun w(word: String, start: Long, end: Long) = TranscriptSegment(word, start, end)
    private val rlm = Bidi.RLM

    @Test
    fun arabicTextIsDetectedAsRightToLeftAndLatinIsNot() {
        assertTrue(Bidi.containsRtl("مرحبا بالعالم"))
        assertTrue(Bidi.containsRtl("שלום עולם"))
        assertTrue(Bidi.containsRtl("سلام دنیا")) // Persian
        assertTrue(Bidi.containsRtl("آپ کیسے ہیں")) // Urdu
        assertFalse(Bidi.containsRtl("hello world 123"))
        assertFalse(Bidi.containsRtl("你好 สวัสดี"))
        assertFalse(Bidi.containsRtl(""))
    }

    @Test
    fun aCueThatContainsArabicGetsOneRightToLeftMarkAndLatinCuesAreUntouched() {
        assertEquals("${rlm}مرحبا", Bidi.markRtl("مرحبا"))
        assertEquals("${rlm}WhatsApp رسالة", Bidi.markRtl("WhatsApp رسالة")) // starts with a Latin word: the case the mark is for
        assertEquals("${rlm}12 كتابا", Bidi.markRtl("12 كتابا"))
        assertEquals("hello", Bidi.markRtl("hello"))
        assertEquals("${rlm}مرحبا", Bidi.markRtl("${rlm}مرحبا")) // never doubled
    }

    @Test
    fun srtAndVttOfAnArabicTranscriptKeepTheWordsInReadingOrderWithTheMark() {
        val words = listOf(w("مرحبا", 0, 400), w("بكم", 450, 800), w("في", 850, 1000), w("البرنامج", 1050, 1600))
        val cues = buildSubtitleCues(words)
        assertEquals(1, cues.size)
        assertEquals("مرحبا بكم في البرنامج", cues[0].text) // logical order; the player's bidi algorithm does the visual flip
        val srt = cues.toSrt().replace("\r\n", "\n")
        assertEquals("1\n00:00:00,000 --> 00:00:01,600\n${rlm}مرحبا بكم في البرنامج\n\n", srt)
        val vtt = cues.toVtt().replace("\r\n", "\n")
        assertEquals("WEBVTT\n\n00:00:00.000 --> 00:00:01.600\n${rlm}مرحبا بكم في البرنامج\n\n", vtt)
    }

    @Test
    fun anEnglishTranscriptExportIsByteForByteWhatItWasBefore() {
        val cues = listOf(SubtitleCue(0, 1000, "hello world"))
        assertEquals("1\n00:00:00,000 --> 00:00:01,000\nhello world\n\n", cues.toSrt().replace("\r\n", "\n"))
    }

    @Test
    fun mixedArabicAndLatinStaysInOneCue() {
        val words = listOf(w("افتح", 0, 300), w("Gmail", 350, 700), w("الآن", 750, 1000))
        assertEquals("افتح Gmail الآن", buildSubtitleCues(words).single().text)
    }

    @Test
    fun thaiPiecesAreJoinedWithoutSpacesAndEachPieceIsATimedWord() {
        val groups = groupTokens(arrayOf("สวัส", "ดี", "ครับ", "ผม", "ชื่อ"))
        assertEquals(listOf("สวัส", "ดี", "ครับ", "ผม", "ชื่อ"), groups.map { it.text })
        assertEquals("สวัสดีครับผมชื่อ", joinWords(groups.map { it.text }))
        assertFalse(needsSpace("ดี", "ครับ"))
        assertTrue(needsSpace("hello", "world"))
    }

    @Test
    fun thaiAndChineseCuesHoldMoreUnitsThanSpacedLanguages() {
        val thai = (0 until 30).map { w("ก", it * 100L, it * 100L + 80) }
        val cues = buildSubtitleCues(thai)
        assertEquals(24, cues.first().text.length) // 24 pieces per cue, not 10
        val english = (0 until 30).map { w("w", it * 100L, it * 100L + 80) }
        assertEquals(10, buildSubtitleCues(english).first().text.split(" ").size)
    }
}
