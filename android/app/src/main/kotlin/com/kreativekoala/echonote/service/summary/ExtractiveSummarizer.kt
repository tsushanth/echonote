package com.kreativekoala.echonote.service.summary

import com.kreativekoala.echonote.data.model.TranscriptSegment
import com.kreativekoala.echonote.data.model.TranscriptionLanguage
import com.kreativekoala.echonote.service.isNoSpaceScript
import com.kreativekoala.echonote.service.joinWords
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Picks the most central sentences of a transcript. No model, no network, deterministic.
 *
 * Method: split into sentence-like units, weight terms by frequency x inverse sentence
 * frequency (stopwords dropped; Chinese/Japanese use character bigrams), score each unit by
 * cosine similarity to the whole-document vector, then choose units greedily while penalising
 * overlap with units already chosen (maximal marginal relevance). The chosen units are
 * returned in their original order.
 *
 * Transcripts without punctuation (Vosk) are split at pauses in the word timings when those
 * exist, otherwise into fixed-size word windows; such units end with an ellipsis.
 */
class ExtractiveSummarizer @Inject constructor() : Summarizer {
    override val kind = SummaryKind.EXTRACTIVE

    override suspend fun summarize(
        text: String,
        language: TranscriptionLanguage,
        segments: List<TranscriptSegment>
    ): SummaryResult? = withContext(Dispatchers.Default) { summarizeNow(text, language, segments) }

    internal fun summarizeNow(
        text: String,
        language: TranscriptionLanguage,
        segments: List<TranscriptSegment> = emptyList()
    ): SummaryResult? {
        val clean = text.trim()
        if (clean.isEmpty()) return null
        val units = splitUnits(clean, segments)
        val cjk = isMostlyNoSpaceScript(clean)
        val size = if (cjk) clean.count { !it.isWhitespace() } else clean.split(WHITESPACE).size
        if (units.size < MIN_UNITS || size < (if (cjk) MIN_CJK_CHARS else MIN_WORDS)) return null

        val stop = stopwords(language)
        val termLists = units.map { tokens(it.text, stop) }
        val df = HashMap<String, Int>()
        termLists.forEach { terms -> terms.toSet().forEach { df[it] = (df[it] ?: 0) + 1 }}
        val n = units.size
        val vectors = termLists.map { terms ->
            val tf = HashMap<String, Int>()
            terms.forEach { tf[it] = (tf[it] ?: 0) + 1 }
            // Terms that occur in every unit carry no information about which unit matters.
            tf.mapValues { (t, c) -> (1.0 + ln(c.toDouble())) * ln(1.0 + n.toDouble() / df.getValue(t)) }
        }
        val centroid = HashMap<String, Double>()
        vectors.forEach { v -> v.forEach { (t, w) -> centroid[t] = (centroid[t] ?: 0.0) + w } }
        val norms = vectors.map { norm(it) }
        val centroidNorm = norm(centroid)
        if (centroidNorm == 0.0) return null

        val base = DoubleArray(n) { i ->
            if (norms[i] == 0.0) 0.0 else {
                val cos = dot(vectors[i], centroid) / (norms[i] * centroidNorm)
                // Slight lead bias: openings state the topic. Very short units are rarely informative.
                val lead = 1.0 + 0.15 * (1.0 - i.toDouble() / n)
                val len = termLists[i].size
                val lenFactor = if (len < MIN_TERMS) len.toDouble() / MIN_TERMS else 1.0
                cos * lead * lenFactor
            }
        }

        val want = pointCount(n)
        val budget = if (cjk) MAX_CHARS_CJK else MAX_CHARS
        val chosen = mutableListOf<Int>()
        var used = 0
        while (chosen.size < want) {
            var best = -1
            var bestScore = 0.0
            for (i in 0 until n) {
                if (i in chosen || base[i] <= 0.0) continue
                val redundancy = chosen.maxOfOrNull { cosine(vectors[i], norms[i], vectors[it], norms[it]) } ?: 0.0
                val score = base[i] - REDUNDANCY_WEIGHT * redundancy
                if (score > bestScore + 1e-12) { bestScore = score; best = i }
            }
            if (best < 0) break
            val cost = units[best].display().length
            if (chosen.isNotEmpty() && used + cost > budget) break
            chosen += best
            used += cost
        }
        if (chosen.isEmpty()) return null
        return SummaryResult(chosen.sorted().map { units[it].display() }, SummaryKind.EXTRACTIVE)
    }

    private class Unit(val text: String, val windowed: Boolean) {
        fun display(): String = if (windowed) text.trimEnd() + "…" else text
    }

    private fun splitUnits(text: String, segments: List<TranscriptSegment>): List<Unit> {
        val sentences = splitSentences(text)
        val words = if (isMostlyNoSpaceScript(text)) 0 else text.split(WHITESPACE).size
        val punctuated = if (words == 0) {
            sentences.size >= text.length / MAX_CJK_UNIT_CHARS
        } else {
            sentences.size >= words / (MAX_UNIT_WORDS * 2 / 3)
        }
        if (punctuated) return sentences.flatMap { subdivideLong(it) }
        // Little or no punctuation: use pauses in the timings when we have them.
        if (segments.size >= MIN_WORDS / 2) {
            val byPause = windowByTimings(segments)
            if (byPause.size >= MIN_UNITS) return byPause
        }
        return sentences.flatMap { subdivideLong(it) }
    }

    private fun splitSentences(text: String): List<Unit> {
        val out = mutableListOf<Unit>()
        val sb = StringBuilder()
        fun flush() {
            val s = sb.toString().trim()
            if (s.length >= 2) out += Unit(s, false)
            sb.setLength(0)
        }
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\n') { flush(); i++; continue }
            sb.append(c)
            val strongCjk = c == '。' || c == '！' || c == '？' || c == '；' || c == '।' || c == '؟'
            val latin = c == '.' || c == '!' || c == '?' || c == '…'
            if (strongCjk) {
                while (i + 1 < text.length && text[i + 1] in CLOSERS) { i++; sb.append(text[i]) }
                flush()
            } else if (latin) {
                // Consume a run such as "?!" or "...", then require whitespace/end to end the sentence,
                // which keeps "3.5", "e.g." (lowercase follower) and "v1.2" together.
                while (i + 1 < text.length && (text[i + 1] in ".!?…" || text[i + 1] in CLOSERS)) { i++; sb.append(text[i]) }
                val next = i + 1
                if (next >= text.length) flush()
                else if (text[next].isWhitespace()) {
                    var j = next
                    while (j < text.length && text[j].isWhitespace()) j++
                    val lower = j >= text.length || text[j].isLowerCase()
                    val abbreviation = c == '.' && sb.length > 0 && !sb.endsWith("..") &&
                        (endsWithTitle(sb) || (endsWithAbbreviation(sb) && lower))
                    if (!abbreviation) flush()
                } else if (isNoSpaceScript(text[next])) flush()
            }
            i++
        }
        flush()
        return out
    }

    private fun endsWithTitle(sb: StringBuilder): Boolean =
        sb.toString().trimEnd('.').lowercase().substringAfterLast(' ') in TITLES

    private fun endsWithAbbreviation(sb: StringBuilder): Boolean {
        val s = sb.toString().trimEnd('.').lowercase()
        val lastWord = s.substringAfterLast(' ')
        return lastWord in ABBREVIATIONS || (lastWord.length == 1 && lastWord[0].isLetter())
    }

    /** Breaks runs that are far too long for one point (run-on speech) into word windows. */
    private fun subdivideLong(u: Unit): List<Unit> {
        if (isMostlyNoSpaceScript(u.text)) {
            if (u.text.length <= MAX_CJK_UNIT_CHARS * 2) return listOf(u)
            return u.text.chunked(MAX_CJK_UNIT_CHARS).map { Unit(it, true) }
        }
        val words = u.text.split(WHITESPACE)
        if (words.size <= MAX_UNIT_WORDS * 2) return listOf(u)
        val parts = ceil(words.size / MAX_UNIT_WORDS.toDouble()).toInt()
        val per = ceil(words.size / parts.toDouble()).toInt()
        return words.chunked(per).map { w ->
            Unit(w.joinToString(" "), windowed = true)
        }
    }

    private fun windowByTimings(segments: List<TranscriptSegment>): List<Unit> {
        val out = mutableListOf<Unit>()
        var start = 0
        fun emit(end: Int) {
            if (end > start) out += Unit(joinWords(segments.subList(start, end).map { it.word }), true)
            start = end
        }
        for (i in 1 until segments.size) {
            val gap = segments[i].startMs - segments[i - 1].endMs
            val speakerChanged = segments[i].speaker != segments[i - 1].speaker
            val len = i - start
            if ((gap >= PAUSE_MS && len >= MIN_WINDOW_WORDS) || speakerChanged && len >= MIN_WINDOW_WORDS || len >= MAX_UNIT_WORDS) emit(i)
        }
        emit(segments.size)
        // A trailing sliver is merged into the previous window rather than kept alone.
        if (out.size >= 2 && out.last().text.length < 12) {
            val last = out.removeAt(out.size - 1)
            val prev = out.removeAt(out.size - 1)
            out += Unit(prev.text + " " + last.text, true)
        }
        return out
    }

    private fun tokens(text: String, stop: Set<String>): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        fun flushWord() {
            if (sb.isNotEmpty()) {
                val w = sb.toString()
                sb.setLength(0)
                if (w.length > 1 && w !in stop) out += w
            }
        }
        val cjkRun = StringBuilder()
        fun flushCjk() {
            val run = cjkRun.toString()
            cjkRun.setLength(0)
            if (run.length == 1 && run[0] !in CJK_STOP_CHARS) out += run
            for (k in 0 until run.length - 1) {
                val a = run[k]
                val b = run[k + 1]
                if (a in CJK_STOP_CHARS && b in CJK_STOP_CHARS) continue
                out += "$a$b"
            }
        }
        for (c in text.lowercase()) {
            when {
                isNoSpaceScript(c) && (c.isLetter()) -> { flushWord(); cjkRun.append(c) }
                c.isLetterOrDigit() || c == '\'' || c == '’' -> { flushCjk(); sb.append(c) }
                else -> { flushWord(); flushCjk() }
            }
        }
        flushWord(); flushCjk()
        return out
    }

    private fun isMostlyNoSpaceScript(text: String): Boolean {
        var cjk = 0
        var letters = 0
        for (c in text) if (c.isLetter()) { letters++; if (isNoSpaceScript(c)) cjk++ }
        return letters > 0 && cjk * 2 > letters
    }

    private fun pointCount(units: Int): Int = ceil(units * 0.15).toInt().coerceIn(MIN_POINTS, MAX_POINTS)

    private fun dot(a: Map<String, Double>, b: Map<String, Double>): Double {
        val (small, big) = if (a.size <= b.size) a to b else b to a
        var s = 0.0
        for ((k, v) in small) big[k]?.let { s += v * it }
        return s
    }

    private fun norm(a: Map<String, Double>): Double = sqrt(a.values.sumOf { it * it })

    private fun cosine(a: Map<String, Double>, na: Double, b: Map<String, Double>, nb: Double): Double =
        if (na == 0.0 || nb == 0.0) 0.0 else dot(a, b) / (na * nb)

    companion object {
        const val MIN_UNITS = 4
        const val MIN_WORDS = 40
        const val MIN_CJK_CHARS = 50
        private const val MIN_TERMS = 4
        private const val MIN_POINTS = 3
        private const val MAX_POINTS = 8
        private const val MAX_CHARS = 1100
        private const val MAX_CHARS_CJK = 450
        private const val MAX_UNIT_WORDS = 28
        private const val MIN_WINDOW_WORDS = 6
        private const val MAX_CJK_UNIT_CHARS = 48
        private const val PAUSE_MS = 700L
        private const val REDUNDANCY_WEIGHT = 0.7

        private val WHITESPACE = Regex("\\s+")
        private val CLOSERS = setOf('"', '\'', ')', ']', '”', '’', '」', '』', '）', '»')
        private val TITLES = setOf("mr", "mrs", "ms", "dr", "prof", "sr", "jr", "sra", "srta", "dra", "mme", "mlle")
        private val ABBREVIATIONS = setOf(
            "mr", "mrs", "ms", "dr", "prof", "sr", "jr", "st", "vs", "etc", "inc", "ltd", "no", "fig",
            "sra", "srta", "dra", "av", "mme", "mlle"
        )
        private val CJK_STOP_CHARS = setOf(
            '的', '了', '是', '在', '我', '你', '他', '她', '它', '们', '和', '也',
            '都', '就', '不', '有', '这', '那', '个', '到', '说', '要', '会', '吗', '呀', '呢', '啊',
            'の', 'に', 'は', 'を', 'た', 'が', 'で', 'と', 'し', 'て', 'こ', 'も', 'へ', 'や', 'か', 'な'
        )

        private val EN = words("a an and are as at be been but by can could did do does for from had has have he her his i if in into is it its just me my not of on or our she so than that the their them then there these they this to too up us was we were what when which who will with would you your about also very really like yeah okay um uh")
        private val ES = words("a al algo como con consu de del el ella ellos en es esa ese eso esta este esto la las le les lo los me mi mucho muy no nos o para pero por porque que se si sin su sus un una uno y ya yo tu te hay es son fue ser estar pues entonces bueno vale")
        private val FR = words("a au aux avec ce ces dans de des du elle en et est il ils je la le les leur lui ma mais me mes moi mon ne nous on ou par pas pour qu que qui sa se ses son sur ta te tes toi ton tu un une vos votre vous c d j l m n s t y ça cest alors donc bon")
        private val DE = words("aber alle als am an auch auf aus bei bin bis da das dass dem den der des die doch du ein eine einen einem einer er es für hat hier ich ihr im in ist ja kann man mein mit nach nicht noch nur oder sich sie sind so über um und uns von war was wenn wir wird wie zu zum zur also dann")
        private val PT = words("a ao aos as com como da das de do dos e ela ele em entre era essa esse esta este eu foi isso isto já mais mas me meu na nas no nos não o os ou para pela pelo por qual que se sem ser seu sua são só tem um uma você vocês então né")
        private val IT = words("a ad al alla alle anche che chi ci col come con cui da dal del della delle dei di e è ha ho il in io la le lei lo loro ma mi nel nella non o per più quando che questo questa se si sono su tra tu un una uno vi allora cioè")
        private val HI = words("का की के को में से पर है हैं था थी थे और या कि यह वह इस उस तो भी ही नहीं एक कर रहा रही रहे हो होता")

        private fun words(s: String) = s.split(' ').filter { it.isNotEmpty() }.toSet()

        fun stopwords(language: TranscriptionLanguage): Set<String> = when (language) {
            TranscriptionLanguage.ENGLISH -> EN
            TranscriptionLanguage.SPANISH -> ES
            TranscriptionLanguage.FRENCH -> FR
            TranscriptionLanguage.GERMAN -> DE
            TranscriptionLanguage.PORTUGUESE -> PT
            TranscriptionLanguage.ITALIAN -> IT
            TranscriptionLanguage.HINDI -> HI
            // CJK uses character stopwords; Korean falls back to inverse-frequency weighting alone.
            TranscriptionLanguage.CHINESE, TranscriptionLanguage.JAPANESE, TranscriptionLanguage.KOREAN -> emptySet()
        }
    }
}
