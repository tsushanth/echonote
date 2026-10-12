package com.kreativekoala.echonote.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoskResultsTest {
    private fun result(vararg w: Triple<String, Double, Double>): String {
        val words = w.joinToString(",") { """{"word":"${it.first}","start":${it.second},"end":${it.third}}""" }
        return """{"result":[$words],"text":"${w.joinToString(" ") { it.first }}"}"""
    }

    @Test
    fun everySentenceIsKeptNotJustTheLastOne() {
        val out = mergeVoskResults(
            listOf(
                result(Triple("hello", 1.1, 1.4), Triple("there", 1.4, 1.9)),
                result(Triple("how", 18.0, 18.2), Triple("are", 18.2, 18.4), Triple("you", 18.4, 18.8)),
                result(Triple("fine", 30.0, 30.5))
            )
        )!!
        assertEquals("hello there how are you fine", out.text)
        assertEquals(listOf("hello", "there", "how", "are", "you", "fine"), out.segments.map { it.word })
        // times are continuous across sentences and strictly ordered
        assertEquals(1100L, out.segments.first().startMs)
        assertEquals(30000L, out.segments.last().startMs)
        assertTrue(out.segments.zipWithNext().all { (a, b) -> a.startMs <= b.startMs })
    }

    @Test
    fun emptyAndUnparseableResultsAreSkipped() {
        val out = mergeVoskResults(listOf("""{"text":""}""", "not json", result(Triple("ok", 0.5, 0.9)), """{"text":""}"""))!!
        assertEquals("ok", out.text)
    }

    @Test
    fun nothingRecognizedGivesNull() {
        assertNull(mergeVoskResults(emptyList()))
        assertNull(mergeVoskResults(listOf("""{"text":""}""", """{"partial":""}""")))
    }

    @Test
    fun chineseWordsFromVoskAreJoinedWithoutSpaces() {
        val out = mergeVoskResults(listOf(result(Triple("桥", 0.1, 0.3), Triple("下", 0.3, 0.5)), result(Triple("垂直", 1.0, 1.4))))!!
        assertEquals("桥下垂直", out.text)
    }

    @Test
    fun aResultWithTextButNoWordTimesStillContributesItsText() {
        val out = mergeVoskResults(listOf("""{"text":"no word detail here"}""", result(Triple("then", 5.0, 5.3))))!!
        assertTrue(out.text.contains("no word detail here"))
        assertTrue(out.text.contains("then"))
        assertEquals(listOf("then"), out.segments.map { it.word })
    }
}
