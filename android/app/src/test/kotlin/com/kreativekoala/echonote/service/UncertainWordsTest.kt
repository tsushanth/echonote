package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptSegment
import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import com.kreativekoala.echonote.data.model.toJson
import com.kreativekoala.echonote.data.model.toTranscriptSegments
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UncertainWordsTest {
    private fun segs(vararg w: String) = w.mapIndexed { i, x -> TranscriptSegment(x, i * 400L, i * 400L + 300L) }
    private fun flagged(r: List<TranscriptSegment>) = r.filter { it.uncertain }.map { it.word }

    @Test
    fun identicalSequencesMarkNothing() {
        val r = UncertainWords.mark(segs("Hello,", "world."), listOf("hello", "world"), characters = false)
        assertEquals(emptyList<String>(), flagged(r))
    }

    @Test
    fun caseAndPunctuationAreIgnored() {
        val r = UncertainWords.mark(segs("Don't", "STOP!", "(now)"), listOf("don't", "stop", "now"), false)
        assertEquals(emptyList<String>(), flagged(r))
    }

    @Test
    fun substitutedWordIsMarked() {
        val r = UncertainWords.mark(segs("We", "met", "in", "Kowloon", "today"), listOf("we", "met", "in", "coal", "loon", "today"), false)
        assertEquals(listOf("Kowloon"), flagged(r))
    }

    @Test
    fun wordsVoskNeverHeardAreMarked() {
        val r = UncertainWords.mark(segs("I", "really", "do", "think", "so"), listOf("i", "do", "think", "so"), false)
        assertEquals(listOf("really"), flagged(r))
    }

    @Test
    fun wordsOnlyVoskHeardAddNothing() {
        val r = UncertainWords.mark(segs("I", "think"), listOf("i", "really", "think"), false)
        assertEquals(emptyList<String>(), flagged(r))
    }

    @Test
    fun pureLeadingPunctuationNeverMarked() {
        val r = UncertainWords.mark(segs("Yes", "-", "no"), listOf("yes", "no"), false)
        assertEquals(emptyList<String>(), flagged(r))
    }

    @Test
    fun emptySecondOpinionLeavesWordsUnmarked() {
        val words = segs("a", "b")
        assertEquals(words, UncertainWords.mark(words, emptyList(), false))
        assertEquals(emptyList<TranscriptSegment>(), UncertainWords.mark(emptyList(), listOf("a"), false))
    }

    @Test
    fun secondOpinionMuchShorterIsNotTrusted() {
        val words = segs(*Array(100) { "w$it" })
        assertEquals(words, UncertainWords.mark(words, listOf("w1"), false))
    }

    @Test
    fun otherFieldsAreKept() {
        val r = UncertainWords.mark(listOf(TranscriptSegment("x", 5, 9, speaker = 1)), listOf("y"), false)
        assertEquals(TranscriptSegment("x", 5, 9, speaker = 1, uncertain = true), r[0])
    }

    @Test
    fun characterModeComparesPerCharacter() {
        // Whisper words of 2 characters; Vosk spaced differently. Only the third word has a wrong character.
        val primary = segs("今日は", "天気が", "良い")
        val second = listOf("今日", "は", "天気", "が", "悪い")
        val r = UncertainWords.mark(primary, second, characters = true)
        assertEquals(listOf("良い"), flagged(r)) // 1 of 2 characters wrong: "い" matches, "良" does not => half => marked
    }

    @Test
    fun characterModeIgnoresPunctuationAndSpaces() {
        val r = UncertainWords.mark(segs("你好，", "世界。"), listOf("你", "好", "世界"), characters = true)
        assertEquals(emptyList<String>(), flagged(r))
    }

    @Test
    fun longSequencesAlignWithDrift() {
        val a = (0 until 3000).map { "w$it" }
        val b = a.toMutableList()
        b.removeAt(1500); b[2000] = "zzz"; b.add(10, "extra")
        val r = UncertainWords.mark(a.mapIndexed { i, w -> TranscriptSegment(w, i * 10L, i * 10L + 5) }, b, false)
        assertEquals(setOf("w1500", "w2001"), flagged(r).toSet())
    }

    @Test
    fun languageSupportFollowsTheBenchmark() {
        assertTrue(UncertainWords.supports(TranscriptionLanguage.ENGLISH, AsrEngine.WHISPER_SMALL))
        assertTrue(UncertainWords.supports(TranscriptionLanguage.GERMAN, AsrEngine.NEMO_FAST_CONFORMER))
        assertTrue(UncertainWords.supports(TranscriptionLanguage.CHINESE, AsrEngine.SENSE_VOICE))
        assertFalse(UncertainWords.supports(TranscriptionLanguage.ENGLISH, AsrEngine.VOSK)) // low-RAM fallback
        assertFalse(UncertainWords.supports(TranscriptionLanguage.ITALIAN, AsrEngine.VOSK))
        assertFalse(UncertainWords.supports(TranscriptionLanguage.HINDI, AsrEngine.VOSK))
        assertFalse(UncertainWords.supports(TranscriptionLanguage.KOREAN, AsrEngine.WHISPER_SMALL))
        assertTrue(UncertainWords.comparesCharacters(TranscriptionLanguage.JAPANESE))
        assertFalse(UncertainWords.comparesCharacters(TranscriptionLanguage.KOREAN))
    }

    @Test
    fun everySupportedLanguageUsesARealSecondEngine() {
        for (lang in TranscriptionLanguage.entries) {
            if (UncertainWords.supports(lang, EnginePlan.bestForLanguage(lang))) {
                assertTrue(lang.name, EnginePlan.bestForLanguage(lang) != AsrEngine.VOSK)
            }
        }
    }

    @Test
    fun uncertainFlagRoundTripsAndOldJsonStillLoads() {
        val words = listOf(
            TranscriptSegment("a", 0, 100),
            TranscriptSegment("b", 100, 200, speaker = 1, uncertain = true)
        )
        assertEquals(words, words.toJson().toTranscriptSegments())

        // an unflagged word adds nothing to the JSON
        val arr = JSONArray(words.toJson())
        assertFalse(arr.getJSONObject(0).has("uncertain"))
        assertTrue(arr.getJSONObject(1).getBoolean("uncertain"))

        val old = """[{"word":"hi","startMs":0,"endMs":50},{"word":"yo","startMs":50,"endMs":90,"speaker":0}]"""
        val loaded = old.toTranscriptSegments()
        assertEquals(2, loaded.size)
        assertFalse(loaded.any { it.uncertain })
        assertEquals(0, loaded[1].speaker)
        assertNull(loaded[0].speaker)
        assertNotNull(loaded[0])
    }
}
