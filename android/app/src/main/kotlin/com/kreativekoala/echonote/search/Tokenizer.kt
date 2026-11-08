package com.kreativekoala.echonote.search

import java.text.Normalizer
import java.util.Locale

/** A word of a text with the character range it occupies in the ORIGINAL string (so matches can be highlighted). */
data class Token(val norm: String, val start: Int, val end: Int)

/**
 * Splitting and normalising text for search. Pure Kotlin.
 *  - case-insensitive; accents are ignored for Latin script ("cafe" finds "café") but never for scripts where marks
 *    carry meaning (Devanagari vowel signs);
 *  - apostrophes are dropped ("don't" -> "dont"), on both sides, so they match each other;
 *  - Arabic and Hebrew vowel marks are ignored and the Arabic letter variants that people type interchangeably are
 *    folded (alef with hamza, alef maksura, teh marbuta, tatweel), so a query finds the word however it is spelled;
 *  - Han / Kana / Thai / Lao / Khmer / Burmese have no spaces, so every character is its own token.
 */
object Tokenizer {

    fun tokenize(text: String): List<Token> {
        val out = mutableListOf<Token>()
        var start = -1
        var i = 0
        val n = text.length
        fun flush(end: Int) {
            if (start >= 0) {
                val norm = normalizeWord(text.substring(start, end))
                if (norm.isNotEmpty()) out += Token(norm, start, end)
                start = -1
            }
        }
        while (i < n) {
            val cp = text.codePointAt(i)
            val len = Character.charCount(cp)
            when {
                isCjk(cp) -> {
                    flush(i)
                    out += Token(String(Character.toChars(cp)).lowercase(Locale.ROOT), i, i + len)
                }
                Character.isLetterOrDigit(cp) || isMark(cp) -> if (start < 0) start = i
                (cp == '\''.code || cp == '’'.code) && start >= 0 && i + len < n && Character.isLetterOrDigit(text.codePointAt(i + len)) -> Unit // inside a word
                else -> flush(i)
            }
            i += len
        }
        flush(n)
        return out
    }

    /** Normalised terms of [text], in order, repeated terms kept. */
    fun terms(text: String): List<String> = tokenize(text).map { it.norm }

    fun normalizeWord(word: String): String {
        val lower = word.replace("'", "").replace("’", "").lowercase(Locale.ROOT)
        val nfd = Normalizer.normalize(lower, Normalizer.Form.NFD)
        val sb = StringBuilder(nfd.length)
        var baseIsLatin = false
        var i = 0
        while (i < nfd.length) {
            val cp = nfd.codePointAt(i)
            i += Character.charCount(cp)
            if (isMark(cp)) {
                if (!baseIsLatin && !isRtlVowelMark(cp)) sb.appendCodePoint(cp)
            } else if (cp == TATWEEL) {
                // elongation stroke, carries no letter
            } else {
                baseIsLatin = Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN
                sb.appendCodePoint(foldLetter(cp))
            }
        }
        return Normalizer.normalize(sb.toString(), Normalizer.Form.NFC)
    }

    private const val TATWEEL = 0x0640

    /** Arabic harakat, Quranic marks (including the hamza that NFD splits off an alef) and Hebrew points. */
    private fun isRtlVowelMark(cp: Int): Boolean =
        cp in 0x0610..0x061A || cp in 0x064B..0x065F || cp == 0x0670 || cp in 0x06D6..0x06ED || cp in 0x0591..0x05C7

    /** alef maksura and Farsi yeh to yeh, teh marbuta to heh, Farsi kaf to kaf. */
    private fun foldLetter(cp: Int): Int = when (cp) {
        0x0649, 0x06CC -> 0x064A
        0x0629 -> 0x0647
        0x06A9 -> 0x0643
        else -> cp
    }

    private fun isMark(cp: Int): Boolean = when (Character.getType(cp).toByte()) {
        Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
        else -> false
    }

    private fun isCjk(cp: Int): Boolean = when (Character.UnicodeScript.of(cp)) {
        Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.THAI, Character.UnicodeScript.LAO, Character.UnicodeScript.KHMER, Character.UnicodeScript.MYANMAR -> true
        else -> false
    }
}

/** Words that carry no meaning for finding passages. English only; other languages simply keep all their words. */
object Stopwords {
    val COMMON: Set<String> = setOf(
        "a", "an", "the", "and", "or", "but", "if", "of", "to", "in", "on", "at", "by", "for", "with", "from", "as", "is", "are",
        "was", "were", "be", "been", "being", "am", "it", "its", "this", "that", "these", "those", "there", "here", "i", "you",
        "he", "she", "we", "they", "me", "him", "her", "us", "them", "my", "your", "our", "their", "so", "then", "than", "too",
        "very", "just", "do", "does", "did", "have", "has", "had", "not", "no", "yes", "can", "could", "would", "should", "will",
    )

    /** Extra words that only phrase a question ("what did they say about X" -> X). */
    val QUESTION: Set<String> = COMMON + setOf(
        "what", "whats", "who", "whom", "whose", "when", "where", "why", "how", "which", "tell", "say", "said", "says", "saying",
        "talk", "talked", "talking", "mention", "mentioned", "mentions", "about", "regarding", "recording", "recordings",
        "interview", "interviews", "anyone", "anybody", "someone", "else", "any", "some", "ever", "find", "show", "give", "please",
        "get", "got", "like", "know", "think", "thought",
    )

    /** [terms] without [stop]; if that would leave nothing, the terms unchanged. */
    fun strip(terms: List<String>, stop: Set<String>): List<String> {
        val kept = terms.filter { it !in stop }
        return kept.ifEmpty { terms }
    }
}
