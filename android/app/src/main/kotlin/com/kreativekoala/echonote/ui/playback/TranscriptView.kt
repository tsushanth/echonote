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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.kreativekoala.echonote.R
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kreativekoala.echonote.data.model.TranscriptSegment
import com.kreativekoala.echonote.service.needsSpace
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

/**
 * Splits timed words into paragraphs at long pauses, when the speaker changes, or when a
 * paragraph runs too long. Each paragraph carries the speaker of its first word.
 */
fun buildParagraphs(words: List<TranscriptSegment>): List<TranscriptParagraph> {
    if (words.isEmpty()) return emptyList()
    val result = mutableListOf<TranscriptParagraph>()
    var first = 0
    for (i in 1 until words.size) {
        val gap = words[i].startMs - words[i - 1].endMs
        val speakerChanged = words[i].speaker != words[i - 1].speaker
        if (gap >= PAUSE_BREAK_MS || speakerChanged || i - first >= MAX_WORDS_PER_PARAGRAPH) {
            result += TranscriptParagraph(first, i, words[first].startMs, words[first].speaker)
            first = i
        }
    }
    result += TranscriptParagraph(first, words.size, words[first].startMs, words[first].speaker)
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
 * current word sits on a highlighter mark. Tapping any word seeks there; a long press
 * calls [onEdit] (null when editing is unavailable).
 */
fun LazyListScope.transcriptItems(
    words: List<TranscriptSegment>,
    paragraphs: List<TranscriptParagraph>,
    activeWord: Int,
    onSeek: (Long) -> Unit,
    onEdit: ((TranscriptParagraph) -> Unit)? = null
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
                onEdit = onEdit,
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
    onEdit: ((TranscriptParagraph) -> Unit)?,
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
                val w = words[paragraph.firstWord + i].word
                val gap = if (i + 1 < arr.size && needsSpace(w, words[paragraph.firstWord + i + 1].word)) 1 else 0
                offset += w.length + gap
            }
        }
    }

    val text = remember(words, paragraph, activeWord, playedColor, upcomingColor, echo) {
        buildAnnotatedString {
            for (i in paragraph.firstWord until paragraph.endWord) {
                if (i > paragraph.firstWord && needsSpace(words[i - 1].word, words[i].word)) append(" ")
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

    // Character ranges of words the second engine did not agree with (empty unless "Mark uncertain words" was on).
    val uncertainRanges = remember(words, paragraph, starts) {
        buildList {
            for (i in starts.indices) {
                val w = words[paragraph.firstWord + i]
                if (w.uncertain && w.word.isNotEmpty()) add(starts[i] until (starts[i] + w.word.length))
            }
        }
    }
    val underlineColor = colors.onSurfaceVariant.copy(alpha = 0.85f)
    val unsureHint = stringResource(R.string.flags_a11y_unsure)
    val spoken = remember(words, paragraph, unsureHint, uncertainRanges.isEmpty()) {
        if (uncertainRanges.isEmpty()) null else buildString {
            for (i in paragraph.firstWord until paragraph.endWord) {
                if (i > paragraph.firstWord && needsSpace(words[i - 1].word, words[i].word)) append(" ")
                append(words[i].word)
                if (words[i].uncertain) append(" (").append(unsureHint).append(")")
            }
        }
    }

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
                .then(if (spoken != null) Modifier.semantics { contentDescription = spoken } else Modifier)
                .then(
                    if (uncertainRanges.isEmpty()) Modifier else Modifier.drawBehind {
                        drawUncertainUnderlines(layout, uncertainRanges, underlineColor)
                    }
                )
                .pointerInput(starts, onEdit) {
                    detectTapGestures(
                        onLongPress = onEdit?.let { edit -> { _ -> edit(paragraph) } },
                        onTap = { position ->
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
                    )
                }
        )
    }
}

/** Plain transcript for recordings that have text but no word timings (older recordings); long press edits it. */
fun LazyListScope.plainTranscriptItem(text: String, onEdit: (() -> Unit)? = null) {
    item(key = "plain") {
        Column(
            Modifier
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .pointerInput(onEdit) {
                    if (onEdit != null) detectTapGestures(onLongPress = { onEdit() })
                }
        ) {
            Text(
                text = text,
                style = TranscriptTextStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
        }
    }
}

/**
 * Dotted line just under each marked word (one piece per wrapped line). Quiet on purpose: same ink as the
 * secondary text, no red, no wave, so a marked word reads as "worth a second listen" rather than as an error.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawUncertainUnderlines(
    layout: TextLayoutResult?,
    ranges: List<IntRange>,
    color: androidx.compose.ui.graphics.Color
) {
    val l = layout ?: return
    val stroke = 1.5.dp.toPx()
    val dots = PathEffect.dashPathEffect(floatArrayOf(0.01f, stroke * 2.2f))
    val last = l.layoutInput.text.length - 1
    for (r in ranges) {
        if (r.first > last) continue
        val end = minOf(r.last, last)
        val firstLine = l.getLineForOffset(r.first)
        val lastLine = l.getLineForOffset(end)
        for (line in firstLine..lastLine) {
            val from = maxOf(r.first, l.getLineStart(line))
            val to = minOf(end, l.getLineEnd(line, visibleEnd = true) - 1)
            if (to < from) continue
            val left = l.getBoundingBox(from).left
            val right = l.getBoundingBox(to).right
            val y = l.getLineBaseline(line) + 3.dp.toPx()
            drawLine(color, Offset(left, y), Offset(right, y), strokeWidth = stroke, cap = StrokeCap.Round, pathEffect = dots)
        }
    }
}
