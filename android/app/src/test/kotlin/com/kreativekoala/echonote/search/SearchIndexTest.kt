package com.kreativekoala.echonote.search

import com.kreativekoala.echonote.data.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchIndexTest {

    private fun rec(id: String, title: String, date: Long, vararg lines: String) = IndexedRecording(
        id, title, date,
        lines.mapIndexed { i, t -> Sentence(i, t, i * 5_000L, i * 5_000L + 4_000L) }
    )

    private val maria = rec(
        "r1", "Interview Maria", 100,
        "Thanks for talking to me today.",
        "I moved to Austin in 2019 because of the housing prices back home.",
        "The relocation was stressful for my family.",
        "My sister still lives in Monterrey.",
    )
    private val omar = rec(
        "r2", "Interview Omar", 200,
        "Tell me about your job.",
        "I drive for a delivery company and the housing market here is terrible.",
        "Rent doubled in two years.",
        "I would never move to Austin again.",
    )
    private val index = SearchIndex(listOf(maria, omar))

    @Test fun findRequiresEveryWordAndReturnsTheMatchingSentence() {
        val hits = index.find("housing prices")
        assertEquals(1, hits.size)
        assertEquals("r1", hits[0].recordingId)
        assertEquals(1, hits[0].sentence.index)
        assertEquals(5_000L, hits[0].sentence.startMs)
    }

    @Test fun findAcrossRecordingsReturnsEachMatchingSentence() {
        val hits = index.find("housing")
        assertEquals(setOf("r1", "r2"), hits.map { it.recordingId }.toSet())
    }

    @Test fun lastWordMatchesAsAPrefixWhileTyping() {
        assertEquals(1, index.find("reloc").size)
        assertEquals(0, index.find("reloc", prefixLast = false).size)
        assertEquals(1, index.find("relocation", prefixLast = false).size)
    }

    @Test fun caseAndAccentsAreIgnoredForLatinScript() {
        val i = SearchIndex(listOf(rec("x", "t", 1, "We met at the Café del Mar.")))
        assertEquals(1, i.find("CAFE").size)
        assertEquals(1, i.find("café").size)
    }

    @Test fun apostrophesDoNotBreakWords() {
        val i = SearchIndex(listOf(rec("x", "t", 1, "I don’t know what they said.")))
        assertEquals(1, i.find("don't").size)
        assertEquals(1, i.find("dont know").size)
    }

    @Test fun anExactPhraseOutranksScatteredWords() {
        val i = SearchIndex(listOf(rec("x", "t", 1,
            "The loan was approved after the bank reviewed the paperwork.",
            "She said the bank loan was a mistake.")))
        val hits = i.find("bank loan")
        assertEquals(2, hits.size) // both sentences contain both words
        assertEquals(1, hits[0].sentence.index) // but only the second has them as one phrase
    }

    @Test fun scopedFindOnlySearchesThatRecording() {
        val hits = index.find("austin", recordingId = "r2")
        assertEquals(listOf("r2"), hits.map { it.recordingId }.distinct())
        assertTrue(index.find("austin", recordingId = "missing").isEmpty())
    }

    @Test fun emptyOrUnknownQueriesGiveNothing() {
        assertTrue(index.find("").isEmpty())
        assertTrue(index.find("   ").isEmpty())
        assertTrue(index.find("zebra").isEmpty())
        assertTrue(SearchIndex(emptyList()).find("a").isEmpty())
    }

    @Test fun chineseIsSearchedCharacterByCharacter() {
        val i = SearchIndex(listOf(rec("c", "t", 1, "我们昨天去了北京。", "他说天气很好。")))
        assertEquals(1, i.find("北京").size)
        assertEquals(0, i.find("上海").size)
    }

    @Test fun devanagariVowelSignsAreNotStripped() {
        val i = SearchIndex(listOf(rec("h", "t", 1, "मैं कल दिल्ली गया था।")))
        assertEquals(1, i.find("दिल्ली").size)
        assertEquals(0, i.find("दिल्लो").size)
    }

    @Test fun askDropsQuestionWordsAndReturnsThePassageWithItsNeighbours() {
        val passages = index.ask("What did they say about the housing prices?")
        assertTrue(passages.isNotEmpty())
        val top = passages.first()
        assertEquals("r1", top.recordingId)
        assertEquals(1, top.best.index)
        assertEquals(3, top.sentences.size)
        assertEquals(1, top.bestIndex)
        assertTrue(top.text.contains("Thanks for talking"))
    }

    @Test fun askRanksRareWordsAboveCommonOnes() {
        val i = SearchIndex(listOf(rec("x", "t", 1,
            "Austin is warm.", "Austin is big.", "Austin is loud.", "Austin is old.",
            "Our landlord raised the rent without notice.",
            "Austin has many bars.")))
        val top = i.ask("landlord in austin").first()
        assertEquals(4, top.best.index) // 'landlord' occurs once, 'austin' in most sentences
    }

    @Test fun askReturnsNonOverlappingPassagesAndHonoursTheLimit() {
        val lines = (0 until 30).map { "Sentence $it about budgets and money." }
        val i = SearchIndex(listOf(rec("x", "t", 1, *lines.toTypedArray())))
        val p = i.ask("budgets", limit = 3)
        assertEquals(3, p.size)
        val idx = p.map { it.best.index }.sorted()
        assertTrue(idx.zipWithNext().all { (a, b) -> b - a > 2 })
    }

    @Test fun askNeedsAtLeastHalfTheWordsToMatch() {
        val i = SearchIndex(listOf(rec("x", "t", 1, "The garden was lovely.", "We painted the fence green.")))
        assertTrue(i.ask("mortgage insurance premium deadline").isEmpty())
        assertTrue(i.ask("garden mortgage").isNotEmpty())
    }

    @Test fun askScopedToOneRecording() {
        val p = index.ask("austin", recordingId = "r1")
        assertEquals(setOf("r1"), p.map { it.recordingId }.toSet())
    }

    @Test fun askWithOnlyQuestionWordsFallsBackToThemInsteadOfReturningNothing() {
        val i = SearchIndex(listOf(rec("x", "t", 1, "What happened next?")))
        assertTrue(i.ask("what").isNotEmpty())
    }

    @Test fun highlightsCoverMatchingWordsInTheOriginalText() {
        val text = "I moved to Austin in 2019."
        val r = SearchIndex.highlights(text, "austin 2019", prefixLast = false)
        assertEquals(listOf("Austin", "2019"), r.map { text.substring(it) })
        val p = SearchIndex.highlights("The relocation went well", "reloc", prefixLast = true)
        assertEquals(listOf("relocation"), p.map { "The relocation went well".substring(it) })
    }

    @Test fun sentencesFromWordsBreakOnPunctuationPausesAndSpeakerChanges() {
        fun w(t: String, s: Long, e: Long, sp: Int? = null) = TranscriptSegment(t, s, e, sp)
        val words = listOf(
            w("Hello", 0, 400), w("there.", 400, 800), w("How", 900, 1100), w("are", 1100, 1300), w("you", 1300, 1500),
            w("fine", 3500, 3900), w("thanks", 3900, 4300, 1),
        )
        val s = Sentences.fromWords(words)
        assertEquals(listOf("Hello there.", "How are you", "fine", "thanks"), s.map { it.text })
        assertEquals(0L, s[0].startMs); assertEquals(800L, s[0].endMs)
        assertEquals(3500L, s[2].startMs)
        assertEquals(1, s[3].speaker)
        assertEquals(listOf(0, 1, 2, 3), s.map { it.index })
    }

    @Test fun plainTextWithoutTimesGivesSentencesWithoutTimestamps() {
        val s = Sentences.fromPlainText("One thing. Another thing.")
        assertEquals(2, s.size)
        assertEquals(null, s[0].startMs)
    }

    @Test fun veryLongRunsWithoutAnyBreakAreCutAt40Words() {
        val words = (0 until 100).map { TranscriptSegment("w$it", it * 300L, it * 300L + 250) }
        assertEquals(listOf(40, 40, 20), Sentences.fromWords(words).map { it.text.split(" ").size })
    }

    @Test fun indexReportsContent() {
        assertTrue(index.hasContent()); assertFalse(SearchIndex(emptyList()).hasContent())
        assertEquals(2, index.recordingCount)
        assertNotNull(index.find("rent").firstOrNull())
    }
}
