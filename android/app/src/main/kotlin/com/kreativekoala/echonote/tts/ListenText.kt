package com.kreativekoala.echonote.tts

import com.kreativekoala.echonote.data.model.TranscriptSegment
import com.kreativekoala.echonote.service.joinWords
import com.kreativekoala.echonote.ui.playback.buildParagraphs

/** Turns transcripts into the rows Listen reads and highlights. */
object ListenText {

    /**
     * Timed transcripts are cut at long pauses first (Vosk output has no punctuation, so sentence marks alone would
     * give one huge row), then at sentence marks. Older recordings without word times are split by sentence marks.
     */
    fun transcriptUnits(words: List<TranscriptSegment>, plainText: String): List<String> {
        if (words.isEmpty()) return SentenceSplitter.split(plainText)
        return buildParagraphs(words).flatMap { p ->
            SentenceSplitter.split(joinWords(words.subList(p.firstWord, p.endWord).map { it.word }))
        }
    }

    fun paragraphUnits(words: List<TranscriptSegment>, firstWord: Int, endWord: Int): List<String> =
        SentenceSplitter.split(joinWords(words.subList(firstWord, endWord).map { it.word }))
}
