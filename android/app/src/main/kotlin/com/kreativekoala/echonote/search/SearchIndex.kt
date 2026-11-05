package com.kreativekoala.echonote.search

import java.util.TreeMap
import kotlin.math.ln

/** A recording's searchable content. */
data class IndexedRecording(
    val id: String,
    val title: String,
    val dateCreated: Long,
    val sentences: List<Sentence>,
)

/** A sentence that matched a Find query. */
data class FindHit(
    val recordingId: String,
    val title: String,
    val dateCreated: Long,
    val sentence: Sentence,
    val score: Double,
)

/** A passage (the best sentence plus its neighbours) retrieved for an Ask question. It is quoted, never rewritten. */
data class AskPassage(
    val recordingId: String,
    val title: String,
    val dateCreated: Long,
    /** The sentences shown, in transcript order; [bestIndex] is the position in this list of the best match. */
    val sentences: List<Sentence>,
    val bestIndex: Int,
    val score: Double,
) {
    val best: Sentence get() = sentences[bestIndex]
    val text: String get() = sentences.joinToString(" ") { it.text }
}

/**
 * In-memory full-text index over all transcripts, with BM25 ranking. No database, no network, no model: for a few
 * hundred hours of transcripts it holds a few MB and answers in milliseconds. Rebuilt from the recordings when they
 * change. Pure Kotlin (testable on the JVM).
 */
class SearchIndex(recordings: List<IndexedRecording>) {

    private class Doc(val rec: Int, val sentence: Int, val len: Int, val phrase: String)

    private val recs = recordings
    private val docs = ArrayList<Doc>()
    /** term -> (doc ids, term frequencies), parallel arrays built after the scan. */
    private val vocab = TreeMap<String, Postings>()
    private var avgLen = 1.0
    /** First doc id of each recording, to find neighbours. */
    private val firstDoc = IntArray(recordings.size)

    private class Postings {
        val ids = ArrayList<Int>()
        val tfs = ArrayList<Int>()
    }

    val recordingCount: Int get() = recs.size
    val sentenceCount: Int get() = docs.size
    fun hasContent(): Boolean = docs.isNotEmpty()

    init {
        var totalLen = 0L
        recordings.forEachIndexed { r, rec ->
            firstDoc[r] = docs.size
            for (s in rec.sentences) {
                val terms = Tokenizer.terms(s.text)
                val id = docs.size
                docs += Doc(r, s.index, terms.size, " " + terms.joinToString(" ") + " ")
                totalLen += terms.size
                val tf = HashMap<String, Int>()
                for (t in terms) tf[t] = (tf[t] ?: 0) + 1
                for ((t, c) in tf) {
                    val p = vocab.getOrPut(t) { Postings() }
                    p.ids += id; p.tfs += c
                }
            }
        }
        avgLen = if (docs.isEmpty()) 1.0 else (totalLen.toDouble() / docs.size).coerceAtLeast(1.0)
    }

    /** Terms of the vocabulary a query term stands for: itself, plus words it is a prefix of when [prefix]. */
    private fun expand(term: String, prefix: Boolean): List<String> {
        if (!prefix) return if (vocab.containsKey(term)) listOf(term) else emptyList()
        val out = ArrayList<String>()
        for ((k, _) in vocab.subMap(term, term + Character.MAX_VALUE)) {
            out += k
            if (out.size >= MAX_EXPANSIONS) break
        }
        return out
    }

    private fun idf(df: Int): Double = ln(1.0 + (docs.size - df + 0.5) / (df + 0.5))

    private fun bm25(tf: Int, len: Int, idf: Double): Double =
        idf * (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * len / avgLen))

    /**
     * Per-doc score and set of matched query-term positions. [prefixLast] makes the last term match word beginnings
     * (typing "reloc" finds "relocation").
     */
    private fun scoreDocs(terms: List<String>, prefixLast: Boolean, allowedRec: Int?): Pair<DoubleArray, Array<BitSetLite?>> {
        val scores = DoubleArray(docs.size)
        val matched = arrayOfNulls<BitSetLite>(docs.size)
        terms.forEachIndexed { qi, term ->
            val isLast = qi == terms.lastIndex
            for (word in expand(term, prefix = prefixLast && isLast)) {
                val p = vocab[word] ?: continue
                val idf = idf(p.ids.size)
                for (k in p.ids.indices) {
                    val d = p.ids[k]
                    if (allowedRec != null && docs[d].rec != allowedRec) continue
                    scores[d] += bm25(p.tfs[k], docs[d].len, idf)
                    (matched[d] ?: BitSetLite().also { matched[d] = it }).set(qi)
                }
            }
        }
        return scores to matched
    }

    private fun recIndex(recordingId: String?): Int? =
        if (recordingId == null) null else recs.indexOfFirst { it.id == recordingId }.takeIf { it >= 0 } ?: -1

    /**
     * Find: sentences that contain EVERY query word (the last one may be a word beginning), best first, then newest
     * recording first. A sentence that contains the words as one phrase scores higher.
     */
    fun find(query: String, prefixLast: Boolean = true, recordingId: String? = null, limit: Int = 200): List<FindHit> {
        val terms = Tokenizer.terms(query).distinct().let { Stopwords.strip(it, Stopwords.COMMON) }
        if (terms.isEmpty() || docs.isEmpty()) return emptyList()
        val allowed = recIndex(recordingId)
        if (allowed == -1) return emptyList()
        val (scores, matched) = scoreDocs(terms, prefixLast, allowed)
        val phraseTerms = Tokenizer.terms(query)
        val phrase = " " + phraseTerms.joinToString(" ")
        val hits = ArrayList<FindHit>()
        for (d in docs.indices) {
            val m = matched[d] ?: continue
            if (m.count() < terms.size) continue // AND
            val doc = docs[d]
            var score = scores[d]
            if (phraseTerms.size > 1 && (doc.phrase.contains("$phrase ") || (prefixLast && doc.phrase.contains(phrase)))) score *= PHRASE_BOOST
            val rec = recs[doc.rec]
            hits += FindHit(rec.id, rec.title, rec.dateCreated, rec.sentences[doc.sentence], score)
        }
        return hits.sortedWith(compareByDescending<FindHit> { it.score }.thenByDescending { it.dateCreated }.thenBy { it.sentence.index })
            .take(limit)
    }

    /**
     * Ask: retrieves the passages most relevant to a question. Question words are dropped, any matching word counts
     * (BM25 weighs rare words more), and each sentence is lifted a little by its neighbours so a passage where the
     * words are spread over two sentences still surfaces. Returns at most [limit] non-overlapping passages, each the
     * best sentence plus the sentence before and after. Nothing is generated: the text is the transcript's own.
     */
    fun ask(question: String, recordingId: String? = null, limit: Int = 5): List<AskPassage> {
        val terms = Stopwords.strip(Tokenizer.terms(question).distinct(), Stopwords.QUESTION)
        if (terms.isEmpty() || docs.isEmpty()) return emptyList()
        val allowed = recIndex(recordingId)
        if (allowed == -1) return emptyList()
        val (scores, matched) = scoreDocs(terms, prefixLast = false, allowedRec = allowed)
        val needed = minOf(terms.size, (terms.size + 1) / 2)

        val lifted = DoubleArray(docs.size)
        for (d in docs.indices) {
            if (scores[d] <= 0.0) continue
            var s = scores[d]
            val rec = docs[d].rec
            if (d > 0 && docs[d - 1].rec == rec) s += NEIGHBOUR_WEIGHT * scores[d - 1]
            if (d + 1 < docs.size && docs[d + 1].rec == rec) s += NEIGHBOUR_WEIGHT * scores[d + 1]
            lifted[d] = s
        }
        val ranked = docs.indices
            .filter { lifted[it] > 0.0 && (matched[it]?.count() ?: 0) >= needed }
            .sortedWith(compareByDescending<Int> { lifted[it] }.thenByDescending { recs[docs[it].rec].dateCreated }.thenBy { it })

        val chosen = ArrayList<Int>()
        for (d in ranked) {
            if (chosen.size >= limit) break
            // skip a sentence next to one we already picked in the same recording
            if (chosen.any { docs[it].rec == docs[d].rec && kotlin.math.abs(docs[it].sentence - docs[d].sentence) <= 2 }) continue
            chosen += d
        }
        return chosen.map { d ->
            val doc = docs[d]
            val rec = recs[doc.rec]
            val from = (doc.sentence - 1).coerceAtLeast(0)
            val to = (doc.sentence + 1).coerceAtMost(rec.sentences.lastIndex)
            AskPassage(rec.id, rec.title, rec.dateCreated, rec.sentences.subList(from, to + 1), doc.sentence - from, lifted[d])
        }
    }

    /** Character ranges of [text] that match the query, for highlighting. Same normalisation as the index. */
    fun highlights(text: String, query: String, prefixLast: Boolean, questionMode: Boolean = false): List<IntRange> =
        Companion.highlights(text, query, prefixLast, questionMode)

    private class BitSetLite {
        private var bits = 0L
        fun set(i: Int) { bits = bits or (1L shl (i and 63)) }
        fun count(): Int = java.lang.Long.bitCount(bits)
    }

    companion object {
        private const val K1 = 1.2
        private const val B = 0.75
        private const val PHRASE_BOOST = 1.5
        private const val NEIGHBOUR_WEIGHT = 0.3
        private const val MAX_EXPANSIONS = 40

        fun highlights(text: String, query: String, prefixLast: Boolean, questionMode: Boolean = false): List<IntRange> {
            val raw = Tokenizer.terms(query).distinct()
            val terms = Stopwords.strip(raw, if (questionMode) Stopwords.QUESTION else Stopwords.COMMON)
            if (terms.isEmpty()) return emptyList()
            val last = terms.last()
            return Tokenizer.tokenize(text).filter { t ->
                t.norm in terms || (prefixLast && t.norm.startsWith(last))
            }.map { it.start until it.end }
        }
    }
}
