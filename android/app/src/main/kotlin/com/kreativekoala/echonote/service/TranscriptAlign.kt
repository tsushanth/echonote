package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptSegment
import kotlin.math.abs

/** A span of audio attributed to one speaker, in milliseconds. */
data class SpeakerSpan(val startMs: Long, val endMs: Long, val speaker: Int)

/** Scripts written without spaces between words: Han, Hiragana, Katakana and their punctuation. */
fun isNoSpaceScript(c: Char): Boolean =
    c in '\u3040'..'\u30FF' || c in '\u3400'..'\u4DBF' || c in '\u4E00'..'\u9FFF' ||
        c in '\u3000'..'\u303F' || c in '\uFF00'..'\uFFEF' || c in '\u31F0'..'\u31FF'

private fun isPunctuation(c: Char): Boolean =
    !c.isLetterOrDigit() && !c.isWhitespace() && (c in '\u3000'..'\u303F' || c in '\uFF00'..'\uFF0F' ||
        c in '\uFF1A'..'\uFF20' || Character.getType(c).let {
            it == Character.CONNECTOR_PUNCTUATION.toInt() || it == Character.DASH_PUNCTUATION.toInt() ||
                it == Character.START_PUNCTUATION.toInt() || it == Character.END_PUNCTUATION.toInt() ||
                it == Character.INITIAL_QUOTE_PUNCTUATION.toInt() || it == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
                it == Character.OTHER_PUNCTUATION.toInt()
        })

/** True when a space belongs between two adjacent words (never next to Chinese or Japanese text). */
fun needsSpace(prev: String, next: String): Boolean {
    if (prev.isEmpty() || next.isEmpty()) return false
    return !isNoSpaceScript(prev.last()) && !isNoSpaceScript(next.first())
}

/** Joins words with spaces, except around scripts that are written without them. */
fun joinWords(words: List<String>): String = buildString {
    words.forEachIndexed { i, w ->
        if (i > 0 && needsSpace(words[i - 1], w)) append(' ')
        append(w)
    }
}

/** A word and the index of the first token it was built from. */
data class TokenGroup(val text: String, val firstToken: Int)

/**
 * Groups recognizer tokens into words. A token starts a new word when it begins with a space, when it is
 * the first token, or at every Chinese or Japanese character (those scripts have no spaces, so each
 * character is its own word and tapping lands precisely). Punctuation stays with the word before it, and
 * special tokens (`<|...|>`) and empty byte fragments are dropped.
 */
fun groupTokens(tokens: Array<String>): List<TokenGroup> {
    val groups = mutableListOf<TokenGroup>()
    var text = StringBuilder()
    var first = 0
    fun flush() {
        val w = text.toString().trim()
        if (w.isNotEmpty()) groups += TokenGroup(w, first)
        text = StringBuilder()
    }
    for (i in tokens.indices) {
        val token = tokens[i]
        if (token.isEmpty() || (token.startsWith("<|") && token.endsWith("|>"))) continue
        val core = token.trimStart()
        if (core.isEmpty()) { flush(); continue }
        val c = core.first()
        val startsNew = token.startsWith(" ") || text.isEmpty() ||
            (isNoSpaceScript(c) && !isPunctuation(c)) ||
            (isNoSpaceScript(text.last()) && !isPunctuation(c))
        if (startsNew) { flush(); first = i }
        text.append(core)
    }
    flush()
    return groups
}

/**
 * Timed words from recognizer tokens that carry start times ([timestampsSec], seconds). A word ends where
 * the next begins (the last uses [durationsSec] when present). Returns empty when the arrays do not line up,
 * which is the case for Whisper exports that give no token times; use [groupTokens] with
 * [distributeOverSpeech] for those.
 */
fun tokensToWords(
    tokens: Array<String>,
    timestampsSec: FloatArray,
    durationsSec: FloatArray,
    offsetMs: Long
): List<TranscriptSegment> {
    if (tokens.isEmpty() || timestampsSec.size != tokens.size) return emptyList()
    val groups = groupTokens(tokens)
    return groups.mapIndexed { gi, g ->
        val start = offsetMs + (timestampsSec[g.firstToken] * 1000).toLong()
        val end = if (gi + 1 < groups.size) offsetMs + (timestampsSec[groups[gi + 1].firstToken] * 1000).toLong()
        else if (durationsSec.size == tokens.size) start + (durationsSec[g.firstToken] * 1000).toLong()
        else start + 300
        TranscriptSegment(g.text, start, maxOf(end, start))
    }
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

/**
 * Whisper's word times run a little late, so a speaker turn can end a word or two before the
 * sentence does. Give each sentence (ending in . ? or !) the speaker who has most of its spoken
 * time, so turns break at sentence ends. Words with no speaker are left alone.
 */
fun smoothSpeakersBySentence(words: List<TranscriptSegment>): List<TranscriptSegment> {
    if (words.none { it.speaker != null }) return words
    val out = words.toMutableList()
    var start = 0
    for (i in words.indices) {
        val endsSentence = words[i].word.let { it.endsWith(".") || it.endsWith("?") || it.endsWith("!") }
        if (endsSentence || i == words.lastIndex) {
            val weight = HashMap<Int, Long>()
            for (j in start..i) {
                val sp = words[j].speaker ?: continue
                weight[sp] = (weight[sp] ?: 0L) + maxOf(words[j].endMs - words[j].startMs, 1L)
            }
            val winner = weight.maxByOrNull { it.value }?.key
            if (winner != null) for (j in start..i) out[j] = words[j].copy(speaker = winner)
            start = i + 1
        }
    }
    return out
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
