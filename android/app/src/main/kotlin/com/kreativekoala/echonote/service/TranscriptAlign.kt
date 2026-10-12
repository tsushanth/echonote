package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptSegment
import kotlin.math.abs

/** A span of audio attributed to one speaker, in milliseconds. */
data class SpeakerSpan(val startMs: Long, val endMs: Long, val speaker: Int)

/**
 * Groups Whisper subword tokens into timed words. A token that starts with a space
 * begins a new word; any other token continues the current one. Special tokens
 * (`<|...|>`) are dropped. [timestampsSec] holds each token's start in seconds, and
 * a token ends where the next begins (the last uses [durationsSec] when present).
 */
fun tokensToWords(
    tokens: Array<String>,
    timestampsSec: FloatArray,
    durationsSec: FloatArray,
    offsetMs: Long
): List<TranscriptSegment> {
    if (tokens.isEmpty() || timestampsSec.size != tokens.size) return emptyList()
    val words = mutableListOf<TranscriptSegment>()
    var text = StringBuilder()
    var start = 0L
    var end = 0L

    fun flush() {
        val w = text.toString().trim()
        if (w.isNotEmpty()) words += TranscriptSegment(w, start, end)
        text = StringBuilder()
    }

    for (i in tokens.indices) {
        val token = tokens[i]
        if (token.startsWith("<|") && token.endsWith("|>")) continue
        val tokenStart = offsetMs + (timestampsSec[i] * 1000).toLong()
        val tokenEnd = when {
            i + 1 < tokens.size -> offsetMs + (timestampsSec[i + 1] * 1000).toLong()
            durationsSec.size == tokens.size -> tokenStart + (durationsSec[i] * 1000).toLong()
            else -> tokenStart + 300
        }
        if (token.startsWith(" ") || text.isEmpty()) {
            flush()
            start = tokenStart
        }
        text.append(token)
        end = maxOf(tokenEnd, start)
    }
    flush()
    return words
}

/**
 * Labels each word with the speaker whose span contains the word's midpoint, or the
 * nearest span when it falls in a gap. Speaker ids are renumbered by first
 * appearance so the first voice heard is always Speaker 1.
 */
fun assignSpeakers(words: List<TranscriptSegment>, spans: List<SpeakerSpan>): List<TranscriptSegment> {
    if (spans.isEmpty() || words.isEmpty()) return words
    val sorted = spans.sortedBy { it.startMs }
    val raw = words.map { w ->
        val mid = (w.startMs + w.endMs) / 2
        val inside = sorted.firstOrNull { mid >= it.startMs && mid <= it.endMs }
        (inside ?: sorted.minByOrNull { distanceTo(it, mid) }!!).speaker
    }
    val order = LinkedHashMap<Int, Int>()
    raw.forEach { order.getOrPut(it) { order.size } }
    return words.mapIndexed { i, w -> w.copy(speaker = order.getValue(raw[i])) }
}

private fun distanceTo(span: SpeakerSpan, ms: Long): Long = when {
    ms < span.startMs -> span.startMs - ms
    ms > span.endMs -> ms - span.endMs
    else -> 0L
}

/**
 * Start index of the quietest [frame]-sample window in [from, to), used to cut long
 * audio between words instead of through them.
 */
fun quietestFrameStart(samples: ShortArray, from: Int, to: Int, frame: Int = 1600): Int {
    val last = minOf(to, samples.size) - frame
    if (last < from) return minOf(to, samples.size)
    var best = from
    var bestEnergy = Long.MAX_VALUE
    var i = from
    while (i <= last) {
        var e = 0L
        for (j in i until i + frame) e += abs(samples[j].toInt())
        if (e < bestEnergy) { bestEnergy = e; best = i }
        i += frame / 2
    }
    return best
}
