package com.kreativekoala.echonote.service

import com.kreativekoala.echonote.data.model.TranscriptSegment
import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import java.text.Normalizer

/**
 * "Mark uncertain words": after the Accurate engine has transcribed, Vosk transcribes the same audio and the
 * Accurate words that no Vosk word agrees with are marked. Measured in tools/asr-bench/flag_eval.py: for the
 * languages in [supports] the marked words are 2.5x to 25x more often wrong than the rest, and 40% to 80% of
 * the Accurate engine's errors fall on a marked word. It is a hint, not a confidence score: most marked words
 * are still correct.
 */
object UncertainWords {
    /**
     * Languages where the measured benefit is clear and few enough words are marked. Korean is left out
     * (68% of words would be marked); Italian and Hindi use Vosk as the main engine, so there is no second opinion.
     */
    private val SUPPORTED = setOf(
        TranscriptionLanguage.ENGLISH, TranscriptionLanguage.GERMAN, TranscriptionLanguage.SPANISH,
        TranscriptionLanguage.FRENCH, TranscriptionLanguage.PORTUGUESE, TranscriptionLanguage.JAPANESE,
        TranscriptionLanguage.CHINESE
    )

    /** Chinese and Japanese have no spaces, so they are compared character by character. */
    private val CHARACTER_LANGUAGES = setOf(TranscriptionLanguage.CHINESE, TranscriptionLanguage.JAPANESE)

    /** True when marking can run: the main engine is not Vosk itself and the language was measured as useful. */
    fun supports(language: TranscriptionLanguage, engine: AsrEngine): Boolean =
        engine != AsrEngine.VOSK && language in SUPPORTED

    fun comparesCharacters(language: TranscriptionLanguage): Boolean = language in CHARACTER_LANGUAGES

    /** Longest recording the second pass is run on (recordings above this are left unmarked). */
    const val MAX_WORDS = 20_000

    /**
     * Lower-cases, applies NFKC and turns punctuation and symbols into separators, like the Python scorer.
     * With [characters] every non-space character is its own unit, otherwise units are whitespace-separated words.
     */
    fun normalize(text: String, characters: Boolean): List<String> {
        val folded = Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase()
        val sb = StringBuilder(folded.length)
        for (c in folded) sb.append(if (isSeparator(c)) ' ' else c)
        val s = sb.toString()
        return if (characters) s.filterNot { it.isWhitespace() }.map { it.toString() }
        else s.split(' ', '\t', '\n', '\r').filter { it.isNotEmpty() }
    }

    private fun isSeparator(c: Char): Boolean = when (Character.getType(c).toByte()) {
        Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION,
        Character.END_PUNCTUATION, Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION,
        Character.OTHER_PUNCTUATION, Character.MATH_SYMBOL, Character.CURRENCY_SYMBOL,
        Character.MODIFIER_SYMBOL, Character.OTHER_SYMBOL -> true
        else -> c.isWhitespace()
    }

    /**
     * Marks the [primary] words that the Vosk words [second] do not back up. Both sequences are normalized and
     * aligned (edit distance); a primary word is marked when at least half of its units (words or characters)
     * have no equal partner. Words that normalize to nothing (pure punctuation) are never marked. Returns the
     * list unchanged when either side is empty or too long to align. Existing flags are kept.
     */
    fun mark(
        primary: List<TranscriptSegment>,
        second: List<String>,
        characters: Boolean
    ): List<TranscriptSegment> {
        if (primary.isEmpty() || second.isEmpty() || primary.size > MAX_WORDS) return primary

        val units = ArrayList<String>()
        val owner = ArrayList<Int>()
        val unitCount = IntArray(primary.size)
        primary.forEachIndexed { i, seg ->
            val u = normalize(seg.word, characters)
            unitCount[i] = u.size
            for (x in u) { units.add(x); owner.add(i) }
        }
        val other = ArrayList<String>()
        for (w in second) other.addAll(normalize(w, characters))
        if (units.isEmpty() || other.isEmpty()) return primary

        val matched = alignMatches(units, other) ?: return primary
        val unmatched = IntArray(primary.size)
        for (k in units.indices) if (!matched[k]) unmatched[owner[k]]++
        return primary.mapIndexed { i, seg ->
            if (unitCount[i] > 0 && unmatched[i] * 2 >= unitCount[i]) seg.copy(uncertain = true) else seg
        }
    }

    /**
     * Banded edit-distance alignment of [a] against [b]. Result[i] is true when a[i] was paired with an equal b
     * element. Ties prefer diagonal, then skipping an a element, then skipping a b element (same as the Python scorer).
     * The band is wide enough to reach the corner, so memory is about a.size * (|a-b| + 600) bytes.
     * Returns null when the sequences are so different in length that aligning them would be meaningless.
     */
    fun alignMatches(a: List<String>, b: List<String>): BooleanArray? {
        val n = a.size
        val m = b.size
        val diff = kotlin.math.abs(n - m)
        if (diff > maxOf(n, m) / 2 + 20) return null // one side is mostly missing; a flag on everything is noise
        val k = diff + BAND
        val width = 2 * k + 1
        val inf = Int.MAX_VALUE / 2
        val back = ByteArray((n + 1) * width) // 0 diagonal, 1 skip a, 2 skip b
        var prev = IntArray(m + 1) { if (it <= k) it else inf }
        var cur = IntArray(m + 1)
        for (i in 1..n) {
            val lo = maxOf(0, i - k)
            val hi = minOf(m, i + k)
            java.util.Arrays.fill(cur, inf)
            if (lo == 0) cur[0] = i
            for (j in maxOf(lo, 1)..hi) {
                val diag = prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                val up = prev[j] + 1
                val left = cur[j - 1] + 1
                var best = diag
                var dir = 0
                if (up < best) { best = up; dir = 1 }
                if (left < best) { best = left; dir = 2 }
                cur[j] = best
                back[i * width + (j - i + k)] = dir.toByte()
            }
            val t = prev; prev = cur; cur = t
        }
        // Tie preference in the scorer's traceback is diagonal first, then up, then left; the forward pass above
        // picks the same order, so the path found is equivalent.
        val ok = BooleanArray(n)
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            if (i == 0) { j--; continue }
            if (j == 0) { i--; continue }
            val idx = j - i + k
            if (idx < 0 || idx >= width) return null
            when (back[i * width + idx].toInt()) {
                0 -> { ok[i - 1] = a[i - 1] == b[j - 1]; i--; j-- }
                1 -> i--
                else -> j--
            }
        }
        return ok
    }

    private const val BAND = 300
}
