package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptEditTest {
    private fun w(word: String, s: Long, e: Long, sp: Int? = null) = TranscriptSegment(word, s, e, sp)
    private val base = listOf(w("hello", 0, 500, 0), w("wrld", 500, 900, 0), w("again", 3000, 3400, 1))

    @Test
    fun sameWordCountKeepsEveryTiming() {
        val out = applyParagraphEdit(base, 0, 2, "hello world")!!
        assertEquals(listOf("hello", "world", "again"), out.map { it.word })
        assertEquals(base.map { it.startMs to it.endMs }, out.map { it.startMs to it.endMs })
        assertEquals(listOf<Int?>(0, 0, 1), out.map { it.speaker })
    }

    @Test
    fun changedCountSpreadsTheParagraphSpanByLength() {
        val out = applyParagraphEdit(base, 0, 2, "a bb cccc ddddd")!!
        val para = out.subList(0, 4)
        assertEquals(0L, para.first().startMs)
        assertEquals(900L, para.last().endMs)
        assertEquals(listOf<Int?>(0, 0, 0, 0), para.map { it.speaker })
        // contiguous, ordered, and longer words get longer spans
        for (i in 1 until para.size) assertEquals(para[i - 1].endMs, para[i].startMs)
        assertTrue(para[3].endMs - para[3].startMs > para[0].endMs - para[0].startMs)
        assertEquals(base[2], out[4])
    }

    @Test
    fun distributeOverZeroSpanNeverGoesNegative() {
        val out = distributeByLength(listOf("a", "b"), 100, 100, null)
        assertEquals(listOf(100L to 100L, 100L to 100L), out.map { it.startMs to it.endMs })
    }

    @Test
    fun whitespaceAroundAndBetweenWordsIsIgnored() {
        assertEquals(listOf("a", "b"), splitEditedText("  a \n\t b  "))
    }

    @Test
    fun emptyOrBlankEditIsRejected() {
        assertNull(applyParagraphEdit(base, 0, 2, ""))
        assertNull(applyParagraphEdit(base, 0, 2, "  \n "))
    }

    @Test
    fun invalidRangeIsRejected() {
        assertNull(applyParagraphEdit(base, 2, 2, "x"))
        assertNull(applyParagraphEdit(base, 0, 9, "x"))
        assertNull(applyParagraphEdit(emptyList(), 0, 0, "x"))
    }

    @Test
    fun chineseSplitsPerCharacterAndNeverGetsSpaces() {
        assertEquals(listOf("你", "好", "吗，"), splitEditedText("你好吗，"))
        assertEquals(listOf("hi", "你", "好", "ok"), splitEditedText("hi 你好 ok"))
        val out = applyParagraphEdit(base, 0, 2, "你好 世界")!!
        assertEquals("你好世界again", transcriptText(out))
    }

    @Test
    fun transcriptTextUsesSpacingRules() {
        assertEquals("hello wrld again", transcriptText(base))
        assertEquals("日本語text", transcriptText(listOf(w("日", 0, 1), w("本", 1, 2), w("語", 2, 3), w("text", 3, 4))))
    }

    @Test
    fun aCorrectedWordLosesItsUncertainMarkButAnUntouchedOneKeepsIt() {
        val flagged = listOf(
            TranscriptSegment("hello", 0, 500, uncertain = true),
            TranscriptSegment("wrld", 500, 900, uncertain = true),
            TranscriptSegment("again", 3000, 3400)
        )
        val out = applyParagraphEdit(flagged, 0, 2, "hello world")!!
        assertEquals(listOf(true, false, false), out.map { it.uncertain })
    }
}
