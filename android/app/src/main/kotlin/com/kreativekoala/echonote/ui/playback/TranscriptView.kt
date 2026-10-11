package com.kreativekoala.echonote.ui.playback

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreativekoala.echonote.data.model.TranscriptSegment
import com.kreativekoala.echonote.ui.theme.EchoTheme
import com.kreativekoala.echonote.ui.theme.TimerTextStyle
import com.kreativekoala.echonote.ui.theme.TranscriptTextStyle
import com.kreativekoala.echonote.util.TimeFormatting

/**
 * A run of words shown as one paragraph. [speaker] is null until speaker labels
 * exist; the layout already reserves the header row for it.
 */
data class TranscriptParagraph(
    val firstWord: Int,
    val endWord: Int, // exclusive
    val startMs: Long,
    val speaker: Int? = null
)

private const val PAUSE_BREAK_MS = 1200L
private const val MAX_WORDS_PER_PARAGRAPH = 70

/** Splits timed words into paragraphs at long pauses, or when a paragraph runs too long. */
fun buildParagraphs(words: List<TranscriptSegment>): List<TranscriptParagraph> {
    if (words.isEmpty()) return emptyList()
    val result = mutableListOf<TranscriptParagraph>()
    var first = 0
    for (i in 1 until words.size) {
        val gap = words[i].startMs - words[i - 1].endMs
        if (gap >= PAUSE_BREAK_MS || i - first >= MAX_WORDS_PER_PARAGRAPH) {
            result += TranscriptParagraph(first, i, words[first].startMs)
            first = i
        }
    }
    result += TranscriptParagraph(first, words.size, words[first].startMs)
    return result
}

/** Index of the paragraph that contains [wordIndex], or -1. */
fun paragraphIndexOf(paragraphs: List<TranscriptParagraph>, wordIndex: Int): Int {
    if (wordIndex < 0) return -1
    return paragraphs.indexOfFirst { wordIndex >= it.firstWord && wordIndex < it.endWord }
}

/**
 * Adds the transcript to a lazy list, one item per paragraph so long recordings
 * stay cheap. Words already spoken read in full ink, upcoming words recede, and the
 * current word sits on a highlighter mark. Tapping any word seeks there.
 */
fun LazyListScope.transcriptItems(
    words: List<TranscriptSegment>,
    paragraphs: List<TranscriptParagraph>,
    activeWord: Int,
    onSeek: (Long) -> Unit
) {
    paragraphs.forEachIndexed { index, paragraph ->
        item(key = "p$index") {
            // Paragraphs away from the playhead get a constant so they skip recomposition
            // while the active word moves.
            val localActive = when {
                activeWord < paragraph.firstWord -> NONE_PLAYED
                activeWord >= paragraph.endWord -> ALL_PLAYED
                else -> activeWord
            }
            TranscriptParagraphView(
                words = words,
                paragraph = paragraph,
                activeWord = localActive,
                onSeek = onSeek,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
            )
        }
    }
}

private const val NONE_PLAYED = -1
private const val ALL_PLAYED = Int.MAX_VALUE

@Composable
private fun TranscriptParagraphView(
    words: List<TranscriptSegment>,
    paragraph: TranscriptParagraph,
    activeWord: Int,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val echo = EchoTheme.colors
    val playedColor = colors.onSurface
    val upcomingColor = colors.onSurfaceVariant

    // Start offset of each word in the built string, for mapping a tap to a word.
    val starts = remember(words, paragraph) {
        IntArray(paragraph.endWord - paragraph.firstWord).also { arr ->
            var offset = 0
            for (i in arr.indices) {
                arr[i] = offset
                offset += words[paragraph.firstWord + i].word.length + 1
            }
        }
    }

    val text = remember(words, paragraph, activeWord, playedColor, upcomingColor, echo) {
        buildAnnotatedString {
            for (i in paragraph.firstWord until paragraph.endWord) {
                if (i > paragraph.firstWord) append(" ")
                val isActive = i == activeWord
                val played = i < activeWord
                val style = when {
                    isActive -> SpanStyle(
                        color = echo.onHighlighter,
                        background = echo.highlighter,
                        fontWeight = FontWeight.Medium
                    )
                    played -> SpanStyle(color = playedColor)
                    else -> SpanStyle(color = upcomingColor)
                }
                withStyle(style) { append(words[i].word) }
            }
        }
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        paragraph.speaker?.let { speaker ->
            Text(
                text = "Speaker ${speaker + 1}",
                style = MaterialTheme.typography.labelLarge,
                color = echo.speakers[speaker % echo.speakers.size]
            )
        }
        Text(
            text = TimeFormatting.formatDuration(paragraph.startMs),
            style = TimerTextStyle.copy(fontSize = 12.sp),
            color = echo.faint
        )
        Text(
            text = text,
            style = TranscriptTextStyle,
            onTextLayout = { layout = it },
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(starts) {
                    detectTapGestures { position ->
                        val l = layout ?: return@detectTapGestures
                        val offset = l.getOffsetForPosition(position)
                        var lo = 0
                        var hi = starts.size - 1
                        while (lo < hi) {
                            val mid = (lo + hi + 1) / 2
                            if (starts[mid] <= offset) lo = mid else hi = mid - 1
                        }
                        onSeek(words[paragraph.firstWord + lo].startMs)
                    }
                }
        )
    }
}

/** Plain transcript for recordings that have text but no word timings (older recordings). */
fun LazyListScope.plainTranscriptItem(text: String) {
    item(key = "plain") {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Text(
                text = text,
                style = TranscriptTextStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}
