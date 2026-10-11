package com.kreativekoala.echonote.util

import com.kreativekoala.echonote.data.model.TranscriptSegment

data class SubtitleCue(val startMs: Long, val endMs: Long, val text: String)

private const val MAX_CUE_WORDS = 10
private const val MAX_CUE_MS = 5000L
private const val CUE_BREAK_GAP_MS = 700L

/**
 * Groups timed words into subtitle cues that start and end where the speech does.
 * Vosk emits no punctuation, so cues break on a pause, a word count, or a duration cap.
 */
fun buildSubtitleCues(words: List<TranscriptSegment>): List<SubtitleCue> {
    if (words.isEmpty()) return emptyList()
    val cues = mutableListOf<SubtitleCue>()
    var first = 0
    fun flush(endExclusive: Int) {
        val slice = words.subList(first, endExclusive)
        cues += SubtitleCue(slice.first().startMs, slice.last().endMs, slice.joinToString(" ") { it.word })
        first = endExclusive
    }
    for (i in 1 until words.size) {
        val gap = words[i].startMs - words[i - 1].endMs
        val count = i - first
        val span = words[i - 1].endMs - words[first].startMs
        if (gap >= CUE_BREAK_GAP_MS || count >= MAX_CUE_WORDS || span >= MAX_CUE_MS) flush(i)
    }
    flush(words.size)
    return cues
}

private fun timestamp(ms: Long, separator: Char): String {
    val h = ms / 3_600_000
    val m = (ms % 3_600_000) / 60_000
    val s = (ms % 60_000) / 1000
    val millis = ms % 1000
    return "%02d:%02d:%02d%c%03d".format(h, m, s, separator, millis)
}

fun List<SubtitleCue>.toSrt(): String = buildString {
    this@toSrt.forEachIndexed { i, cue ->
        appendLine(i + 1)
        appendLine("${timestamp(cue.startMs, ',')} --> ${timestamp(cue.endMs, ',')}")
        appendLine(cue.text)
        appendLine()
    }
}

fun List<SubtitleCue>.toVtt(): String = buildString {
    appendLine("WEBVTT")
    appendLine()
    this@toVtt.forEach { cue ->
        appendLine("${timestamp(cue.startMs, '.')} --> ${timestamp(cue.endMs, '.')}")
        appendLine(cue.text)
        appendLine()
    }
}
