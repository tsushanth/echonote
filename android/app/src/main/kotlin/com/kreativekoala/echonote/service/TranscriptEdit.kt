package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptSegment

/**
 * Splits hand-edited text into words the way the recognizer output is split: on whitespace, and at every
 * Chinese or Japanese character (see [groupTokens]). Leading and trailing whitespace is ignored.
 */
fun splitEditedText(text: String): List<String> {
    val tokens = mutableListOf<String>()
    for (piece in text.trim().split(Regex("\\s+"))) {
        if (piece.isEmpty()) continue
        piece.forEachIndexed { i, c -> tokens += if (i == 0) " $c" else c.toString() }
    }
    return groupTokens(tokens.toTypedArray()).map { it.text }
}

/** Plain transcript text for [words], with no spaces next to Chinese or Japanese text. */
fun transcriptText(words: List<TranscriptSegment>): String = joinWords(words.map { it.word })

/**
 * Timed words for [newWords] spread over [startMs]..[endMs] in proportion to word length, so the timings
 * stay usable for tap-to-seek and subtitles. Every word gets a non-negative span; the last ends at [endMs].
 */
fun distributeByLength(newWords: List<String>, startMs: Long, endMs: Long, speaker: Int?): List<TranscriptSegment> {
    if (newWords.isEmpty()) return emptyList()
    val span = maxOf(endMs - startMs, 0L)
    val total = newWords.sumOf { maxOf(it.length, 1) }.toLong()
    var consumed = 0L
    var cursor = startMs
    return newWords.mapIndexed { i, w ->
        consumed += maxOf(w.length, 1)
        val end = if (i == newWords.lastIndex) startMs + span else startMs + span * consumed / total
        TranscriptSegment(w, cursor, end, speaker).also { cursor = end }
    }
}

/**
 * Replaces words [first] until [end] of [words] with [newText]. Same word count keeps each word's timing;
 * otherwise the paragraph's time span is redistributed by word length. New words take the paragraph's
 * speaker. Returns null when the range is invalid or the edit leaves no words.
 */
fun applyParagraphEdit(words: List<TranscriptSegment>, first: Int, end: Int, newText: String): List<TranscriptSegment>? {
    if (first < 0 || end > words.size || first >= end) return null
    val newWords = splitEditedText(newText)
    if (newWords.isEmpty()) return null
    val old = words.subList(first, end)
    val speaker = old.first().speaker
    val replacement = if (newWords.size == old.size) {
        old.mapIndexed { i, w -> w.copy(word = newWords[i], uncertain = w.uncertain && w.word == newWords[i]) }
    } else {
        distributeByLength(newWords, old.first().startMs, old.last().endMs, speaker)
    }
    return words.subList(0, first) + replacement + words.subList(end, words.size)
}
