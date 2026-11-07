package com.kreativekoala.echonote.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Search over right-to-left and unspaced scripts. */
class TokenizerRtlTest {
    @Test
    fun arabicVowelMarksAndElongationDoNotStopAMatch() {
        // كِتَاب (with harakat) must equal كتاب, and tatweel كتـــاب too
        assertEquals(Tokenizer.normalizeWord("كتاب"), Tokenizer.normalizeWord("كِتَاب"))
        assertEquals(Tokenizer.normalizeWord("كتاب"), Tokenizer.normalizeWord("كتـــاب"))
    }

    @Test
    fun arabicLetterVariantsPeopleTypeInterchangeablyAreFolded() {
        assertEquals(Tokenizer.normalizeWord("اسلام"), Tokenizer.normalizeWord("إسلام"))
        assertEquals(Tokenizer.normalizeWord("اسلام"), Tokenizer.normalizeWord("أسلام"))
        assertEquals(Tokenizer.normalizeWord("مدرسه"), Tokenizer.normalizeWord("مدرسة"))
        assertEquals(Tokenizer.normalizeWord("علي"), Tokenizer.normalizeWord("على"))
    }

    @Test
    fun hebrewPointsAreIgnored() {
        assertEquals(Tokenizer.normalizeWord("שלום"), Tokenizer.normalizeWord("שָׁלוֹם"))
    }

    @Test
    fun devanagariVowelSignsAreStillKept() {
        // the rule that marks matter for scripts other than Latin/Arabic/Hebrew must still hold
        assertTrue(Tokenizer.normalizeWord("कि") != Tokenizer.normalizeWord("क"))
    }

    @Test
    fun arabicSentencesTokenizeOnSpacesAndPunctuation() {
        val terms = Tokenizer.terms("مرحبا، كيف حالك؟")
        assertEquals(listOf("مرحبا", "كيف", "حالك"), terms)
    }

    @Test
    fun thaiIsSearchedCharacterByCharacterLikeChinese() {
        val a = Tokenizer.terms("สวัสดีครับ")
        val query = Tokenizer.terms("ดีครับ")
        assertTrue(a.size > 3)
        // the query's pieces appear in the sentence as a run
        assertTrue((0..a.size - query.size).any { i -> a.subList(i, i + query.size) == query })
    }

    @Test
    fun arabicQuestionMarkEndsASentence() {
        val words = listOf("هل", "تتكلم", "العربية؟", "نعم").mapIndexed { i, s ->
            com.kreativekoala.echonote.data.model.TranscriptSegment(s, i * 300L, i * 300L + 250)
        }
        assertEquals(listOf("هل تتكلم العربية؟", "نعم"), Sentences.fromWords(words).map { it.text })
    }
}
