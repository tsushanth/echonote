package com.kreativekoala.echonote.search

import com.kreativekoala.echonote.data.model.TranscriptSegment
import com.kreativekoala.echonote.service.joinWords
import com.kreativekoala.echonote.tts.SentenceSplitter

/** One searchable sentence of a transcript. Times are null for older recordings that only have plain text. */
data class Sentence(
    val index: Int,
    val text: String,
    val startMs: Long?,
    val endMs: Long?,
    val speaker: Int? = null,
)

object Sentences {

    private const val PAUSE_BREAK_MS = 1200L
    private const val MAX_WORDS = 40
    private val enders = setOf('.', '?', '!', '…', '。', '！', '？', '।', '؟', '۔')
    private val trailing = setOf('"', '\'', '”', '’', ')', ']')

    /**
     * Sentences from timed words: a sentence ends at a word that ends with . ? ! (Whisper adds them), at a pause of
     * 1.2 s or more (Vosk adds no punctuation), when the speaker changes, or after 40 words.
     */
    fun fromWords(words: List<TranscriptSegment>): List<Sentence> {
        if (words.isEmpty()) return emptyList()
        val out = mutableListOf<Sentence>()
        var first = 0
        fun emit(endExclusive: Int) {
            if (endExclusive <= first) return
            val slice = words.subList(first, endExclusive)
            val text = joinWords(slice.map { it.word }).trim()
            if (text.isNotEmpty()) out += Sentence(out.size, text, slice.first().startMs, slice.last().endMs, slice.first().speaker)
            first = endExclusive
        }
        for (i in words.indices) {
            val last = i == words.lastIndex
            val endsSentence = words[i].word.trimEnd { it in trailing }.lastOrNull()?.let { it in enders } == true
            val pauseAfter = !last && words[i + 1].startMs - words[i].endMs >= PAUSE_BREAK_MS
            val speakerChange = !last && words[i + 1].speaker != words[i].speaker
            val tooLong = i + 1 - first >= MAX_WORDS
            if (last || endsSentence || pauseAfter || speakerChange || tooLong) emit(i + 1)
        }
        return out
    }

    /** Older recordings with text but no word times: sentences with no timestamp. */
    fun fromPlainText(text: String): List<Sentence> =
        SentenceSplitter.split(text).mapIndexed { i, s -> Sentence(i, s, null, null) }
}
