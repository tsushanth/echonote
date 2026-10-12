package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptSegment
import kotlin.math.log10
import kotlin.math.sqrt

/** A stretch of speech inside a window, in milliseconds from the window start. */
data class SpeechSpan(val startMs: Long, val endMs: Long)

private const val FRAME = 320                // 20 ms at 16 kHz
private const val VOICED_DB = -24.75         // relative to the loud-speech level (95th percentile)
private const val FILL_GAP_FRAMES = 7        // close pauses up to ~140 ms
private const val MIN_SPAN_FRAMES = 3        // drop blips shorter than ~60 ms

/**
 * Finds where speech is in 16 kHz mono audio, by frame energy relative to the loud parts, so word times
 * can skip pauses. Falls back to the whole window when nothing stands out.
 */
fun speechSpans(samples: ShortArray, count: Int): List<SpeechSpan> {
    val n = count / FRAME
    val whole = listOf(SpeechSpan(0, count * 1000L / 16000))
    if (n == 0) return whole
    val rms = DoubleArray(n) { f ->
        var sum = 0.0
        for (i in f * FRAME until (f + 1) * FRAME) { val v = samples[i].toDouble(); sum += v * v }
        sqrt(sum / FRAME) + 1e-9
    }
    val ref = rms.sortedArray()[((n - 1) * 0.95).toInt()]
    val voiced = BooleanArray(n) { 20 * log10(rms[it] / ref + 1e-9) > VOICED_DB }
    // close short pauses
    var i = 0
    while (i < n) {
        if (!voiced[i]) {
            var j = i
            while (j < n && !voiced[j]) j++
            if (i > 0 && j < n && j - i <= FILL_GAP_FRAMES) for (k in i until j) voiced[k] = true
            i = j
        } else i++
    }
    val spans = mutableListOf<SpeechSpan>()
    i = 0
    while (i < n) {
        if (voiced[i]) {
            var j = i
            while (j < n && voiced[j]) j++
            if (j - i >= MIN_SPAN_FRAMES) spans += SpeechSpan(i * 20L, j * 20L)
            i = j
        } else i++
    }
    return spans.ifEmpty { whole }
}

/**
 * Gives words start and end times when the recognizer returned none: spreads them over the speech
 * (not the pauses) in proportion to their length. On real English speech this put 66% of words within
 * half a second of the true start, against 50% for an even spread over the whole window.
 */
fun distributeOverSpeech(words: List<String>, spans: List<SpeechSpan>, offsetMs: Long): List<TranscriptSegment> {
    if (words.isEmpty()) return emptyList()
    val speech = spans.ifEmpty { return emptyList() }
    val total = speech.sumOf { it.endMs - it.startMs }.toDouble()
    val weights = words.map { maxOf(it.length, 1).toDouble() }
    val sumW = weights.sum()
    fun toTime(speechMs: Double): Long {
        var acc = 0.0
        for (s in speech) {
            val len = (s.endMs - s.startMs).toDouble()
            if (speechMs <= acc + len) return s.startMs + (speechMs - acc).toLong()
            acc += len
        }
        return speech.last().endMs
    }
    var cum = 0.0
    val starts = weights.map { w -> toTime(cum / sumW * total).also { cum += w } }
    return words.mapIndexed { i, w ->
        val start = offsetMs + starts[i]
        val end = if (i + 1 < words.size) offsetMs + starts[i + 1] else offsetMs + speech.last().endMs
        TranscriptSegment(w, start, maxOf(end, start))
    }
}
